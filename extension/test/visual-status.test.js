import { describeVisual } from '../lib/visual-status.js';
import { t } from '../lib/i18n.js';

let checks = 0;
const check = (ok, label) => { checks++; if (!ok) throw new Error(label); };
const ready = { ok: true, available: true, state: 'ready' };
const base = {
  inspectVisual: true, personBlur: 'off', desiredRevision: 3, appliedRevision: 3,
  enforcement: { version: 1, status: 'applied', desiredRevision: 3, appliedRevision: 3,
    layers: { domains: 'applied', search: 'applied', text: 'applied', visual: 'applied' } },
};
function withReceipt(receipt, extra = {}) { return { ...base, ...extra, enforcement: receipt }; }

for (const status of [undefined, null, { ok: false }, { available: false, state: 'ready' },
  { available: true, state: 'unavailable' }]) {
  const result = describeVisual(status, base);
  check(result.level !== 'active' && result.available === false, 'missing/unavailable engine cannot report active visual checks');
}
{
  const result = describeVisual(ready, base);
  check(result.level === 'active' && result.text === t('visual.status.ready'), 'ready engine plus current visual installation can report ready');
  check(result.available === true, 'installed visual engine remains selectable');
}
for (const receipt of [undefined, {}, { ...base.enforcement, version: 2 },
  { ...base.enforcement, desiredRevision: 2 }, { ...base.enforcement, appliedRevision: 2 },
  { ...base.enforcement, status: 'pending' },
  { ...base.enforcement, layers: { ...base.enforcement.layers, visual: 'pending' } },
  { ...base.enforcement, layers: { domains: 'applied', search: 'applied', text: 'applied' } }]) {
  const result = describeVisual(ready, withReceipt(receipt));
  check(result.level === 'unknown' && result.text === t('enforcement.pending'), 'ready model cannot hide missing, stale, or pending scanner installation');
  check(result.available === true, 'an installation problem does not disable model controls');
}
{
  const result = describeVisual(ready, { ...base, appliedRevision: 2 });
  check(result.level === 'unknown' && result.text === t('enforcement.pending'), 'stale state-side applied revision cannot report ready');
}
{
  const receipt = { ...base.enforcement, layers: { ...base.enforcement.layers, visual: 'off' } };
  const result = describeVisual(ready, withReceipt(receipt));
  check(result.level !== 'active' && result.text === t('details.off') && result.available,
    'a requested visual layer that is actually off is never reported ready');
}
{
  const receipt = { ...base.enforcement, status: 'failed', layers: { ...base.enforcement.layers, visual: 'failed' } };
  const result = describeVisual(ready, withReceipt(receipt));
  check(result.level === 'partial' && result.text === t('enforcement.failed') && result.available,
    'a ready model does not conceal failed scanner installation');
}
{
  const receipt = { ...base.enforcement, status: 'failed', layers: { ...base.enforcement.layers, domains: 'failed' } };
  const result = describeVisual(ready, withReceipt(receipt));
  check(result.level === 'active', 'a separate domain-layer failure does not erase a current successful visual receipt');
}
for (const state of [undefined, {ok: false}]) {
  const result = describeVisual(ready, state);
  check(result.level === 'unknown' && result.text === t('visual.status.checking') && result.available,
    'a warm engine with missing preferences does not invent a disabled choice');
}
for (const state of [{ ...base, inspectVisual: false, personBlur: 'off' }]) {
  const result = describeVisual(ready, state);
  check(result.level !== 'active' && result.text === t('visual.status.disabled') && result.available,
    'a warm engine without a requested visual feature does not claim active checking');
}
for (const personBlur of ['all', 'female', 'male']) {
  const result = describeVisual(ready, { ...base, inspectVisual: false, personBlur });
  check(result.level === 'active', 'each selected person-protection mode uses the same installation requirement');
}
for (const state of ['failed', 'loading', 'waiting']) {
  const result = describeVisual({ ...ready, state }, base);
  check(result.level === 'partial' && result.text === t(`visual.status.${state}`) && result.available,
    'a verified installation still reports the independent model state honestly');
}
{
  const result = describeVisual({ ...ready, state: 'unexpected' }, base);
  check(result.level === 'unknown' && result.text === t('visual.status.checking'), 'unknown model state cannot imply active checks or a deliberate opt-out');
}
print(`  ${checks}/${checks} visual status checks passed`);
