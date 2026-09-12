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

/**
 * The false-positive corrections the user made from block pages: words they
 * disowned (`ignoreTerms`) and hosts they exempted (`textAllow`). Unlike the
 * lists above, the app never owns these, so this section stays editable even
 * when managed — and REMOVING an entry re-enables that blocking, which is a
 * tightening, so `guardedUpdate` allows it even during a lock. Each item mutates
 * the local `state` copy so the list can re-render without a round trip.
 */
function renderReported(state) {
  for (const key of ["ignoreTerms", "textAllow"]) {
    const box = $(key);
    box.textContent = "";
    for (const item of state[key] || []) {
      const chip = document.createElement("span");
      chip.className = "chip";
      const label = document.createElement("span");
      label.textContent = item;                 // textContent, never innerHTML
      const x = document.createElement("button");
      x.type = "button";
      x.textContent = "×";                  // ×
      x.title = "Start blocking this again";
      x.setAttribute("aria-label", `Remove ${item}`);
      x.onclick = async () => {
        const next = (state[key] || []).filter((v) => v !== item);
        const r = await send({ type: "update", patch: { [key]: next } });
        if (r.ok) {
          state[key] = next;
          renderReported(state);
          $("msgReported").textContent = "";
        } else {
          $("msgReported").textContent = `Could not remove: ${r.reason}`;
        }
      };
      chip.append(label, x);
      box.appendChild(chip);
    }
  }
}

/**
 * Reports made while a lock was running. They could not loosen the lock then,
 * so they were parked in `disputed`. Here the user can APPLY one (move it into
 * the real list — allowed only once the lock has ended, since applying is a
 * loosening) or DISMISS it (drop it, which loosens nothing and works any time).
 * Hidden entirely when the queue is empty, which is the normal case.
 */
function renderDisputed(state) {
  const fs = $("disputedFs");
  const list = state.disputed || [];
  if (!list.length) { fs.hidden = true; return; }

  const locked = (state.lockUntil || 0) > Date.now();
  fs.hidden = false;
  $("disputedHint").textContent = locked
    ? "Reports you made while the lock is running. They cannot loosen a lock, "
      + "so they wait here until it ends."
    : "Your lock has ended. Apply a report to make it take effect, or dismiss it.";

  const box = $("disputed");
  box.textContent = "";
  for (const entry of list) {
    const value = entry.term || entry.host || "";
    if (!value) continue;
    const chip = document.createElement("span");
    chip.className = "chip";
    const label = document.createElement("span");
    label.textContent = value;                  // textContent, never innerHTML
    chip.appendChild(label);

    if (!locked) {
      const apply = document.createElement("button");
      apply.type = "button";
      apply.className = "apply";
      apply.textContent = "Apply";
      apply.onclick = () => resolveDisputed(state, entry, true);
      chip.appendChild(apply);
    }
    const x = document.createElement("button");
    x.type = "button";
    x.textContent = "×";                    // ×
    x.title = "Dismiss";
    x.setAttribute("aria-label", `Dismiss ${value}`);
    x.onclick = () => resolveDisputed(state, entry, false);
    chip.appendChild(x);

    box.appendChild(chip);
  }
}

async function resolveDisputed(state, entry, apply) {
  const payload = entry.term ? { term: entry.term, apply } : { host: entry.host, apply };
  const r = await send({ type: "resolveDisputed", entry: payload });
  if (r.ok) {
    Object.assign(state, r.state);              // worker returns the new state
    renderDisputed(state);
    renderReported(state);                      // an applied item now appears above
    $("msgDisputed").textContent = r.applied ? "Applied." : "";
  } else {
    $("msgDisputed").textContent = r.reason === "locked"
      ? "That cannot take effect until the lock ends."
      : `Could not do that: ${r.reason}`;
  }
}

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

  // The false-positive corrections — browser-local, so shown and editable in
  // both configurations, unlike the app-owned lists below.
  renderReported(state);
  renderDisputed(state);

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
