/**
 * Options page.
 *
 * The asymmetry here is the whole point: while a lock is active you may
 * ADD blocks but never remove them, and you may SHRINK the allowlist but never
 * grow it. The service worker enforces this in guardedUpdate(); this page just
 * reflects it, because a UI-only guard is no guard at all.
 */

const parse = (text) =>
  [...new Set(
    text.split(/\r?\n/)
        .map((l) => l.trim().toLowerCase().replace(/^https?:\/\//, "").replace(/\/.*$/, ""))
        .filter((l) => l && /^[a-z0-9.-]+\.[a-z]{2,}$/.test(l))
  )];

const send = (msg) => new Promise((res) => chrome.runtime.sendMessage(msg, res));

async function init() {
  const state = await send({ type: "getState" });
  const locked = (state.lockUntil || 0) > Date.now();

  document.getElementById("custom").value = (state.customBlocks || []).join("\n");
  document.getElementById("allow").value = (state.allowlist || []).join("\n");

  document.getElementById("saveCustom").onclick = async () => {
    const domains = parse(document.getElementById("custom").value);
    const merged = locked
      ? [...new Set([...(state.customBlocks || []), ...domains])]  // add-only
      : domains;
    const r = await send({ type: "update", patch: { customBlocks: merged } });
    document.getElementById("msgCustom").textContent = r.ok
      ? `Saved ${merged.length} domains.${locked ? " Removals are frozen until the lock ends." : ""}`
      : `Blocked: ${r.reason}`;
  };

  document.getElementById("saveAllow").onclick = async () => {
    const domains = parse(document.getElementById("allow").value);
    const r = await send({ type: "update", patch: { allowlist: domains } });
    document.getElementById("msgAllow").textContent = r.ok
      ? `Saved ${domains.length} domains.`
      : "You cannot add sites to the allowlist while a lock is running.";
  };
}

init();
