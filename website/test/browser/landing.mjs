// Browser check for the product page: English, the Arabic switch, the download
// link, the roadmap and a narrow viewport. It starts its own static server and
// a headless Chrome, fails on any console error, and optionally writes
// screenshots.
//
//   HISN_CHROME="/path/to/Chrome" node website/test/browser/landing.mjs --shots <dir>
import { spawn } from 'node:child_process';
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const site = path.resolve(here, '..', '..');
const args = process.argv.slice(2);
const shotsIndex = args.indexOf('--shots');
const shots = shotsIndex >= 0 ? path.resolve(args[shotsIndex + 1]) : mkdtempSync(path.join(tmpdir(), 'hisn-site-shots-'));
mkdirSync(shots, { recursive: true });

const CHROME = process.env.HISN_CHROME || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const port = 8300 + Math.floor(Math.random() * 400);
const debugPort = 9300 + Math.floor(Math.random() * 400);
const server = spawn('python3', ['-m', 'http.server', String(port), '--bind', '127.0.0.1'], {
  cwd: site, stdio: 'ignore',
});
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  '--hide-scrollbars', `--remote-debugging-port=${debugPort}`,
  `--user-data-dir=${mkdtempSync(path.join(tmpdir(), 'hisn-site-cdp-'))}`,
  '--window-size=1280,1600', 'about:blank',
], { stdio: 'ignore' });

const cleanup = () => {
  try { chrome.kill('SIGKILL'); } catch { /* gone */ }
  try { server.kill('SIGKILL'); } catch { /* gone */ }
};
process.on('exit', cleanup);
process.on('SIGINT', () => { cleanup(); process.exit(130); });

async function waitFor(fn, timeout = 40000) {
  const start = Date.now();
  for (;;) {
    try { const value = await fn(); if (value) return value; } catch { /* retry */ }
    if (Date.now() - start > timeout) throw new Error('timed out waiting for the browser');
    await sleep(150);
  }
}

await waitFor(() => fetch(`http://127.0.0.1:${port}/index.html`).then((r) => r.ok));
await waitFor(() => fetch(`http://127.0.0.1:${debugPort}/json/version`).then((r) => r.ok));

const errors = [];
const target = await (await fetch(
  `http://127.0.0.1:${debugPort}/json/new?${encodeURIComponent(`http://127.0.0.1:${port}/index.html`)}`,
  { method: 'PUT' },
)).json();
const ws = new WebSocket(target.webSocketDebuggerUrl);
await new Promise((resolve, reject) => { ws.onopen = resolve; ws.onerror = reject; });
let id = 0;
const pending = new Map();
ws.onmessage = (event) => {
  const msg = JSON.parse(event.data);
  if (msg.id && pending.has(msg.id)) { pending.get(msg.id)(msg); pending.delete(msg.id); }
  if (msg.method === 'Runtime.exceptionThrown') errors.push(msg.params.exceptionDetails.text);
  if (msg.method === 'Runtime.consoleAPICalled' && msg.params.type === 'error') {
    errors.push(msg.params.args.map((a) => a.value ?? a.description ?? '').join(' '));
  }
};
const send = (method, params = {}) => new Promise((resolve) => {
  const callId = ++id;
  pending.set(callId, resolve);
  ws.send(JSON.stringify({ id: callId, method, params }));
});
const evaluate = async (expression) => {
  const reply = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
  if (reply.result?.exceptionDetails) throw new Error(JSON.stringify(reply.result.exceptionDetails));
  return reply.result?.result?.value;
};

await send('Runtime.enable');
await send('Page.enable');

async function load(pathname) {
  await send('Page.navigate', { url: `http://127.0.0.1:${port}${pathname}` });
  await waitFor(async () => (await evaluate('document.readyState')) === 'complete');
  await sleep(1200);
}

async function shot(name, width, height) {
  await send('Emulation.setDeviceMetricsOverride', {
    width, height, deviceScaleFactor: 1, mobile: width < 600,
  });
  await sleep(250);
  const metrics = await send('Page.getLayoutMetrics');
  const full = Math.min(Math.ceil(metrics.result.contentSize.height), 9000);
  await send('Emulation.setDeviceMetricsOverride', {
    width, height: full, deviceScaleFactor: 1, mobile: width < 600,
  });
  await sleep(250);
  // The viewport is already the full page height. captureBeyondViewport has a
  // right-edge offset bug on RTL pages in headless Chrome, so it is not used.
  const image = await send('Page.captureScreenshot', { format: 'png', fromSurface: true });
  writeFileSync(path.join(shots, name), Buffer.from(image.result.data, 'base64'));
  await send('Emulation.setDeviceMetricsOverride', { width, height: 1600, deviceScaleFactor: 1, mobile: false });
}

try {
  await load('/index.html');
  await evaluate('localStorage.clear()');
  await load('/index.html');

  const h1 = await evaluate("document.querySelector('h1').textContent");
  if (!h1.includes('Adult content, blocked')) throw new Error(`unexpected hero: ${h1}`);
  const downloads = await evaluate("document.querySelectorAll('a[href=\"downloads/hisn-android.apk\"]').length");
  if (downloads < 2) throw new Error(`expected hero and card downloads, got ${downloads}`);
  const roadmap = await evaluate("document.querySelector('#roadmap').textContent");
  for (const name of ['A new Android update', 'Mac & iPhone', 'Linux & Windows', 'Browser extension', 'Hisn Vault']) {
    if (!roadmap.includes(name)) throw new Error(`roadmap missing ${name}`);
  }
  const visible = await evaluate('document.body.innerText.toLowerCase()');
  for (const term of ['sha-256', 'certificate', 'version code', 'signed', 'github', 'checksum']) {
    if (visible.includes(term)) throw new Error(`public page still says ${term}`);
  }
  const motion = await evaluate('Boolean(window.__hisnMotion) && document.documentElement.classList.contains("js-motion")');
  if (!motion) throw new Error('animation library did not initialize');
  await sleep(900);
  const opacity = await evaluate('Number(getComputedStyle(document.querySelector(".hero3 h1")).opacity)');
  if (opacity < 0.95) throw new Error(`hero did not finish animating: opacity ${opacity}`);

  await send('Emulation.setEmulatedMedia', {
    features: [{ name: 'prefers-color-scheme', value: 'light' }],
  });
  await load('/index.html');
  await shot('landing-en-light.png', 1280, 1600);
  await send('Emulation.setEmulatedMedia', { features: [] });
  await load('/index.html');
  await shot('landing-en.png', 1280, 1600);

  await evaluate("document.querySelector('[data-lang-button]').click()");
  await sleep(400);
  const lang = await evaluate('document.documentElement.lang');
  const dir = await evaluate('document.documentElement.dir');
  const arabicTitle = await evaluate("document.querySelector('h1').textContent");
  if (lang !== 'ar' || dir !== 'rtl') throw new Error(`Arabic switch failed: ${lang}/${dir}`);
  if (!arabicTitle.includes('محتوى')) throw new Error(`Arabic hero missing: ${arabicTitle}`);
  const arOverflow = await evaluate('document.documentElement.scrollWidth - document.documentElement.clientWidth');
  if (arOverflow > 1) throw new Error(`Arabic desktop overflow: ${arOverflow}px`);
  await send('Emulation.setDeviceMetricsOverride', { width: 1280, height: 1600, deviceScaleFactor: 1, mobile: false });
  await sleep(300);
  const heroShot = await send('Page.captureScreenshot', { format: 'png', fromSurface: true });
  writeFileSync(path.join(shots, 'hero-ar.png'), Buffer.from(heroShot.result.data, 'base64'));

  await evaluate("document.querySelector('[data-lang-button]').click()");
  await sleep(400);
  if ((await evaluate('document.documentElement.lang')) !== 'en') throw new Error('English switch failed');

  await send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true });
  await load('/index.html');
  const overflow = await evaluate('document.documentElement.scrollWidth - window.innerWidth');
  if (overflow > 1) throw new Error(`mobile horizontal overflow: ${overflow}px`);
  await shot('landing-mobile.png', 390, 844);

  await send('Emulation.setDeviceMetricsOverride', { width: 1280, height: 1600, deviceScaleFactor: 1, mobile: false });
  await load('/privacy.html');
  const privacy = await evaluate("document.querySelector('h1').textContent");
  if (!privacy.includes('stays on the phone')) throw new Error(`privacy page failed: ${privacy}`);
  await shot('privacy-en.png', 1280, 1600);

  await send('Emulation.setDeviceMetricsOverride', { width: 1280, height: 1600, deviceScaleFactor: 1, mobile: false });
  await load('/limits.html');
  const limits = await evaluate("document.querySelector('h1').textContent");
  if (!limits.includes('What Hisn will not do')) throw new Error(`limits page failed: ${limits}`);
  await shot('limits-en.png', 1280, 1600);

  if (errors.length) throw new Error(`console errors: ${errors.join(' | ')}`);
  console.log(`Landing OK — screenshots in ${shots}`);
} finally {
  ws.close();
  cleanup();
}
