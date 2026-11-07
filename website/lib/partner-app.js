// The partner's page.
//
// The private key is generated here and never leaves this browser. A release
// request is an encrypted envelope; opening it yields a random 32-byte release
// key that the person's vault accepts. The partner never sees the code, and the
// page has no network access at all (connect-src 'none').

import { b64u, makePartnerKeypair, partnerFingerprint, partnerOpen } from './crypto.js';
import { detectLang, lang, setLang, t } from './copy.js';
import * as store from './store.js';

const app = document.getElementById('app');

let partner = store.loadPartner();
let output = '';
let error = '';
let note = '';
let busy = false;

const esc = (value) => String(value).replace(/[&<>"']/g, (c) => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
}[c]));

function shell() {
  document.getElementById('footer-note').textContent = t('partner.footer');
  document.getElementById('footer-vault').textContent = t('vault');
  document.querySelector('[data-lang-button]').textContent = t('lang');
  const pill = document.getElementById('surface-pill');
  if (pill) pill.textContent = t('partner.pill');
}

function render() {
  shell();
  app.innerHTML = partner ? partnerView() : emptyView();
  const fp = app.querySelector('[data-fingerprint]');
  if (fp && partner) {
    partnerFingerprint(partner.publicJwk).then((value) => { fp.textContent = value; });
  }
  if (output) {
    const box = app.querySelector('[data-release-key]');
    if (box) box.textContent = output;
  }
  if (error) {
    const box = app.querySelector('[data-error]');
    if (box) { box.textContent = error; box.classList.add('show'); }
  }
  if (note) {
    const box = app.querySelector('[data-note]');
    if (box) { box.textContent = note; box.classList.add('show'); }
  }
}

function emptyView() {
  return `
  <section class="card hero">
    <h1>${t('partner.title')}</h1>
    <p class="lede">${t('partner.lede')}</p>
  </section>
  <section class="card">
    <p class="hint">${t('partner.empty')}</p>
    <button type="button" class="primary mt-8" data-action="create-key">${t('partner.create')}</button>
  </section>`;
}

function partnerView() {
  return `
  <section class="card hero">
    <h1>${t('partner.title')}</h1>
    <p class="lede">${t('partner.lede')}</p>
    <p class="mini mt-16">${t('partner.fingerprint')}: <span class="fingerprint" data-fingerprint>…</span></p>
  </section>

  <section class="card">
    <h2>${t('partner.publicKey')}</h2>
    <textarea readonly class="mt-8" data-public>${esc(JSON.stringify(partner.publicJwk))}</textarea>
    <div class="row mt-16">
      <button type="button" data-action="copy-public">${t('partner.copy')}</button>
      <button type="button" data-action="backup">${t('partner.backup')}</button>
      <button type="button" data-action="replace" class="danger">${t('partner.replace')}</button>
    </div>
    <label class="button mt-8">
      ${t('partner.restore')}
      <input type="file" accept="application/json,.json" data-action="restore" hidden>
    </label>
    <div class="note" data-note role="status"></div>
  </section>

  <section class="card">
    <h2>${t('partner.requestTitle')}</h2>
    <p class="hint">${t('partner.requestHint')}</p>
    <textarea class="mt-8" data-request spellcheck="false" placeholder='{"hisn":"vault-release-request", …}'></textarea>
    <div class="row mt-16">
      <button type="button" class="primary" data-action="release">${t('partner.requestButton')}</button>
    </div>
    <div class="error" data-error role="alert"></div>
    ${output ? `
      <h3 class="mt-16">${t('partner.releaseTitle')}</h3>
      <p class="hint">${t('partner.releaseHint')}</p>
      <div class="release-key mt-8" data-release-key>${esc(output)}</div>
      <div class="row mt-8"><button type="button" data-action="copy-release">${t('partner.copy')}</button></div>` : ''}
  </section>`;
}

async function createKey() {
  if (busy) return;
  busy = true;
  const pair = await makePartnerKeypair();
  partner = { v: 1, kind: 'hisn-partner-key', createdAt: new Date().toISOString(), ...pair };
  store.savePartner(partner);
  note = t('partner.created');
  busy = false;
  render();
}

function replaceKey() {
  if (!confirm(t('partner.replaceConfirm'))) return;
  store.clearPartner();
  partner = null;
  output = '';
  note = '';
  error = '';
  render();
}

async function release() {
  error = '';
  output = '';
  const text = app.querySelector('[data-request]').value.trim();
  let data;
  try { data = JSON.parse(text); } catch { error = t('partner.badRequest'); return render(); }
  const envelope = data?.envelope || data?.partner?.envelope;
  if (!envelope) { error = t('partner.badRequest'); return render(); }
  try {
    const mine = await partnerFingerprint(partner.publicJwk);
    if (data.fingerprint && data.fingerprint !== mine) throw new Error('fingerprint');
    const key = await partnerOpen(partner.privateJwk, envelope);
    if (key.length !== 32) throw new Error('key length');
    output = `HISN-VK-${b64u(key)}`;
    render();
  } catch {
    error = t('partner.badRequest');
    render();
  }
}

async function restore(file) {
  try {
    const data = await store.readJsonFile(file);
    if (!data?.publicJwk || !data?.privateJwk) throw new Error('shape');
    await partnerFingerprint(data.publicJwk);
    partner = data;
    store.savePartner(partner);
    note = t('partner.restored');
    output = '';
    error = '';
    render();
  } catch {
    error = t('partner.badRequest');
    render();
  }
}

app.addEventListener('click', (event) => {
  const target = event.target.closest('[data-action]');
  if (!target) return;
  const action = target.dataset.action;
  if (action === 'create-key') return createKey();
  if (action === 'replace') return replaceKey();
  if (action === 'release') return release();
  if (action === 'backup') return store.downloadJson(partner, store.partnerFileName(partner));
  if (action === 'copy-public') {
    store.copyText(JSON.stringify(partner.publicJwk)).then((ok) => { if (ok) target.textContent = t('partner.copied'); });
    return;
  }
  if (action === 'copy-release') {
    store.copyText(output).then((ok) => { if (ok) target.textContent = t('partner.copied'); });
  }
});

app.addEventListener('change', (event) => {
  if (event.target.matches('input[data-action=restore]')) {
    const file = event.target.files?.[0];
    if (file) restore(file);
  }
});

document.querySelector('[data-lang-button]').addEventListener('click', () => {
  setLang(lang() === 'ar' ? 'en' : 'ar');
  render();
});

setLang(detectLang());
render();
