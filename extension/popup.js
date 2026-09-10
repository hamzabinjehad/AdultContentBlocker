/**
 * Popup — status only.
 *
 * There are intentionally NO controls here. Anything that can turn protection
 * off from a one-click popup is the first thing a person reaches for at 2am.
 * Starting a lock happens in the native app; ending one happens when the timer
 * expires or an accountability partner approves it.
 */

const MODE_LABEL = {
  off: "Off",
  blocklist: "Block list",
  strict: "Strict allowlist",
};

function humanRemaining(ms) {
  if (ms <= 0) return "—";
  const t = Math.floor(ms / 1000);
  const d = Math.floor(t / 86400);
  const h = Math.floor((t % 86400) / 3600);
  const m = Math.floor((t % 3600) / 60);
  if (d) return `${d}d ${h}h`;
  if (h) return `${h}h ${m}m`;
  return `${m}m`;
}

function render(state) {
  const locked = (state.lockUntil || 0) > Date.now();

  // Three states, not two. "Unlocked" alone implied nothing was being blocked,
  // which was never true and is now definitively false: baseline filtering runs
  // whether or not a lock exists and whether or not the app is installed.
  const statusEl = document.getElementById("status");
  statusEl.innerHTML = locked
    ? '<span class="pill">Locked</span>'
    : '<span class="pill on">Protecting</span>';

  document.getElementById("mode").textContent =
    MODE_LABEL[state.failClosed ? "strict" : state.mode] ?? state.mode;
  document.getElementById("left").textContent =
    humanRemaining((state.lockUntil || 0) - Date.now());

  // Report what is ENFORCED, not what was downloaded. A version number climbing
  // while no rules are installed is precisely the "looks healthy, blocks
  // nothing" state the threat model rates as worse than being switched off —
  // and it is exactly what this extension did until the updater was fixed to
  // actually fetch and apply the rules artifact.
  const rules = state.rulesApplied || 0;
  document.getElementById("ver").textContent =
    rules ? `v${state.listVersion} · ${rules} rules` : "bundled only";

  if (state.failClosed) {
    const b = document.getElementById("banner");
    b.hidden = false;
    b.className = "banner";
    b.textContent =
      "The Hisn app stopped responding during an active lock, so filtering " +
      "tightened to strict mode automatically. Reopen the app to restore it.";
  }

  // Say which configuration this is. "Not locked" reads as "not protected"
  // unless the page also says that baseline blocking is on regardless — and
  // on a browser-only install there is no app to go and look at, so pointing
  // the user at one would be a dead end.
  document.getElementById("foot").textContent = locked
    ? "Settings are frozen until the lock ends."
    : state.appPresent
      ? "Blocking is on. Start a lock from the Hisn app to make it permanent."
      : "Blocking is on. Install the Hisn app to add a lock and to cover every "
        + "browser on this Mac, not just this one.";
}

/**
 * Ask the user to enable the extension in private windows.
 *
 * The manifest is `incognito: spanning`, which means the extension CAN run in a
 * private window — but Chrome keeps it off there until the user turns on "Allow
 * in Incognito/Private". That opt-in is deliberate (it is the browser's own
 * consent gate), so the honest thing is not to pretend it happened: this
 * detects when the extension has NOT been allowed in private windows and says
 * so, rather than letting page-text scanning silently not run there.
 *
 * If the profile has removed private browsing entirely (the other answer to the
 * incognito question), there is no private window to allow into and this is
 * simply never relevant — `isAllowedIncognitoAccess` reports the real setting.
 */
function checkIncognitoAccess() {
  if (!chrome.extension?.isAllowedIncognitoAccess) return;
  chrome.extension.isAllowedIncognitoAccess((allowed) => {
    if (allowed) return;
    const el = document.getElementById("incognito");
    if (!el) return;
    el.hidden = false;
    el.textContent =
      "Not active in private windows yet. To block there too, open this "
      + "browser's Extensions page and turn on “Allow in Incognito” (or "
      + "“Allow in Private”) for Hisn.";
  });
}

chrome.runtime.sendMessage({ type: "getState" }, (state) => {
  render(state || {});
});
checkIncognitoAccess();
