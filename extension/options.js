/**
 * Options page.
 *
 * ── TWO CONFIGURATIONS, AND THE PAGE HAS TO BE HONEST ABOUT WHICH ─────────
 * This extension is legitimately run two ways, and the settings below mean
 * different things in each:
 *
 *   standalone   — browser-only. This page IS the authoring surface. What is
 *                  typed here is what is enforced, and it persists.
 *   with the app — the Hisn app owns the lists and overwrites this side on
 *                  every heartbeat, so anything typed here survives at most a
 *                  minute. The fields go read-only and say so.
 *
 * Silently accepting edits that a background sync is about to discard is the
 * kind of small dishonesty that makes people distrust the whole tool, so the
 * page reads `appPresent` and changes its mind about what it is.
 *
 * ── THE ASYMMETRY, IN BOTH CONFIGURATIONS ────────────────────────────────
 * While a lock is active you may ADD blocks but never remove them, and you may
 * SHRINK the allowlist but never grow it. `guardedUpdate` in the service worker
 * enforces that; this page only reflects it, because a UI-only guard is no
 * guard at all — anything here can be posted from a devtools console.
 */

const parse = (text) =>
  [...new Set(
    text.split(/\r?\n/)
        .map((l) => l.trim().toLowerCase().replace(/^https?:\/\//, "").replace(/\/.*$/, ""))
        .filter((l) => l && /^[a-z0-9.-]+\.[a-z]{2,}$/.test(l))
  )];

const send = (msg) => new Promise((res) => chrome.runtime.sendMessage(msg, res));
const $ = (id) => document.getElementById(id);

async function init() {
  const state = await send({ type: "getState" });
  const locked = (state.lockUntil || 0) > Date.now();
  const managed = !!state.appPresent;

  // ── the banner that tells you which product you are actually using ──────
  const banner = $("mode");
  if (managed) {
    banner.className = "mode managed";
    banner.textContent = locked
      ? "The Hisn app is managing this browser, and a lock is running. "
        + "Settings are edited in the app."
      : "The Hisn app is managing this browser. Edit these lists in the app — "
        + "changes made here are replaced within a minute.";
  } else {
    banner.className = "mode standalone";
    banner.textContent =
      "Running without the Hisn app. Blocking is active and these lists are "
      + "yours to edit. Install the app to add a lock that cannot be undone, "
      + "and to cover every browser on this Mac rather than this one.";
  }

  $("custom").value = (state.customBlocks || []).join("\n");
  $("allow").value = (state.allowlist || []).join("\n");
  $("inspectText").checked = state.inspectText !== false;
  $("textSensitivity").value = state.textSensitivity ?? 50;
  $("sensitivityValue").textContent = state.textSensitivity ?? 50;

  // Fields the app owns become read-only rather than merely futile.
  for (const id of ["custom", "allow", "inspectText", "textSensitivity"]) {
    $(id).disabled = managed;
  }
  for (const id of ["saveCustom", "saveAllow", "saveChecks"]) {
    $(id).disabled = managed;
  }

  $("textSensitivity").oninput = () => {
    $("sensitivityValue").textContent = $("textSensitivity").value;
  };

  $("saveCustom").onclick = async () => {
    const domains = parse($("custom").value);
    // Merge add-only while locked so the page never even attempts a removal —
    // the worker would refuse it and the user would lose their other edits.
    const merged = locked
      ? [...new Set([...(state.customBlocks || []), ...domains])]
      : domains;
    const r = await send({ type: "update", patch: { customBlocks: merged } });
    $("msgCustom").textContent = r.ok
      ? `Saved ${merged.length} domains.`
        + (locked ? " Removals are frozen until the lock ends." : "")
      : `Not saved: ${r.reason}`;
  };

  $("saveAllow").onclick = async () => {
    const domains = parse($("allow").value);
    const r = await send({ type: "update", patch: { allowlist: domains } });
    $("msgAllow").textContent = r.ok
      ? `Saved ${domains.length} domains.`
      : "You cannot add sites to the allowlist while a lock is running.";
  };

  $("saveChecks").onclick = async () => {
    const r = await send({ type: "update", patch: {
      inspectText: $("inspectText").checked,
      textSensitivity: Number($("textSensitivity").value),
    }});
    $("msgChecks").textContent = r.ok
      ? "Saved."
      : "You cannot switch checking off or lower it while a lock is running.";
    if (!r.ok) {
      // Put the controls back to what is actually in force, rather than
      // leaving them showing a setting that was refused.
      const fresh = await send({ type: "getState" });
      $("inspectText").checked = fresh.inspectText !== false;
      $("textSensitivity").value = fresh.textSensitivity ?? 50;
      $("sensitivityValue").textContent = fresh.textSensitivity ?? 50;
    }
  };
}

init();
