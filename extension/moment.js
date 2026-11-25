/**
 * The pause and closed page for a distraction rule.
 *
 * It shows what the worker stored for this tab and nothing else: the page it
 * replaced is never read, and the rule itself lives in the macOS app. Walking
 * away is always the first button.
 */

import { initLanguage, t } from "./lib/i18n.js";
import { clock } from "./lib/distraction.js";

await initLanguage();

const $ = (id) => document.getElementById(id);
const ask = (type) => chrome.runtime.sendMessage({ type }).catch(() => null);
const CIRCUMFERENCE = 326.7;

function leave() {
  // A blank new tab in place of this one; closing the last tab would close the window.
  chrome.tabs.create({}).then(() => window.close()).catch(() => window.close());
}

function timeOfDay(ms) {
  return new Date(ms).toLocaleTimeString(document.documentElement.lang || undefined, { hour: "numeric", minute: "2-digit" });
}

function show(info) {
  const main = $("moment");
  main.dataset.mode = info.mode;
  $("eyebrow").textContent = info.ruleName;
  const own = $("own");
  own.hidden = !info.own;
  own.textContent = info.own ? t("moment.own", info.own) : "";
  if (info.mode === "wait") wait(info); else closed(info);
}

function wait(info) {
  $("title").textContent = t("moment.wait.title", info.host);
  $("detail").textContent = t("moment.wait.detail");
  $("ring").hidden = false;
  const primary = $("primary"), open = $("secondary");
  primary.textContent = t("moment.wait.notNow");
  primary.onclick = leave;
  open.hidden = false;
  open.disabled = true;
  open.textContent = t("moment.wait.open", info.openMinutes);
  $("foot").textContent = info.opensLeft === null ? "" : t("moment.wait.opensLeft", info.opensLeft);
  const started = Date.now();
  const tick = () => {
    const left = info.seconds - (Date.now() - started) / 1000;
    $("ringNumber").textContent = String(Math.max(0, Math.ceil(left)));
    $("ringFill").style.strokeDashoffset = String(CIRCUMFERENCE * Math.max(0, left / info.seconds));
    if (left <= 0) { open.disabled = false; return; }
    setTimeout(tick, 250);
  };
  tick();
  open.onclick = async () => {
    open.disabled = true;
    const reply = await ask("distractionOpen");
    if (!reply?.ok) { $("foot").textContent = t("moment.appMissing"); open.disabled = false; }
  };
}

function closed(info) {
  $("glyph").hidden = false;
  $("glyph").textContent = info.reason === "focus" ? "🎯" : info.reason === "limitReached" ? "⏳" : "🌙";
  $("title").textContent = t(`moment.closed.${info.reason}`, info.host);
  $("detail").textContent = info.reason === "focus" && info.intent ? t("moment.closed.intent", info.intent)
    : info.until ? t("moment.closed.until", timeOfDay(info.until))
    : info.reason === "limitReached" || info.reason === "noOpensLeft" ? t("moment.closed.tomorrow")
    : t("moment.closed.calm");
  const primary = $("primary"), snooze = $("secondary"), breathe = $("breathe");
  primary.textContent = t("moment.close");
  primary.onclick = leave;
  breathe.hidden = false;
  breathe.onclick = () => {
    $("breath").hidden = false;
    breathe.hidden = true;
    const word = $("breathWord");
    const turn = () => { word.textContent = (Date.now() / 1000) % 10 < 4 ? t("moment.breatheIn") : t("moment.breatheOut"); };
    turn();
    setInterval(turn, 500);
  };
  if (info.snoozeDelay === null) {
    if (info.reason !== "focus") $("foot").textContent = t("moment.locked");
  } else {
    snooze.hidden = false;
    snooze.disabled = true;
    const ready = info.at + info.snoozeDelay * 1000;
    const tick = () => {
      const left = (ready - Date.now()) / 1000;
      if (left <= 0) { snooze.disabled = false; snooze.textContent = t("moment.snooze"); return; }
      snooze.textContent = t("moment.snoozeIn", clock(left));
      setTimeout(tick, 500);
    };
    tick();
    snooze.onclick = async () => {
      snooze.disabled = true;
      const reply = await ask("distractionSnooze");
      if (!reply?.ok) $("foot").textContent = t("moment.locked");
    };
  }
  // The rule may end while this page is open: a window closes, a Focus is over.
  setInterval(() => { ask("distractionRecheck"); }, 30 * 1000);
}

const reply = await ask("distractionInfo");
if (reply?.ok && reply.info) {
  show(reply.info);
} else {
  // Opened by hand, or the worker forgot this tab: nothing to hold, so step aside.
  const main = $("moment");
  main.dataset.mode = "closed";
  $("title").textContent = t("moment.docTitle");
  $("detail").textContent = t("moment.appMissing");
  $("primary").textContent = t("moment.close");
  $("primary").onclick = leave;
}
