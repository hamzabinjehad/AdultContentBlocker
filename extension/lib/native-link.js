/**
 * One open line to the native app instead of a new process per question.
 *
 * `sendNativeMessage` starts the bridge, asks once and lets it exit. Measured
 * on the release bridge that costs about 24 ms a question, nearly all of it
 * the launch. The bridge already answers in a loop, so a port that stays open
 * answers in under a millisecond.
 *
 * The bridge replies in the order it was asked and carries no ids, so the
 * pending questions are a queue. Anything that could put the queue out of
 * step (a timeout, a disconnect) closes the port and the unanswered questions
 * are asked again one at a time, the old way. A browser where a port never
 * answers (Safari routes native messages through its app extension) is left
 * on the old way for a while rather than retried on every page.
 *
 * Pure: the transport and the timers are handed in, so this runs in tests.
 */

export const IDLE_CLOSE_MS = 60 * 1000;
export const PORT_RETRY_MS = 5 * 60 * 1000;

export function createLink({ connect, send, now = () => Date.now(),
                             setTimer = (fn, ms) => setTimeout(fn, ms),
                             clearTimer = (id) => clearTimeout(id) }) {
  let port = null;
  let pending = [];
  let answered = 0;
  let idle = null;
  let portOffUntil = 0;

  function oneShot(message, timeoutMs) {
    return new Promise((resolve, reject) => {
      const timer = setTimer(() => reject(new Error("timed out")), timeoutMs);
      Promise.resolve().then(() => send(message)).then(
        (reply) => { clearTimer(timer); resolve(reply); },
        (error) => { clearTimer(timer); reject(error); });
    });
  }

  function drop(reason) {
    const closing = port;
    const waiting = pending;
    port = null;
    pending = [];
    if (idle != null) { clearTimer(idle); idle = null; }
    // A port that never answered once is not worth opening again soon.
    if (answered === 0) portOffUntil = now() + PORT_RETRY_MS;
    answered = 0;
    try { closing?.disconnect(); } catch { /* already gone */ }
    for (const item of waiting) {
      clearTimer(item.timer);
      if (reason === "timeout" && item.timedOut) item.reject(new Error("timed out"));
      else oneShot(item.message, item.timeoutMs).then(item.resolve, item.reject);
    }
  }

  function open() {
    if (port || typeof connect !== "function" || now() < portOffUntil) return port;
    try {
      const opened = connect();
      if (!opened) throw new Error("no port");
      opened.onMessage.addListener((reply) => {
        if (opened !== port) return;
        const item = pending.shift();
        if (!item) return;
        answered += 1;
        clearTimer(item.timer);
        item.resolve(reply);
      });
      opened.onDisconnect.addListener(() => { if (opened === port) drop("disconnect"); });
      port = opened;
    } catch {
      port = null;
      portOffUntil = now() + PORT_RETRY_MS;
    }
    return port;
  }

  function touch() {
    if (idle != null) clearTimer(idle);
    idle = setTimer(() => { idle = null; if (port && pending.length === 0) { answered = Math.max(answered, 1); drop("idle"); } }, IDLE_CLOSE_MS);
  }

  /** Ask the app one question. Rejects on a timeout or when the app is absent. */
  function ask(message, timeoutMs) {
    if (!open()) return oneShot(message, timeoutMs);
    return new Promise((resolve, reject) => {
      const item = { message, timeoutMs, resolve, reject, timedOut: false, timer: null };
      item.timer = setTimer(() => { item.timedOut = true; drop("timeout"); }, timeoutMs);
      pending.push(item);
      touch();
      try {
        port.postMessage(message);
      } catch {
        drop("disconnect");
      }
    });
  }

  return { ask, close: () => { if (port) { answered = Math.max(answered, 1); drop("idle"); } }, isOpen: () => port != null };
}
