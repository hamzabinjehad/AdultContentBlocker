import { validateCapture, visualPreferences, adultDecision, appearancePrediction, needsAppearanceConfirmation, confirmAppearance, faceDetections,
  personDecision, squareFrame, unpadFaces } from '../visual/decisions.js';
import { LocalInferenceQueue } from '../visual/queue.js';
import {visualPolicyWeakens,validatePatch} from '../lib/policy.js';
let checks = 0;
const check = (ok, label) => { checks++; if (!ok) throw new Error(label); };
const scores = values => ['Porn', 'Hentai', 'Sexy', 'Neutral', 'Drawing'].map((className, index) => ({ className, probability: values[index] }));
const lockedPerson={personBlur:'female',personRegion:'full',personEffect:'solid',personMedia:'both'};
check(visualPolicyWeakens(lockedPerson,{personRegion:'faces'}),'a commitment cannot shrink full protection to faces');
check(visualPolicyWeakens(lockedPerson,{personEffect:'blur'}),'a commitment cannot replace opaque protection with blur');
check(visualPolicyWeakens(lockedPerson,{personMedia:'images'}),'a commitment cannot remove video coverage');
check(visualPolicyWeakens({...lockedPerson,personMedia:'images'},{personMedia:'videos'}),'switching scope cannot silently discard image protection');
check(!visualPolicyWeakens({...lockedPerson,personRegion:'faces',personMedia:'images'},{personRegion:'full',personMedia:'both'}),'widening area and media scope is a strengthening');
check(!visualPolicyWeakens({...lockedPerson,personEffect:'blur'},{personEffect:'solid'}),'a commitment can strengthen blur to an opaque mask');
check(validateCapture({ id: 'one', width: 1, height: 1, rgba: 'AAAAAA==' }), 'bounded RGBA accepted');
for (const bad of [{ width: 385 }, { height: -1 }, { width: 1.2 }, { rgba: 'data:' }, { id: '../path' }]) {
  check(!validateCapture({ id: 'one', width: 1, height: 1, rgba: 'AAAAAA==', ...bad }), 'malformed capture rejected');
}
check(visualPreferences({ inspectVisual: 'true', personBlur: 'anything' }).personBlur === 'off', 'unknown preferences disabled');
check(!adultDecision(scores([.05, .02, .90, .02, .01])).block, 'Sexy alone is not an adult verdict');
check(adultDecision(scores([.75, .02, .02, .20, .01])).block, 'high explicit adult score blocks');
check(adultDecision(scores([.43, .43, .02, .11, .01])).block, 'combined adult classes can block');
check(adultDecision(scores([.01, .01, .02, .95, .01])).block === false, 'ordinary model verdict remains available');
check(adultDecision(scores([.8, .8, .1, .1, .1])) === null, 'invalid probabilities cannot become clean');
check(adultDecision(scores([NaN, 0, 0, 1, 0])) === null, 'nonfinite probabilities rejected');
const face = { faceScore: .9, box: [10, 10, 40, 40], gender: 'female', genderScore: .95 };
const detectorOnly = faceDetections({ face: [{ faceScore: 0, boxScore: .95, score: .95, box: face.box }] });
check(personDecision(detectorOnly, [], 'all', 100, 100).blur, 'detector confidence used when face mesh is disabled');
check(faceDetections({ error: 'inference failed', face: [] }) === null, 'detector error is unknown, not no-face verdict');
check(faceDetections({}) === null, 'missing detector output is unknown');
check(faceDetections({ face: [null] }) === null, 'malformed face output is unknown without throwing');
check(faceDetections({ face: [{ boxScore: 1.5, box: face.box }] }) === null, 'out-of-range detector score rejected');
check(faceDetections({ face: [{ boxScore: .9, box: [10, 10, -4, 40] }] }) === null, 'invalid detector box rejected');
check(appearancePrediction([.92, .08]).gender === 'female', 'packaged appearance model female output index preserved');
check(appearancePrediction([.09, .91]).gender === 'male', 'packaged appearance model male output index preserved');
check(appearancePrediction([.5, .5]) === null, 'a tied appearance output is unknown rather than an invented selected estimate');
check(appearancePrediction([.5000001, .5]) === null, 'near-tied numeric noise cannot invent a selected estimate');
for (const invalid of [[NaN, .1], [1.1, -.1], [.9, .9], [.8], null]) {
  check(appearancePrediction(invalid) === null, 'invalid appearance output cannot become a confident match');
}
for (const [probabilities, mode, required] of [
  [[.1,.9], 'female', true], [[.9,.1], 'male', true],
  [[.2,.8], 'female', true], [[.3,.7], 'female', false],
  [[.9,.1], 'female', false], [[.1,.9], 'male', false],
  [[.1,.9], 'all', false], [[.1,.9], 'off', false], [[.5,.5], 'female', false],
]) check(needsAppearanceConfirmation(appearancePrediction(probabilities), mode) === required,
  'only a valid confident opposite estimate needs a mirrored exclusion check');
for (const [primary, mirrored, uncertain] of [
  [[.1,.9], [.12,.88], false], [[.9,.1], [.82,.18], false],
  [[.1,.9], [.8,.2], true], [[.9,.1], [.1,.9], true],
  [[.1,.9], [.21,.79], true], [[.9,.1], [.79,.21], true],
]) {
  const result = confirmAppearance(primary, mirrored), original = appearancePrediction(primary);
  check(result.appearanceUncertain === uncertain && result.gender === original.gender
    && result.genderScore === original.genderScore,
    'two-view check records uncertainty without inventing a different gender or confidence');
  const opposite = original.gender === 'female' ? 'male' : 'female';
  check(personDecision([{...face,...result}], [], opposite, 100,100).blur === uncertain,
    'only confident agreeing opposite views are excluded from the chosen preference');
}
for (const invalid of [[.5,.5], [NaN,.1], [1.1,-.1], [.9,.9], [.8], null]) {
  check(confirmAppearance([.1,.9], invalid) === null && confirmAppearance(invalid, [.1,.9]) === null,
    'both views must be valid and non-tied before a confirmation is usable');
}
const portrait = squareFrame(101, 224), landscape = squareFrame(224, 101);
check(portrait.edge === 224 && portrait.x === 61 && portrait.y === 0, 'portrait capture letterboxed without stretching and odd remainder retained');
check(landscape.edge === 224 && landscape.y === 61 && landscape.x === 0, 'landscape capture letterboxed without stretching');
check(squareFrame(385, 224) === null && squareFrame(0, 100) === null, 'letterbox dimensions stay inside capture budget');
const remapped = unpadFaces([{ ...face, box: [51, 10, 40, 40] }], portrait, 101, 224);
const remappedRegion = personDecision(remapped, [], 'all', 101, 224).regions[0];
check(remappedRegion.x === 0 && Math.abs(remappedRegion.width - 30 / 101) < 1e-9, 'letterbox face coordinates clip the actual overlapping width');
check(unpadFaces([{ ...face, box: [0, 0, 20, 20] }], portrait, 101, 224).length === 0, 'a detection wholly inside padding is not a person in the image');
check(personDecision([{ ...face, box: [-10, -10, 40, 40] }], [], 'all', 100, 100).regions[0].width === .3,
  'partially clipped boxes do not overstate their visible region');
check(personDecision([face], [], 'all', 100, 100).blur, 'visible confident face matches all');
check(personDecision([face], [], 'female', 100, 100).blur, 'chosen appearance estimate can match');
check(!personDecision([face], [], 'male', 100, 100).blur, 'other appearance is not hidden');
check(!personDecision([{ ...face, genderScore: .4 }], [], 'female', 100, 100).blur, 'low-confidence appearance not asserted');
for (const probability of [.51, .543, .568, .674, .735, .799]) {
  check(personDecision([{ ...face, genderScore: probability }], [], 'female', 100, 100).blur,
    'selected leading estimate is not discarded by the former 80-percent cutoff');
  check(personDecision([{ ...face, gender: 'male', genderScore: probability }], [], 'female', 100, 100).blur,
    'an uncertain opposite estimate is withheld without claiming it is a selected gender');
  check(personDecision([{ ...face, gender: 'male', genderScore: probability }], [], 'male', 100, 100).blur,
    'male preference receives the same wider recall as female preference');
}
check(!personDecision([{...face, genderScore: NaN}], [], 'female', 100, 100).blur, 'non-finite appearance confidence never masks a face');
check(!personDecision([{...face, gender:'male',genderScore:.80}], [], 'female', 100, 100).blur,
  'a confident opposite estimate can remain outside the selected preference');
check(!personDecision([{...face, gender:'male',genderScore:1.5}], [], 'female', 100, 100).blur,
  'out-of-range confidence is not evidence of a valid face preference match');
check(!personDecision([{ ...face, faceScore: .1 }], [], 'all', 100, 100).blur, 'low-confidence face not asserted');
check(!personDecision([face], [], 'off', 100, 100).blur, 'person preferences opt-in');
let finish, calls = 0, now = 0;
const queue = new LocalInferenceQueue(async () => { calls++; return new Promise(resolve => { finish = resolve; }); }, { limit: 2, now: () => now });
const first = queue.submit('1:0:a', { capture: {} });
const second = queue.submit('2:0:b', { capture: {} });
check((await queue.submit('3:0:c', {})).reason === 'queue-full', 'inference queue bounded');
queue.cancel('2:0:b');
check((await second).reason === 'cancelled' && queue.pending.length === 0, 'queued bytes released on cancel');
queue.cancel('1:0:a'); finish({ state: 'ready' });
check((await first).reason === 'cancelled' && calls === 1, 'cancelled active verdict not applied');
const active = queue.submit('1:0:d', {});
const expired = queue.submit('2:0:e', {});
now = 13000; finish({ state: 'ready' });
await active;
check((await expired).reason === 'expired' && calls === 2, 'stale waiting frame is not inferred');
const failed = new LocalInferenceQueue(async () => { throw new Error('fixture'); });
check((await failed.submit('1:0:f', {})).state === 'failed', 'inference failure never clean');
const activeCancel = queue.submit('1:0:g', {}), waitingCancel = queue.submit('2:0:h', {});
queue.cancelAll();
check((await waitingCancel).reason === 'cancelled' && queue.pending.length === 0, 'disable releases all waiting frames');
finish({ state: 'ready' });
check((await activeCancel).reason === 'cancelled', 'disable discards active model result');
print(`  ${checks}/${checks} visual checks passed`);
