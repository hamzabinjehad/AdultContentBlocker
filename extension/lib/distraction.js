/**
 * Distraction rules in the browser — the pure half.
 *
 * The macOS app owns the rules. The worker asks it what to do with a page and
 * tells it what the person did; this file is the part of that with no browser
 * in it, so it can be tested with JavaScriptCore alone.
 *
 * Privacy: only hosts and rule ids ever leave the browser, and a host is only
 * reported when the app listed it (a rule site, or a site it already calls
 * distracting). Ordinary browsing never crosses the channel.
 */

/** A session longer than this is a sleeping Mac, not use. Matches the app. */
export const MAX_SPAN_SECONDS = 30 * 60;

/** The registrable-looking host of an http(s) page, lower-cased, without www. */
export function hostOf(url) {
  // Parsed by hand: the worker has URL, the test shell does not, and both must agree.
  const match = /^https?:\/\/(?:[^\/?#@]*@)?(\[[^\]]+\]|[^\/?#:]+)/i.exec(String(url ?? ""));
  if (!match) return null;
  const host = match[1].toLowerCase().replace(/\.$/, "").replace(/^www\./, "");
  return host || null;
}

function matchIn(host, list) {
  if (!host || !Array.isArray(list)) return null;
  return list.find((entry) => host === entry || host.endsWith(`.${entry}`)) ?? null;
}

/** Whether the app needs to be asked about this host. */
export function watched(host, watch) {
  if (!host || !watch) return false;
  return watch.all === true || matchIn(host, watch.hosts) !== null;
}

/** The listed host to report time under, or null when time here is not reported. */
export function usageHost(host, watch) {
  return matchIn(host, watch?.usageHosts);
}

/**
 * Which feed surfaces can be hidden in place, and how. A selector a site does
 * not use simply never matches, so a moved container costs only this tidy-up.
 */
export const FEED_SELECTORS = Object.freeze({
  youtubeHome: ["ytd-browse[page-subtype='home'] ytd-rich-grid-renderer", "ytd-browse[page-subtype='home'] #contents",
                "ytm-browse ytm-rich-grid-renderer"],
  instagramFeed: ["main article", "main [role='feed']"],
  instagramExplore: ["main"],
  xForYou: ["[data-testid='primaryColumn'] section[role='region']", "[data-testid='primaryColumn'] [aria-label*='Timeline']"],
  redditHome: ["shreddit-feed", "#siteTable"],
  redditPopular: ["shreddit-feed", "#siteTable"],
  facebookFeed: ["div[role='feed']"],
  facebookWatch: ["div[role='main']"],
  linkedinFeed: ["main .scaffold-finite-scroll", "main .feed-shared-update-v2"],
});

/** The stylesheet that hides one surface and says so in a corner. */
export function feedCSS(surface, label) {
  const selectors = FEED_SELECTORS[surface];
  if (!selectors) return null;
  const text = String(label ?? "").replace(/["\\\n]/g, " ");
  return `${selectors.join(",\n")} { display: none !important; }\n`
    + `html::after { content: "${text}"; position: fixed; inset-inline-start: 16px; bottom: 16px; z-index: 2147483647;`
    + ` background: #1f1f1f; color: #fff; font: 500 13px/1 -apple-system, system-ui, sans-serif; padding: 10px 14px;`
    + ` border-radius: 99px; box-shadow: 0 6px 20px rgba(0,0,0,.35), 0 0 0 1px rgba(255,255,255,.12); pointer-events: none; }\n`;
}

const REASONS = new Set(["focus", "limitReached", "noOpensLeft", "scheduled", "blocked"]);

/**
 * What to do with a page, from the app's reply. Anything malformed is "allow":
 * a distraction rule that cannot be read must never close a page by accident.
 * (Adult protection is a different layer and fails closed on its own.)
 */
export function plan(reply, url) {
  if (!reply || reply.ok !== true) return { kind: "allow" };
  const base = { url: String(url), host: hostOf(url), rule: typeof reply.rule === "string" ? reply.rule : null,
                 ruleName: typeof reply.ruleName === "string" ? reply.ruleName.slice(0, 80) : "",
                 own: typeof reply.own === "string" ? reply.own.slice(0, 140) : "" };
  if (reply.action === "wait" && base.rule && Number.isFinite(reply.seconds)) {
    return { kind: "moment", info: { ...base, mode: "wait",
      seconds: Math.min(600, Math.max(1, Math.round(reply.seconds))),
      openMinutes: Number.isFinite(reply.openMinutes) ? Math.max(1, Math.round(reply.openMinutes)) : 10,
      opensLeft: Number.isFinite(reply.opensLeft) ? Math.max(0, Math.round(reply.opensLeft)) : null } };
  }
  if (reply.action === "hideFeed" && typeof reply.surface === "string" && FEED_SELECTORS[reply.surface]) {
    return { kind: "hideFeed", surface: reply.surface };
  }
  if (reply.action === "block" || reply.action === "hideFeed") {
    return { kind: "moment", info: { ...base, mode: "closed",
      reason: REASONS.has(reply.reason) ? reply.reason : "blocked",
      until: Number.isFinite(reply.until) ? reply.until : null,
      snoozeDelay: Number.isFinite(reply.snoozeDelay) ? Math.max(0, Math.round(reply.snoozeDelay)) : null,
      intent: typeof reply.intent === "string" ? reply.intent.slice(0, 140) : "" } };
  }
  return { kind: "allow" };
}

/**
 * Time in front, per listed host. One host at a time: the tab the person is
 * looking at. Kept as plain data so the worker can park it in session storage
 * between its short lives.
 */
export function meterFocus(meter, host, now) {
  const next = { totals: { ...(meter?.totals ?? {}) }, host: host ?? null, since: now };
  if (meter?.host && Number.isFinite(meter.since)) {
    const seconds = Math.min(MAX_SPAN_SECONDS, Math.max(0, (now - meter.since) / 1000));
    if (seconds > 0) next.totals[meter.host] = (next.totals[meter.host] ?? 0) + seconds;
  }
  return next;
}

/** Closes the running span and hands back what to report, a whole second or more per host. */
export function meterFlush(meter, now) {
  const closed = meterFocus(meter, meter?.host ?? null, now);
  const reports = [];
  const totals = {};
  for (const [host, seconds] of Object.entries(closed.totals)) {
    if (seconds >= 1) reports.push({ host, seconds: Math.min(MAX_SPAN_SECONDS, Math.floor(seconds)) });
    else totals[host] = seconds;
  }
  return { meter: { totals, host: closed.host, since: now }, reports };
}

/** "1 h 5 m", "12 m", "40 s" — for the page's own clock. */
export function clock(seconds) {
  const total = Math.max(0, Math.ceil(seconds));
  const m = Math.floor(total / 60), s = total % 60;
  return `${m}:${String(s).padStart(2, "0")}`;
}
