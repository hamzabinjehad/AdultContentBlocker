// End-to-end check of Hisn Vault in a real headless Chrome.
//
//   node website/test/browser/e2e.mjs [--shots <dir>]
//
// It starts its own static server, then walks the real flow: the partner makes
// a key, the person seals a vault to it, the vault shows the code once, the
// partner releases it, and the person opens it with the returned key. Any
// console error fails the run.
//
// Needs Node 20+ and a Chromium-based browser. HISN_CHROME selects the binary;
// the macOS Chrome path is the default.
import { spawn } from 'node:child_process';
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const site = path.resolve(here, '..', '..');
const args = process.argv.slice(2);
const shotsIndex = args.indexOf('--shots');
const shots = shotsIndex >= 0 ? path.resolve(args[shotsIndex + 1]) : mkdtempSync(path.join(tmpdir(), 'hisn-shots-'));
mkdirSync(shots, { recursive: true });

const CHROME = process.env.HISN_CHROME || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const port = 8200 + Math.floor(Math.random() * 500);
const server = spawn('python3', ['-m', 'http.server', String(port), '--bind', '127.0.0.1'], {
  cwd: site, stdio: 'ignore',
});
const debugPort = 9200 + Math.floor(Math.random() * 500);
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  `--remote-debugging-port=${debugPort}`, `--user-data-dir=${mkdtempSync(path.join(tmpdir(), 'hisn-cdp-'))}`,
  '--window-size=900,1300', 'about:blank',
], { stdio: 'ignore' });

let failed = false;
const cleanup = () => { try { chrome.kill('SIGKILL'); } catch { /* already gone */ } try { server.kill('SIGKILL'); } catch { /* already gone */ } };
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
async function open(pathname) {
  const target = await (await fetch(
    `http://127.0.0.1:${debugPort}/json/new?${encodeURIComponent(`http://127.0.0.1:${port}${pathname}`)}`,
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
  await send('Runtime.enable');
  await send('Page.enable');
  await send('Page.navigate', { url: `http://127.0.0.1:${port}${pathname}` });
  await send('Emulation.setDeviceMetricsOverride', { width: 900, height: 1300, deviceScaleFactor: 1, mobile: false });
  await sleep(1400);
  const evaluate = async (expression) => {
    const result = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
    if (result.result?.exceptionDetails) {
      throw new Error(result.result.exceptionDetails.exception?.description || result.result.exceptionDetails.text);
    }
    return result.result?.result?.value;
  };
  const shot = async (name) => {
    const result = await send('Page.captureScreenshot', { format: 'png' });
    writeFileSync(path.join(shots, name), Buffer.from(result.result.data, 'base64'));
  };
  return { evaluate, shot, close: () => ws.close() };
}

try {
  const vault = await open('/index.html');
  const partner = await open('/partner.html');

  await partner.evaluate(`document.querySelector('[data-action=create-key]').click()`);
  await sleep(1200);
  const publicKey = await partner.evaluate(`document.querySelector('[data-public]').value`);
  const fingerprint = await partner.evaluate(`document.querySelector('[data-fingerprint]').textContent`);
  if (!publicKey || !String(fingerprint).startsWith('HISN-PK-')) throw new Error('the partner key did not render');

  await vault.evaluate(`
    (() => {
      const form = document.getElementById('create-form');
      form.querySelector('input[name=mode][value=both]').checked = true;
      const area = form.querySelector('textarea[name=partner]');
      area.value = ${JSON.stringify(publicKey)};
      area.dispatchEvent(new Event('input', { bubbles: true }));
      form.querySelector('input[name=ack]').checked = true;
    })()`);
  await sleep(1600);
  const shown = await vault.evaluate(`document.querySelector('[data-fingerprint]').textContent`);
  if (shown !== fingerprint) throw new Error('the pasted key fingerprint does not match the partner page');

  await vault.evaluate(`document.querySelector('[data-action=create]').click()`);
  await sleep(2500);
  const code = (await vault.evaluate(`document.querySelector('[data-code]')?.textContent`))?.replace(/\s+/g, '');
  if (!/^\d{4}$/.test(code || '')) throw new Error('the setup code did not render');
  await vault.shot('setup.png');

  await vault.evaluate(`document.querySelector('[data-action=seal]').click()`);
  await sleep(400);
  if ((await vault.evaluate(`document.querySelector('h1')?.textContent`)) !== 'Sealed') throw new Error('the vault did not seal');

  const request = await vault.evaluate(`(() => {
    document.querySelector('[data-action=toggle-request]').click();
    return document.querySelector('[data-request]').value; })()`);
  if (!request.includes('vault-release-request')) throw new Error('no release request');
  await vault.shot('sealed.png');

  await partner.evaluate(`(() => {
    document.querySelector('[data-request]').value = ${JSON.stringify(request)};
    document.querySelector('[data-action=release]').click(); })()`);
  await sleep(700);
  const releaseKey = await partner.evaluate(`document.querySelector('[data-release-key]')?.textContent`);
  if (!releaseKey?.startsWith('HISN-VK-')) throw new Error('the partner did not produce a release key');
  await partner.shot('partner.png');

  await vault.evaluate(`(() => {
    document.querySelector('input[name=release]').value = ${JSON.stringify(releaseKey)};
    document.querySelector('[data-action=open-release]').click(); })()`);
  await sleep(900);
  const revealed = (await vault.evaluate(`document.querySelector('[data-code]')?.textContent`))?.replace(/\s+/g, '');
  if (revealed !== code) throw new Error('the revealed code does not match the one that was sealed');
  await vault.shot('revealed.png');

  await vault.evaluate(`document.querySelector('[data-lang-button]').click()`);
  await sleep(400);
  if ((await vault.evaluate('document.documentElement.dir')) !== 'rtl') throw new Error('Arabic did not switch to RTL');
  await vault.shot('arabic.png');

  if (errors.length) throw new Error(`console errors: ${errors.join(' | ')}`);
  vault.close();
  partner.close();
  console.log(`E2E OK — screenshots in ${shots}`);
} catch (error) {
  failed = true;
  console.error('E2E FAILED:', error.message);
} finally {
  cleanup();
}

process.exit(failed ? 1 : 0);
