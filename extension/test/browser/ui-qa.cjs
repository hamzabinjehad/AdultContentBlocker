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
      headless: false, viewport: { width: 480, height: 600 },
      args: [`--load-extension=${stage}`, `--disable-extensions-except=${stage}`,
        '--no-first-run', '--no-default-browser-check', '--use-mock-keychain', '--password-store=basic'],
    });
    const worker = context.serviceWorkers().find(w => w.url().endsWith('/background.js'))
      || await context.waitForEvent('serviceworker');
    const origin = 'chrome-extension://' + new URL(worker.url()).hostname;
    const page = await context.newPage();
    page.on('pageerror', e => evidence.pageErrors.push(e.message));
    for (const lang of ['en', 'ar']) {
      await worker.evaluate(lang => chrome.storage.local.set({ uiLanguage: lang }), lang);
      for (const scheme of ['dark', 'light']) {
        await page.setViewportSize({ width: 480, height: 600 });
        await page.emulateMedia({ colorScheme: scheme });
        await page.goto(origin + '/popup.html');
        await page.waitForFunction(() => document.querySelector('#pDot').classList.contains('active'));
        check(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `${lang}/${scheme} wider popup fits`);
        check(await page.locator('#openSettings').evaluate(n => n.getBoundingClientRect().bottom <= innerHeight), `${lang}/${scheme} dashboard action visible`);
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
    await page.locator('[name="personChoice"][value="female"]').check();
    await page.waitForFunction(async () => (await chrome.runtime.sendMessage({ type: 'getState' })).personBlur === 'female');
    check(true, 'Visible appearance choice saves through real worker');
    const state = await page.evaluate(() => chrome.runtime.sendMessage({ type: 'getState' }));
    check(!state.appPresent && state.inspectText !== false, 'No native host and text baseline retained');
    for (const width of [390, 320]) {
      await page.setViewportSize({ width, height: 844 });
      check(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `Arabic visual page fits ${width}px`);
      await page.locator('[name="personChoice"][value="female"]').scrollIntoViewIfNeeded();
      check(await page.locator('[name="personChoice"][value="female"]').evaluate(n => n.closest('label').getBoundingClientRect().height >= 44), `${width}px appearance target is touch sized`);
      await page.screenshot({ path: path.join(out, `media-narrow-${width}-ar.png`) });
    }
    await page.setViewportSize({ width: 200, height: 280 });
    await page.goto(origin + '/popup.html');
    await page.waitForFunction(() => document.querySelector('#pDot').classList.contains('active'));
    await page.locator('#openSettings').scrollIntoViewIfNeeded();
    check(await page.locator('#openSettings').evaluate(n => {
      const r = n.getBoundingClientRect(); return r.top >= 0 && r.bottom <= innerHeight;
    }), 'Compact full-page action remains reachable');
    await page.screenshot({ path: path.join(out, 'popup-compact-ar.png') });
    check(evidence.pageErrors.length === 0, 'No extension UI runtime errors');
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
  } finally {
    fs.writeFileSync(path.join(out, 'evidence.json'), JSON.stringify(evidence, null, 2));
    await safariBrowser?.close();
    await context?.close(); // Only the fresh QA profile is closed.
    // Retain the isolated stage/profile for failure inspection; never delete a
    // user's files or an existing evidence directory as part of this test.
  }
}
main().catch(error => { console.error(error.stack); process.exitCode = 1; });
