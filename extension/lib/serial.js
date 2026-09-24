/**
 * One lock for the worker's state.
 *
 * Every mutation of `state` is a read-modify-write — `{ ...await getState(),
 * ...patch }` — and the service worker runs several of them concurrently: the
 * native heartbeat lands while an options-page message is being handled while
 * a block page files a report. Two interleaved writers each read the same
 * snapshot and the second overwrites the first's patch. Which patch is lost
 * depends on timing; when it is `failClosed: true` or a custom block, the
 * loss is a restriction. `applyRules` has the same race one level down: two
 * concurrent calls both read the dynamic rules, and the second `addRules`
 * collides on ids with the first and throws, leaving the rules half-applied.
 *
 * `createLock()` returns `run(fn)`: `fn` starts only after every previously
 * queued function has settled, whether it resolved or threw. The chain never
 * poisons — a failure is reported to its own caller and the next function
 * runs. Callers hold the lock for one whole transaction (read, decide, write,
 * apply) and never call `run` from inside a running `fn`, which would wait
 * for itself.
 */
export function createLock() {
  let tail = Promise.resolve();
  return function run(fn) {
    const turn = tail.then(fn, fn);
    tail = turn.then(() => {}, () => {});
    return turn;
  };
}
