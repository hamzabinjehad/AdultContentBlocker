// Repeatable UI review with a real extension worker, never a personal profile.
// NODE_PATH=<Playwright modules> node extension/test/browser/ui-qa.cjs --out <new-dir>
// HISN_QA_BROWSER can select an installed Chromium executable.
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { execFileSync } = require('node:child_process');
const { chromium } = require('playwright');

async function main() {
  const outArg = process.argv.indexOf('--out');
  if (outArg < 0 || !process.argv[outArg + 1]) throw Error('Use --out <new-evidence-directory>');
  const out = path.resolve(process.argv[outArg + 1]);
  fs.mkdirSync(out); // Never overwrite previous evidence.
  const repo = path.resolve(__dirname, '../../..');
  const work = fs.mkdtempSync(path.join(fs.realpathSync(os.tmpdir()), 'hisn-ui-qa-'));
  const stage = path.join(work, 'extension');
  execFileSync('python3', [path.join(repo, 'extension/prepare_unpacked.py'), '--out', stage]);
  const manifestPath = path.join(stage, 'manifest.json');
  const manifest = JSON.parse(fs.readFileSync(manifestPath));
  delete manifest.key;
  manifest.permissions = manifest.permissions.filter(p => p !== 'nativeMessaging');
  fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2));
  const evidence = { version: manifest.version, actualExtensionRuntime: true,
    actualToolbarPopup: false, isolatedProfile: true, nativeMessaging: false,
    checks: [], pageErrors: [], mediaAccuracyBenchmark: false };
  const check = (ok, label) => {
    evidence.checks.push({ ok, label });
    if (!ok) throw Error(label);
  };
  let context, safariBrowser;
  try {
    context = await chromium.launchPersistentContext(path.join(work, 'profile'), {
      executablePath: process.env.HISN_QA_BROWSER || '/Applications/Helium.app/Contents/MacOS/Helium',
      headless: false, timeout: 30000, viewport: { width: 480, height: 600 },
      args: [`--load-extension=${stage}`, `--disable-extensions-except=${stage}`,
        '--no-first-run', '--no-default-browser-check', '--use-mock-keychain', '--password-store=basic'],
    });
    const worker = context.serviceWorkers().find(w => w.url().endsWith('/background.js'))
      || await context.waitForEvent('serviceworker', {timeout:15000});
    const origin = 'chrome-extension://' + new URL(worker.url()).hostname;
    const page = await context.newPage();
    page.on('pageerror', e => evidence.pageErrors.push(e.message));
    await page.goto(origin + '/popup.html');
    // Keep the verified snapshot: startup reconciliation may begin another
    // honest pending phase between two separate state reads.
    const initialReceipt = JSON.parse(await page.evaluate(async () => {
      const began = Date.now();
      while (Date.now() - began < 15000) {
        const state = await chrome.runtime.sendMessage({type:'getState'});
        if (state.enforcement?.status === 'applied' && state.appliedRevision === state.desiredRevision) {
          return JSON.stringify(state);
        }
        await new Promise(resolve => setTimeout(resolve, 50));
      }
      throw new Error('No fully applied worker receipt within the QA deadline');
    }));
    evidence.initialEnforcement = initialReceipt.enforcement;
    check(initialReceipt.enforcement.version === 1 && initialReceipt.enforcement.layers.domains === 'applied'
      && initialReceipt.enforcement.layers.text === 'applied', 'Actual worker verifies installed baseline and text registration before ready UI');
    await page.locator('#protectionDetails > summary').click();
    await page.locator('#repairProtection').click();
    const repaired = JSON.parse(await page.evaluate(async () => {
      const { t } = await import(chrome.runtime.getURL('lib/i18n.js'));
      const began = Date.now();
      while (Date.now() - began < 35000) {
        const state = await chrome.runtime.sendMessage({type:'getState'});
        if (!document.querySelector('#repairProtection').disabled
            && document.querySelector('#repairMessage').textContent === t('repair.verified')
            && state.enforcement?.status === 'applied' && state.appliedRevision === state.desiredRevision) {
          return JSON.stringify(state);
        }
        await new Promise(resolve => setTimeout(resolve, 50));
      }
      throw Error('No verified popup repair and current readback before the QA deadline');
    }));
    evidence.repairReadback = {status:repaired.enforcement?.status,desiredRevision:repaired.desiredRevision,
      initialDesiredRevision:initialReceipt.desiredRevision,lockUntil:repaired.lockUntil,
      initialLockUntil:initialReceipt.lockUntil,appPresent:repaired.appPresent};
    // Startup list reconciliation may legitimately advance the desired revision
    // after the first receipt. Verify one current snapshot, not historical equality.
    // Deterministic worker tests separately prove repair never authors policy.
    check(repaired.enforcement.status === 'applied' && repaired.appliedRevision === repaired.desiredRevision
      && repaired.lockUntil === initialReceipt.lockUntil && repaired.appPresent === false,
      'Actual popup repair verifies current browser configuration without a native host or a lock reset');
    await page.locator('#protectionDetails > summary').click();
    for (const lang of ['en', 'ar']) {
      await worker.evaluate(lang => chrome.storage.local.set({ uiLanguage: lang }), lang);
      for (const scheme of ['dark', 'light']) {
        await page.setViewportSize({ width: 480, height: 600 });
        await page.emulateMedia({ colorScheme: scheme });
        await page.goto(origin + '/popup.html');
        // Explicitly constrained previews, not toolbar auto-size evidence.
        await page.addStyleTag({content:'html.popup-root{width:auto}.popup-root body.popup{max-width:100vw;width:480px}'});
        await page.waitForFunction(() => document.querySelector('#pDot').classList.contains('active'));
        check(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `${lang}/${scheme} wider popup fits`);
        check(await page.locator('#openSettings').evaluate(n => n.getBoundingClientRect().bottom <= innerHeight), `${lang}/${scheme} dashboard action visible`);
        check(await page.evaluate(() => {
          const main = document.querySelector('.popup-scroll').getBoundingClientRect();
          return [...document.querySelectorAll('.layer-checklist li')].every(n=>n.getBoundingClientRect().bottom <= main.bottom);
        }), `${lang}/${scheme} all five baseline protection layers visible immediately`);
        await page.screenshot({ path: path.join(out, `popup-${scheme}-${lang}.png`) });
        await page.setViewportSize({ width: 1200, height: 900 });
        await page.goto(origin + '/options.html#media');
        await page.waitForFunction(() => !document.querySelector('#inspectVisual').disabled);
        check(await page.locator('#media').isVisible(), `${lang}/${scheme} direct visual settings link`);
        await page.screenshot({ path: path.join(out, `media-${scheme}-${lang}.png`) });
      }
    }
    await page.locator('#inspectVisual').check();
    await page.waitForFunction(async () => (await chrome.runtime.sendMessage({ type: 'getState' })).inspectVisual === true);
    await page.waitForFunction(()=>document.querySelector('#inspectVisual').checked && !document.querySelector('#inspectVisual').disabled && !document.querySelector('[name="personChoice"][value="female"]').disabled);
    await page.locator('[name="personChoice"][value="female"]').check();
    await page.waitForFunction(async () => (await chrome.runtime.sendMessage({ type: 'getState' })).personBlur === 'female');
    check(true, 'Visible appearance choice saves through real worker');
    const state = await page.evaluate(() => chrome.runtime.sendMessage({ type: 'getState' }));
    check(!state.appPresent && state.inspectText !== false, 'No native host and text baseline retained');
    for (const [key,value] of [['personRegion','faces'],['personEffect','blur'],['personMedia','images']]) {
      await page.waitForFunction(key=>!document.getElementById(key).disabled,key);
      await page.locator(`#${key}`).selectOption(value);
      await page.waitForFunction(async ({key,value})=>(await chrome.runtime.sendMessage({type:'getState'}))[key]===value,{key,value});
    }
    check(true,'Person area, effect and media scope save through the actual worker');
    // Leave the gallery on the strongest default to retain its baseline test.
    for (const [key,value] of [['personRegion','full'],['personEffect','solid'],['personMedia','both']]) {
      await page.waitForFunction(key=>!document.getElementById(key).disabled,key);
      await page.locator(`#${key}`).selectOption(value);
      await page.waitForFunction(async ({key,value})=>(await chrome.runtime.sendMessage({type:'getState'}))[key]===value,{key,value});
    }
    for (const width of [390, 320]) {
      await page.setViewportSize({ width, height: 844 });
      check(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `Arabic visual page fits ${width}px`);
      await page.locator('[name="personChoice"][value="female"]').scrollIntoViewIfNeeded();
      check(await page.locator('[name="personChoice"][value="female"]').evaluate(n => n.closest('label').getBoundingClientRect().height >= 44), `${width}px appearance target is touch sized`);
      await page.screenshot({ path: path.join(out, `media-narrow-${width}-ar.png`) });
    }
    for (const lang of ['en','ar']) {
      await worker.evaluate(lang => chrome.storage.local.set({uiLanguage:lang}), lang);
      for (const width of [160,200,210,320]) {
        await page.setViewportSize({width,height:600});
        await page.emulateMedia({colorScheme:'dark'});
        await page.goto(origin + '/popup.html');
        await page.addStyleTag({content:'html.popup-root{width:auto;height:auto}.popup-root body.popup{max-width:100vw;width:480px}'});
        await page.waitForFunction(() => document.querySelector('#pDot').classList.contains('active'));
        check(await page.locator('.layer-checklist li>div').evaluateAll(nodes => nodes.every(n => n.getBoundingClientRect().width >= 110)), `${lang}/${width}px layer text does not collapse`);
        check(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `${lang}/${width}px popup has no horizontal overflow`);
        await page.screenshot({path:path.join(out,`popup-overview-${width}-${lang}.png`),fullPage:true});
      }
    }
    await page.setViewportSize({ width: 200, height: 280 });
    await page.goto(origin + '/popup.html');
    await page.addStyleTag({content:'html.popup-root{width:auto}.popup-root body.popup{max-width:100vw;width:480px}'});
    await page.waitForFunction(() => document.querySelector('#pDot').classList.contains('active'));
    await page.locator('#openSettings').scrollIntoViewIfNeeded();
    check(await page.locator('#openSettings').evaluate(n => {
      const r = n.getBoundingClientRect(); return r.top >= 0 && r.bottom <= innerHeight;
    }), 'Compact full-page action remains reachable');
    await page.screenshot({ path: path.join(out, 'popup-compact-ar.png') });
    check(evidence.pageErrors.length === 0, 'No extension UI runtime errors');
    // Actual extension iframe and actual worker: no account/native host used.
    await page.setViewportSize({width:480,height:600});
    await page.emulateMedia({colorScheme:'dark'});
    await page.goto(origin + '/popup.html');
    const pagesBefore = context.pages().length;
    await page.locator('[data-view="rules"]').click();
    const embedded = page.frameLocator('#settingsFrame');
    await embedded.locator('#custom').waitFor({state:'attached'});
    await embedded.locator('#customEntry').fill('popup.example');
    await embedded.locator('#customEntry').evaluate(n => n.closest('form').requestSubmit());
    check(context.pages().length === pagesBefore, 'Rules open in popup without creating a tab');
    await page.locator('#backToStatus').click();
    await page.locator('[data-view="rules"]').click();
    check(await embedded.locator('#custom').inputValue() === 'popup.example', 'Back and reopen preserve unsaved popup draft');
    await embedded.locator('#saveAll').click();
    await page.waitForFunction(async () => (await chrome.runtime.sendMessage({type:'getState'})).customBlocks?.includes('popup.example'));
    check(true, 'Popup standalone site edit saves through real guarded worker');
    await embedded.locator('.sidebar a[href="#media"]').click();
    await embedded.locator('[name="personChoice"][value="male"]').check();
    await page.waitForFunction(async () => (await chrome.runtime.sendMessage({type:'getState'})).personBlur === 'male');
    check(true, 'Popup visual preference saves through the shared controller');
    check(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'Popup editor fits horizontally');
    await page.screenshot({path:path.join(out,'popup-editor-dark-ar.png')});
    await embedded.locator('#uiLanguage').selectOption('en');
    await page.waitForFunction(() => document.documentElement.dir === 'ltr');
    check(await embedded.locator('html').getAttribute('dir') === 'ltr', 'Embedded language choice updates both popup and editor');
    await embedded.locator('#uiLanguage').selectOption('ar');
    await page.waitForFunction(() => document.documentElement.dir === 'rtl');
    for (const sample of [{width:320,height:600},{width:200,height:280}]) {
      await page.setViewportSize(sample);
      await page.addStyleTag({content:'html.popup-root{width:auto}.popup-root body.popup{max-width:100vw;width:480px}'});
      check(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth && document.querySelector('#settingsFrame').clientHeight >= 300), `${sample.width}px compact popup editor stays usable`);
      await page.screenshot({path:path.join(out,`popup-editor-${sample.width}-ar.png`),fullPage:true});
    }
    await page.setViewportSize({width:480,height:600});
    await embedded.locator('.sidebar a[href="#rules"]').click();
    await embedded.locator('#customEntry').fill('draft.example');
    await embedded.locator('#customEntry').evaluate(n => n.closest('form').requestSubmit());
    page.once('dialog', dialog => dialog.dismiss());
    await page.locator('#openFullSettings').click();
    check(context.pages().length === pagesBefore, 'Canceling pop-out keeps unsaved draft in popup');
    check((await embedded.locator('#custom').inputValue()).includes('draft.example'), 'Pop-out warning preserves draft on cancellation');
    await embedded.locator('#discardAll').click();
    // Real browser-owned toolbar bubble: inspect its window, not a tab preview.
    await page.bringToFront();
    const popupSize = await page.evaluate(async () => {
      await chrome.action.openPopup();
      await new Promise(resolve => setTimeout(resolve,500));
      const popup = chrome.extension.getViews({type:'popup'})[0];
      if (!popup) throw Error('No actual toolbar popup found');
      return {width:popup.innerWidth,height:popup.innerHeight,scrollWidth:popup.document.documentElement.scrollWidth};
    });
    evidence.actualToolbarPopup = true;
    evidence.toolbarSize = popupSize;
    check(popupSize.width >= 470 && popupSize.width <= 500, 'Actual toolbar popup opens at the requested wide size');
    check(popupSize.scrollWidth <= popupSize.width && popupSize.height <= 600, 'Actual toolbar popup fits browser bounds without horizontal overflow');
    await page.evaluate(() => {for(const popup of chrome.extension.getViews({type:'popup'})) popup.close();});
    const registration = await worker.evaluate(async () => (await chrome.scripting.getRegisteredContentScripts({ids:['hisn-visual']}))[0]);
    check(registration?.css?.includes('content/visual-gate.css') && registration.runAt === 'document_start', 'Actual opted-in worker registers the pre-display stylesheet at document start');
    const gallery = await context.newPage();
    gallery.on('pageerror',e=>evidence.pageErrors.push(e.message));
    await gallery.setViewportSize({width:720,height:600});
    await gallery.route('http://hisn-media-qa.test/**', route => route.fulfill({contentType:'text/html',body:`<!doctype html><title>Gallery</title><style>img{width:64px;height:64px;margin:2px}body{margin:0}</style>
      ${Array.from({length:50},(_,i)=>`<img alt="" src="data:image/svg+xml,${encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64"><rect width="64" height="64" fill="navy"/></svg>')}#${i}">`).join('')}
      <script>requestAnimationFrame(()=>{window.initialMedia=[...document.images].map(n=>({hidden:getComputedStyle(n).visibility==='hidden',checked:n.dataset.hisnVisual==='checked'}));});</script>`}));
    await gallery.goto('http://hisn-media-qa.test/gallery');
    await gallery.waitForFunction(()=>window.initialMedia?.length===50);
    check(await gallery.evaluate(()=>initialMedia.every(n=>n.hidden||n.checked)), 'Actual extension protects all 50 first-frame gallery elements unless already checked');
    evidence.actualGalleryFirstFrame = true;
    await gallery.close(); // Only the isolated synthetic gallery.
    // Reflect a locked/app-managed state in this isolated QA profile only.
    await worker.evaluate(async () => {
      const {state} = await chrome.storage.local.get('state');
      await chrome.storage.local.set({state:{...state,appPresent:true,lastHeartbeat:Date.now(),lockUntil:Date.now()+60000}});
    });
    await embedded.locator('.sidebar a[href="#rules"]').click();
    await embedded.locator('#custom').waitFor({state:'attached'});
    await page.waitForTimeout(150);
    check(await embedded.locator('#custom').isDisabled(), 'Popup native-owned site settings are read-only');
    await embedded.locator('.sidebar a[href="#media"]').click();
    check(await embedded.locator('[name="personChoice"][value="off"]').isDisabled(), 'Popup cannot disable person blur during an active commitment');
    check(evidence.pageErrors.length === 0, 'Embedded settings introduce no UI runtime errors');
    // Safari's manifest cannot be run as a Chromium extension. Render the
    // actual options files with an explicitly mocked Safari messaging bridge;
    // this checks layout only, never physical Safari execution or coverage.
    evidence.actualSafariRuntime = false;
    // No desktop extension in the Safari layout fixture: otherwise the real
    // text scanner can (correctly for its inputs) score the explanatory UI.
    safariBrowser = await chromium.launch({
      executablePath: process.env.HISN_QA_BROWSER || '/Applications/Helium.app/Contents/MacOS/Helium',
      headless:true, args:['--no-first-run','--no-default-browser-check','--use-mock-keychain','--password-store=basic'],
    });
    const safari = await safariBrowser.newPage();
    safari.on('pageerror', e => evidence.pageErrors.push(e.message));
    await safari.route('http://hisn-safari-ui.test/**', route => {
      const requestURL = new URL(route.request().url());
      const name = requestURL.pathname.slice(1);
      if (['options.css', 'options.js'].includes(name)) {
        return route.fulfill({ contentType: name.endsWith('.css') ? 'text/css' : 'text/javascript',
          body: fs.readFileSync(path.join(repo, 'ios/HisnWebExtension', name)) });
      }
      if (name === 'icons/icon-128.png') return route.fulfill({ contentType: 'image/png', body:fs.readFileSync(path.join(stage, name)) });
      if (name !== 'options.html') return route.fulfill({ status:404, body:'' });
      const lang = requestURL.searchParams.get('lang') === 'ar' ? 'ar' : 'en';
      const mock = `<script>window.fixture = {inspectVisual:false,personBlur:'off'};
      window.browser = {i18n:{getUILanguage:()=>${JSON.stringify(lang)}},runtime:{sendMessage:async m=>{
        if(m.type==='getVisualSettings')return {...fixture};
        if(m.type==='saveVisualSettings'){Object.assign(fixture,m.patch);return {settings:{...fixture}};}
        return {state:'disabled'};}}};</script>`;
      return route.fulfill({ contentType:'text/html', body:fs.readFileSync(path.join(repo,'ios/HisnWebExtension/options.html'),'utf8').replace('<head>','<head>'+mock) });
    });
    for (const sample of [{width:375,lang:'en',scheme:'light'}, {width:375,lang:'ar',scheme:'dark'},
      {width:280,lang:'ar',scheme:'dark'}, {width:1024,lang:'en',scheme:'light'}, {width:1024,lang:'ar',scheme:'dark'}]) {
      await safari.setViewportSize({width:sample.width,height:900});
      await safari.emulateMedia({colorScheme:sample.scheme});
      await safari.goto(`http://hisn-safari-ui.test/options.html?lang=${sample.lang}`);
      await safari.waitForFunction(() => document.querySelector('#inspectVisual')?.disabled === false);
      check(await safari.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `Safari UI fixture ${sample.lang}/${sample.width}px fits`);
      check(await safari.locator('.mode-option').evaluateAll(nodes=>nodes.every(n=>n.getBoundingClientRect().height >= 44)), `Safari UI fixture ${sample.width}px touch targets`);
      await safari.screenshot({path:path.join(out,`safari-ui-${sample.width}-${sample.scheme}-${sample.lang}.png`),fullPage:true});
    }
    await safari.locator('#inspectVisual').check();
    await safari.waitForFunction(()=>window.fixture.inspectVisual);
    await safari.locator('[name="personBlur"][value="female"]').check();
    await safari.waitForFunction(()=>window.fixture.personBlur==='female');
    check(await safari.locator('#inspectVisual').isChecked(), 'Safari UI fixture keeps adult opt-in separate from person preference');
    check(evidence.pageErrors.length === 0, 'No desktop or Safari UI fixture runtime errors');
    console.log(`${evidence.checks.length} UI checks passed (desktop real worker; Safari layout mock); evidence ${out}`);
  } catch (error) {
    // Record the original failure before closing an already-disconnected
    // browser; its close promise can itself lose live handles on some hosts.
    evidence.failure = error.stack || String(error);
    console.error(evidence.failure);
    throw error;
  } finally {
    fs.writeFileSync(path.join(out, 'evidence.json'), JSON.stringify(evidence, null, 2));
    await safariBrowser?.close();
    await context?.close(); // Only the fresh QA profile is closed.
    // Retain the isolated stage/profile for failure inspection; never delete a
    // user's files or an existing evidence directory as part of this test.
  }
}
let completed = false;
main().then(() => { completed = true; }, error => {
  completed = true; console.error(error.stack); process.exitCode = 1;
});
process.once('beforeExit', () => {
  // Some browser-close races leave a pending promise with no live Node handles.
  // A zero exit without the completed review must never count as passed QA.
  if (!completed) {
    console.error('UI QA did not complete; a pending browser operation lost its live handles.');
    process.exitCode = 1;
  }
});
