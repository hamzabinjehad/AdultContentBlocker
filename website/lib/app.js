// Hisn Vault — the page.
//
// State is small on purpose: one vault (ciphertext, in localStorage or imported
// from a file), and the code held in memory only while it is being shown. A
// reload during the setup window loses the code; that is the price of never
// writing it anywhere but inside the sealed payload.

import { fromB64u, publicRawFromJwk } from './crypto.js';
import { detectLang, formatDate, lang, setLang, t } from './copy.js';
import * as store from './store.js';
import {
  DURATIONS, SETUP_WINDOW_MS, createVault, inspectVault, openWithTime, partnerFingerprint,
  phase, readCode, sealVault,
} from './vault.js';

const app = document.getElementById('app');

let vault = store.loadVault();
let code = null;        // in memory only
let fingerprint = null; // for the partner key being pasted
let showRequest = false;
let error = null;
let busy = false;

const esc = (value) => String(value).replace(/[&<>"']/g, (c) => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
}[c]));

const two = (n) => String(n).padStart(2, '0');

function clock(ms) {
  const total = Math.max(0, Math.floor(ms / 1000));
  const d = Math.floor(total / 86400);
  const h = Math.floor((total % 86400) / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  return `${two(h)}:${two(m)}:${two(s)}${d ? ` · ${d}d` : ''}`;
}

function currentView() {
  if (!vault) return 'start';
  const now = phase(vault);
  if (now.state === 'setup') return code ? 'setup' : 'interrupted';
  if (code) return 'revealed';
  if (now.state === 'sealed') return 'sealed';
  return 'due';
}

function shell() {
  document.getElementById('footer-note').textContent = t('footer.note');
  document.getElementById('footer-partner').textContent = t('footer.partner');
  document.querySelector('[data-lang-button]').textContent = t('lang');
  const pill = document.getElementById('surface-pill');
  if (pill) pill.textContent = t('vault');
}

function setError(message) {
  error = message;
  const box = document.getElementById('error');
  if (box) {
    box.textContent = message || '';
    box.classList.toggle('show', Boolean(message));
  }
}

// ------------------------------------------------------------------ views

function startView() {
  const options = [
    ['Screen Time', t('start.screenTime')],
    ['Mac profile', t('start.macProfile')],
    ['Other', t('start.other')],
  ];
  const chips = DURATIONS.map((days) => `
      <button type="button" class="chip" data-action="pick-days" data-days="${days}"
              aria-pressed="${days === 7}">${t(`start.day${days}`)}</button>`).join('');
  return `
  <section class="card hero">
    <h1>${t('start.title')}</h1>
    <p class="lede">${t('start.lede')}</p>
  </section>

  <form class="card" id="create-form">
    <h2>${t('start.make')}</h2>

    <label class="field"><span>${t('start.whatFor')}</span>
      <select name="label">
        ${options.map(([value, text]) => `<option value="${esc(value)}">${esc(text)}</option>`).join('')}
      </select>
    </label>

    <fieldset>
      <legend>${t('start.howLong')}</legend>
      <div class="chips">
        ${chips}
        <button type="button" class="chip" data-action="pick-days" data-days="custom" aria-pressed="false">${t('start.custom')}</button>
      </div>
      <input class="hidden" type="datetime-local" name="custom" class="mt-10">
      <p class="mini mt-8" data-open-date></p>
    </fieldset>

    <fieldset>
      <legend>${t('start.howOpen')}</legend>
      <div class="modes">
        <label class="mode"><input type="radio" name="mode" value="both">
          <span>${t('start.modeBoth')}</span></label>
        <label class="mode"><input type="radio" name="mode" value="time" checked>
          <span>${t('start.modeTime')}</span></label>
        <label class="mode"><input type="radio" name="mode" value="partner">
          <span>${t('start.modePartner')}</span></label>
      </div>

      <label class="field" data-partner-block><span>${t('start.partnerKey')}</span>
        <textarea name="partner" spellcheck="false" placeholder='{"kty":"EC","crv":"P-256","x":"…","y":"…"}'></textarea>
      </label>
      <p class="hint">${t('start.partnerHint')} <a href="partner.html" target="_blank" rel="noopener">${t('start.getPartnerKey')}</a></p>
      <p class="fingerprint" data-fingerprint></p>
    </fieldset>

    <label class="check"><input type="checkbox" name="ack">
      <span>${t('start.understand')}</span></label>

    <div class="error" id="error" role="alert">${esc(error || '')}</div>

    <button class="primary" type="button" data-action="create">${t('start.create')}</button>
  </form>

  <section class="card">
    <h2>${t('start.import')}</h2>
    <p class="hint">${t('start.importHint')}</p>
    <label class="button mt-8">
      ${t('start.importButton')}
      <input type="file" accept="application/json,.json" data-action="import" hidden>
    </label>
    <div class="error" id="import-error" role="alert"></div>
  </section>`;
}

function setupView() {
  if (!vault) return '';
  const ends = Date.parse(vault.createdAt) + SETUP_WINDOW_MS;
  const steps = vault.label === 'Screen Time'
    ? `<ol class="steps">
         <li>${t('setup.step1')}</li>
         <li>${t('setup.step2')}</li>
         <li>${t('setup.step3')}</li>
         <li>${t('setup.step4')}</li>
       </ol>`
    : `<p>${t('setup.generic')}</p>`;
  return `
  <section class="card hero">
    <p class="eyebrow">${t('setup.title')}</p>
    <div class="code-display" data-code>${esc(code.split('').join(' '))}</div>
    <p class="lede" data-setup-lede>${t('setup.lede', { time: clock(ends - Date.now()) })}</p>
    <div class="row mt-16">
      <button type="button" data-action="copy-code">${t('setup.copy')}</button>
      <button type="button" class="primary" data-action="seal">${t('setup.seal')}</button>
    </div>
  </section>

  <section class="card">
    <h2>${t('setup.steps')}</h2>
    ${steps}
    <p class="hint">${t('setup.hideNote')}</p>
  </section>

  <section class="card">
    <button type="button" class="primary" data-action="download">${t('setup.download')}</button>
    <p class="hint">${t('setup.downloadHint')}</p>
  </section>`;
}

function interruptedView() {
  return `
  <section class="card hero">
    <h1>${t('interrupted.title')}</h1>
    <p class="lede">${t('interrupted.body')}</p>
    <div class="row mt-16">
      <button type="button" class="primary" data-action="forget">${t('interrupted.discard')}</button>
      <button type="button" data-action="seal">${t('interrupted.keep')}</button>
    </div>
  </section>`;
}

function sealedView() {
  const until = Date.parse(vault.revealAt);
  const paths = [];
  if (vault.time) {
    paths.push(`<div class="meta-row"><span class="k">${t('sealed.timePath')}</span>
      <span class="v">${t('sealed.timeDetail', { round: vault.round })}</span></div>`);
  }
  if (vault.partner) {
    paths.push(`<div class="meta-row"><span class="k">${t('sealed.partnerPath')}</span>
      <span class="v"><span class="fingerprint">${esc(vault.partner.fingerprint)}</span><br>${t('sealed.partnerDetail')}</span></div>`);
  }
  const request = requestJson();
  return `
  <section class="card hero">
    <div class="lock" aria-hidden="true">
      <svg viewBox="0 0 24 24"><rect x="4" y="10" width="16" height="11" rx="2.5"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/></svg>
    </div>
    <h1>${t('sealed.title')}</h1>
    <p class="lede">${t('sealed.opensOn', { date: formatDate(until) })}</p>
    <p class="mini" data-remaining data-until="${until}">${t('sealed.remaining', { d: Math.floor((until - Date.now()) / 86400000), h: Math.floor(((until - Date.now()) % 86400000) / 3600000) })}</p>
  </section>

  <section class="card">
    <div class="meta">${paths.join('')}</div>
    ${vault.time ? `<button type="button" class="primary" data-action="open-time">${t('sealed.open')}</button>
      <p class="hint">${t('sealed.openEarly')}</p>` : ''}
    <div class="error" id="error" role="alert"></div>
  </section>

  ${vault.partner ? `
  <section class="card">
    <button type="button" data-action="toggle-request">${t('sealed.request')}</button>
    <div class="release ${showRequest ? '' : 'hidden'}" data-release>
      <p class="hint">${t('sealed.requestHint')}</p>
      <textarea readonly data-request>${esc(request)}</textarea>
      <div class="row"><button type="button" data-action="copy-request">${t('sealed.copyRequest')}</button></div>
      <label class="field"><span>${t('sealed.releaseKey')}</span>
        <input type="text" name="release" spellcheck="false" autocomplete="off" placeholder="${t('sealed.releasePlaceholder')}">
      </label>
      <div><button type="button" class="primary" data-action="open-release">${t('sealed.openWithKey')}</button></div>
      <div class="error" id="release-error" role="alert"></div>
    </div>
  </section>` : ''}

  <section class="card">
    <div class="row">
      <button type="button" data-action="download">${t('sealed.download')}</button>
      <button type="button" class="danger" data-action="forget">${t('sealed.forget')}</button>
    </div>
    <p class="hint">${t('sealed.forgetHint')}</p>
  </section>`;
}

function dueView() {
  return `
  <section class="card hero">
    <h1>${t('due.title')}</h1>
    <p class="lede">${t('due.lede')}</p>
    <div class="row mt-16">
      ${vault.time ? `<button type="button" class="primary" data-action="open-time">${t('due.open')}</button>` : ''}
    </div>
    <div class="error" id="error" role="alert"></div>
  </section>

  ${vault.partner ? `
  <section class="card">
    <button type="button" data-action="toggle-request">${t('sealed.request')}</button>
    <div class="release ${showRequest ? '' : 'hidden'}" data-release>
      <p class="hint">${t('sealed.requestHint')}</p>
      <textarea readonly data-request>${esc(requestJson())}</textarea>
      <div class="row"><button type="button" data-action="copy-request">${t('sealed.copyRequest')}</button></div>
      <label class="field"><span>${t('sealed.releaseKey')}</span>
        <input type="text" name="release" spellcheck="false" autocomplete="off" placeholder="${t('sealed.releasePlaceholder')}">
      </label>
      <div><button type="button" class="primary" data-action="open-release">${t('sealed.openWithKey')}</button></div>
      <div class="error" id="release-error" role="alert"></div>
    </div>
  </section>` : ''}

  <section class="card">
    <div class="row">
      <button type="button" data-action="download">${t('sealed.download')}</button>
      <button type="button" class="danger" data-action="forget">${t('sealed.forget')}</button>
    </div>
  </section>`;
}

function revealedView() {
  return `
  <section class="card hero">
    <p class="eyebrow">${t('revealed.title')}</p>
    <div class="code-display" data-code>${esc(code.split('').join(' '))}</div>
    <div class="row">
      <button type="button" class="primary" data-action="copy-code">${t('setup.copy')}</button>
      <button type="button" data-action="close">${t('revealed.close')}</button>
    </div>
  </section>

  <section class="card">
    <button type="button" class="danger" data-action="new-vault">${t('revealed.new')}</button>
  </section>`;
}

const VIEWS = {
  start: startView, setup: setupView, interrupted: interruptedView,
  sealed: sealedView, due: dueView, revealed: revealedView,
};

function render() {
  shell();
  const view = currentView();
  app.innerHTML = VIEWS[view]();
  if (error) setError(error);
  const block = app.querySelector('[data-partner-block]');
  if (block) syncPartnerBlock();
  refreshOpenDate();
}

function refreshOpenDate() {
  const form = document.getElementById('create-form');
  if (!form) return;
  const chip = form.querySelector('.chip[aria-pressed="true"]');
  const output = form.querySelector('[data-open-date]');
  if (!output) return;
  let ms;
  if (chip?.dataset.days === 'custom') {
    const value = form.querySelector('input[name=custom]').value;
    ms = value ? new Date(value).getTime() : NaN;
  } else {
    ms = Date.now() + Number(chip?.dataset.days || 7) * 86400 * 1000;
  }
  output.textContent = Number.isFinite(ms) && ms > Date.now()
    ? t('sealed.opensOn', { date: formatDate(ms) })
    : '';
}

// ------------------------------------------------------------------ partner key input

function partnerText() {
  return app.querySelector('textarea[name=partner]')?.value.trim() || '';
}

function syncPartnerBlock() {
  const element = app.querySelector('[data-fingerprint]');
  if (!element) return;
  if (!partnerText()) {
    element.textContent = '';
    fingerprint = null;
    return;
  }
  parsePartnerKey(partnerText()).then(async (jwk) => {
    fingerprint = await partnerFingerprint(jwk);
    element.textContent = fingerprint;
    const mode = app.querySelector('input[name=mode]:checked');
    if (mode && mode.value === 'time') {
      const both = app.querySelector('input[name=mode][value=both]');
      if (both) both.checked = true;
    }
  }).catch(() => {
    fingerprint = null;
    element.textContent = t('error.partner');
  });
}

async function parsePartnerKey(text) {
  let data;
  try { data = JSON.parse(text); } catch { throw new Error('not json'); }
  const jwk = data.publicJwk || data;
  if (!jwk || jwk.kty !== 'EC' || jwk.crv !== 'P-256' || !jwk.x || !jwk.y) throw new Error('not a P-256 key');
  await publicRawFromJwk(jwk);
  return jwk;
}

function requestJson() {
  return JSON.stringify({
    hisn: 'vault-release-request',
    v: 1,
    fingerprint: vault.partner.fingerprint,
    envelope: vault.partner.envelope,
  }, null, 2);
}

// ------------------------------------------------------------------ actions

async function create() {
  const form = document.getElementById('create-form');
  if (!form || busy) return;
  const picked = form.querySelector('.chip[aria-pressed="true"]');
  const choice = picked?.dataset.days || '7';
  let revealAt;
  if (choice === 'custom') {
    const value = form.querySelector('input[name=custom]').value;
    revealAt = value ? new Date(value).getTime() : NaN;
    if (!Number.isFinite(revealAt)) return setError(t('error.date'));
  } else {
    revealAt = Date.now() + Number(choice) * 86400 * 1000;
  }
  if (!form.querySelector('input[name=ack]').checked) return setError(t('start.understand'));

  const mode = form.querySelector('input[name=mode]:checked').value;
  let partnerJwk = null;
  if (mode !== 'time') {
    try { partnerJwk = await parsePartnerKey(partnerText()); }
    catch { return setError(t('error.partner')); }
  }

  busy = true;
  setError(null);
  try {
    const made = await createVault({
      revealAt,
      label: form.querySelector('select[name=label]').value,
      mode,
      partnerPublicJwk: partnerJwk,
    });
    vault = made.vault;
    code = made.passcode;
    store.saveVault(vault);
    render();
  } catch (caught) {
    setError(caught.message === 'the date must be in the future' ? t('error.date') : t('error.generic'));
  } finally {
    busy = false;
  }
}

function forget(confirmed = false) {
  if (!confirmed && !confirm(t('sealed.forgetConfirm'))) return;
  store.clearVault();
  vault = null;
  code = null;
  showRequest = false;
  error = null;
  render();
}

async function openTime() {
  setError(null);
  try {
    const opened = await openWithTime(vault);
    code = opened.code;
    render();
  } catch (caught) {
    const message = String(caught && caught.message);
    if (/too early/i.test(message)) setError(t('error.notYet'));
    else if (/fetch|network|Failed|404|beacon/i.test(message)) setError(t('error.offline'));
    else setError(t('error.generic'));
  }
}

async function openRelease() {
  const input = app.querySelector('input[name=release]');
  const box = document.getElementById('release-error');
  const fail = (text) => {
    if (box) { box.textContent = text; box.classList.add('show'); }
  };
  const raw = (input?.value || '').trim().replace(/^HISN-VK-/, '');
  let key;
  try { key = fromB64u(raw); } catch { return fail(t('error.badKey')); }
  if (key.length !== 32) return fail(t('error.badKey'));
  try {
    code = await readCode(vault, key);
    render();
  } catch {
    fail(t('error.badKey'));
  }
}

async function importVault(file) {
  const box = document.getElementById('import-error');
  try {
    const data = await store.readJsonFile(file);
    const check = inspectVault(data);
    if (!check.ok) throw new Error(check.problems.join(', '));
    if (vault && !confirm(t('start.importConfirm'))) return;
    vault = data;
    code = null;
    store.saveVault(vault);
    render();
  } catch {
    if (box) { box.textContent = t('error.file'); box.classList.add('show'); }
  }
}

function pickDays(event) {
  const button = event.target.closest('[data-days]');
  if (!button) return;
  const form = document.getElementById('create-form');
  for (const chip of form.querySelectorAll('.chip')) chip.setAttribute('aria-pressed', String(chip === button));
  const custom = form.querySelector('input[name=custom]');
  custom.classList.toggle('hidden', button.dataset.days !== 'custom');
  refreshOpenDate();
}

// ------------------------------------------------------------------ wiring

app.addEventListener('click', (event) => {
  const target = event.target.closest('[data-action]');
  if (!target) return;
  const action = target.dataset.action;
  if (action === 'pick-days') return pickDays(event);
  if (action === 'create') return create();
  if (action === 'seal') {
    vault = sealVault(vault);
    store.saveVault(vault);
    code = null;
    render();
    return;
  }
  if (action === 'copy-code') {
    const text = app.querySelector('[data-code]').textContent.replace(/\s+/g, '');
    const button = target;
    store.copyText(text).then((ok) => { if (ok) button.textContent = t('setup.copied'); });
    return;
  }
  if (action === 'download') {
    store.downloadJson(vault, store.vaultFileName(vault));
    return;
  }
  if (action === 'forget') return forget();
  if (action === 'new-vault') {
    if (!confirm(t('revealed.newConfirm'))) return;
    return forget(true);
  }
  if (action === 'open-time') return openTime();
  if (action === 'toggle-request') {
    showRequest = !showRequest;
    app.querySelector('[data-release]').classList.toggle('hidden', !showRequest);
    return;
  }
  if (action === 'copy-request') {
    const text = app.querySelector('[data-request]').value;
    store.copyText(text).then((ok) => { if (ok) target.textContent = t('setup.copied'); });
    return;
  }
  if (action === 'open-release') return openRelease();
  if (action === 'close') { code = null; render(); }
});

let partnerTimer = 0;
app.addEventListener('input', (event) => {
  if (!event.target.matches('textarea[name=partner]')) return;
  clearTimeout(partnerTimer);
  partnerTimer = setTimeout(syncPartnerBlock, 220);
});

app.addEventListener('change', (event) => {
  if (event.target.matches('input[data-action=import]')) {
    const file = event.target.files?.[0];
    if (file) importVault(file);
  }
  if (event.target.matches('input[name=custom]')) refreshOpenDate();
  if (event.target.matches('input[name=mode]')) {
    // nothing to re-render: the partner block stays visible so a key can be pasted
  }
});

document.querySelector('[data-lang-button]').addEventListener('click', () => {
  setLang(lang() === 'ar' ? 'en' : 'ar');
  render();
});

// ------------------------------------------------------------------ ticker

setInterval(() => {
  const view = currentView();
  if (view === 'setup') {
    const ends = Date.parse(vault.createdAt) + SETUP_WINDOW_MS;
    const left = ends - Date.now();
    if (left <= 0) {
      code = null;
      render();
      return;
    }
    const lede = app.querySelector('[data-setup-lede]');
    if (lede) lede.textContent = t('setup.lede', { time: clock(left) });
    return;
  }
  if (view === 'sealed') {
    if (phase(vault).state !== 'sealed') return render();
    const remaining = app.querySelector('[data-remaining]');
    if (remaining) {
      const until = Number(remaining.dataset.until);
      const left = Math.max(0, until - Date.now());
      remaining.textContent = t('sealed.remaining', {
        d: Math.floor(left / 86400000), h: Math.floor((left % 86400000) / 3600000),
      });
    }
  }
}, 1000);

setLang(detectLang());
render();
