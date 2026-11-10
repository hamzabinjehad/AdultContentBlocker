// Social-feed whole-post suppression: the stylesheet's contract and the
// registration that delivers it. The real-DOM behaviour is in
// test/browser/social-media.html, which loads the same stylesheet.
const { SOCIAL_MEDIA_SCRIPT, socialMediaRegistrationMatches } = await import("../worker/scanners.js");
let checks = 0, failures = 0;
function check(ok, label) { checks++; if (!ok) { failures++; print(`  FAIL ${label}`); } }

const css = readFile("../content/social-media.css");
const code = css.replace(/\/\*[\s\S]*?\*\//g, "");

// ── the rule collapses a post, and nothing else ──────────────────────────
const MEDIA = 'img[data-hisn-visual="adult-hidden"], video[data-hisn-visual="adult-hidden"]';
const blocks = [...code.matchAll(/\{([^}]*)\}/g)].map((match) => match[1].trim());
check(blocks.length === 1, "the stylesheet holds one declaration block");
check(blocks[0] === "display: none !important;", "the only effect is removing the post, with no blur or filter");
const hasCount = (code.match(/:has\(/g) || []).length;
check(hasCount >= 10, "the rule covers every container through :has()");
check((code.match(new RegExp(`:has\\(${MEDIA.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}\\)`, "g")) || []).length === hasCount,
  "every :has() keys on the local adult-media marker and nothing else");
check(!code.includes("person-hidden"), "a person-blur marker is not treated as adult content");
check(!/sensitive|nsfw/i.test(code), "a platform's own sensitive/NSFW flag is never a selector");
check(!/@import|url\(|expression\(|-moz-binding/i.test(code), "the stylesheet loads nothing and executes nothing");
check(!/\b(filter|opacity|visibility|position)\s*:/i.test(code), "a flagged post is removed, not merely obscured");

// Every platform's container is covered. The names describe current markup;
// a site that renames its post element loses only this tidy-up.
for (const container of [
  "article:has(", "div[role=\"article\"]:has(",
  "shreddit-post:has(", "div[data-testid=\"post-container\"]:has(", "div.thing:has(",
  "div[data-e2e=\"recommend-list-item-container\"]:has(",
  "div[data-testid^=\"feedItem-\"]:has(",
  "div.feed-shared-update-v2:has(",
  "div[data-testid=\"pin\"]:has(", "div[role=\"listitem\"]:has(",
]) check(code.includes(container), `the stylesheet covers ${container.replace(/:has\($/, "")}`);
check(!/\b(x\.com|twitter\.com)\b/.test(code), "X stays with the granular feed adapter, which adds an explanation");

// ── registration ─────────────────────────────────────────────────────────
check(SOCIAL_MEDIA_SCRIPT.id === "hisn-social-media", "the registration keeps its stable id");
check((SOCIAL_MEDIA_SCRIPT.js || []).length === 0, "the registration injects no page script");
check(JSON.stringify(SOCIAL_MEDIA_SCRIPT.css) === JSON.stringify(["content/social-media.css"]),
  "the registration injects exactly the one stylesheet");
const expectedMatches = [
  "*://*.instagram.com/*", "*://*.facebook.com/*", "*://*.reddit.com/*",
  "*://*.tiktok.com/*", "*://*.threads.net/*", "*://*.threads.com/*",
  "*://bsky.app/*", "*://*.linkedin.com/*", "*://*.pinterest.com/*",
  "*://*.tumblr.com/*", "*://*.snapchat.com/*",
];
check(JSON.stringify([...SOCIAL_MEDIA_SCRIPT.matches].sort()) === JSON.stringify([...expectedMatches].sort()),
  "the registration covers exactly the supported social platforms");
check(!SOCIAL_MEDIA_SCRIPT.matches.some((pattern) => /(x|twitter|youtube)\.com/.test(pattern)),
  "X/Twitter and YouTube are not claimed by this rule");
check(SOCIAL_MEDIA_SCRIPT.matches.every((pattern) => /^\*:\/\/(\*\.)?[^/]+\/\*$/.test(pattern)),
  "every match pattern has the supported host form");
const scanners = readFile("../worker/scanners.js");
check(scanners.includes('"hisn-social-media"') && scanners.includes('"content/social-media.css"'),
  "the worker registration names the stylesheet");

const valid = { id: "hisn-social-media", css: [...SOCIAL_MEDIA_SCRIPT.css],
  matches: [...SOCIAL_MEDIA_SCRIPT.matches], runAt: "document_start",
  allFrames: false, persistAcrossSessions: true };
check(socialMediaRegistrationMatches(valid), "the expected registration is accepted");
check(!socialMediaRegistrationMatches({ ...valid, id: "other" }), "a foreign id is not accepted");
check(!socialMediaRegistrationMatches({ ...valid, js: ["content/other.js"] }),
  "a registration that runs page script is repaired");
check(!socialMediaRegistrationMatches({ ...valid, css: ["content/other.css"] }),
  "a registration with the wrong stylesheet is repaired");
check(!socialMediaRegistrationMatches({ ...valid, matches: valid.matches.slice(1) }),
  "a registration missing a platform is repaired");
check(!socialMediaRegistrationMatches({ ...valid, matches: [...valid.matches, "*://*.example.com/*"] }),
  "a registration covering an extra host is repaired");
check(!socialMediaRegistrationMatches({ ...valid, allFrames: true }),
  "injection into subframes is repaired");
check(!socialMediaRegistrationMatches({ ...valid, persistAcrossSessions: false }),
  "a registration that dies with the worker is repaired");
check(!socialMediaRegistrationMatches({ ...valid, runAt: "document_idle" }),
  "a late registration is repaired");
check(!socialMediaRegistrationMatches({ ...valid, excludeMatches: ["*://*.instagram.com/*"] }),
  "a registration that silently excludes a platform is repaired");
check(!socialMediaRegistrationMatches({ ...valid, includeGlobs: ["*feed*"] }),
  "a registration narrowed by a glob is repaired");

print(`  ${checks - failures}/${checks} social-media checks passed`);
if (failures) throw new Error(`${failures} social-media checks failed`);
