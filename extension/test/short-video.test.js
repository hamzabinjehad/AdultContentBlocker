// Short-video blocking: the in-page matcher and the network rules must agree,
// and neither may reach ordinary videos, profiles or posts.
load("../content/short-video.js");
const { isShortVideo, BLOCKED_PATHS } = globalThis.HisnShortVideo;
const rules = JSON.parse(readFile("../rules/short_video.json"));
let checks = 0, failures = 0;
function check(ok, label) { checks++; if (!ok) { failures++; print(`  FAIL  ${label}`); } }

const blocked = [
  "https://www.youtube.com/shorts/abc123", "https://m.youtube.com/shorts/abc123?feature=share",
  "https://youtube.com/shorts", "https://www.instagram.com/reels/", "https://www.instagram.com/reel/Cx1/",
  "https://www.instagram.com/explore/", "https://www.facebook.com/reel/123", "https://m.facebook.com/watch/reels/",
  "https://www.snapchat.com/spotlight/xyz", "https://WWW.YOUTUBE.COM/Shorts/abc",
];
const allowed = [
  "https://www.youtube.com/watch?v=abc", "https://www.youtube.com/", "https://www.youtube.com/@channel/videos",
  "https://www.youtube.com/results?search_query=shorts", "https://www.youtube.com/shortsfilm",
  "https://www.instagram.com/someone/", "https://www.instagram.com/p/Cx1/", "https://www.instagram.com/explorer_name/",
  "https://www.facebook.com/reelsmith", "https://www.facebook.com/groups/1", "https://notyoutube.com/shorts/x",
  "https://youtube.com.evil.example/shorts/x", "https://www.snapchat.com/add/someone",
];
for (const url of blocked) check(isShortVideo(url), `in-page blocks ${url}`);
for (const url of allowed) check(!isShortVideo(url), `in-page allows ${url}`);

// Every regex rule must agree with the in-page matcher on the same addresses,
// so a full page load and an in-app tap give the same answer.
const regexRules = rules.filter((rule) => rule.condition.regexFilter);
const ruleBlocks = (url) => regexRules.some((rule) => new RegExp(rule.condition.regexFilter, "i").test(url));
for (const url of [...blocked, ...allowed]) check(ruleBlocks(url) === isShortVideo(url), `rules and script agree on ${url}`);
check(regexRules.length === BLOCKED_PATHS.length, "one network rule per in-page site");

for (const rule of rules) {
  check(rule.priority === 2000, `rule ${rule.id} outranks the allowlist, like web protection`);
  check(rule.action.redirect?.extensionPath === "/blocked.html?reason=shortvideo", `rule ${rule.id} explains itself`);
  check(!rule.condition.resourceTypes.some((type) => !["main_frame", "sub_frame"].includes(type)),
        `rule ${rule.id} blocks pages, not a site's images or scripts`);
}
check(rules.some((rule) => JSON.stringify(rule.condition.requestDomains) === '["tiktok.com"]'), "TikTok is blocked as a whole");
const manifest = JSON.parse(readFile("../manifest.json"));
const entry = manifest.declarative_net_request.rule_resources.find((r) => r.id === "short_video");
check(entry?.enabled === false && entry.path === "rules/short_video.json", "the ruleset ships off and is turned on by choice");

print(`  ${checks - failures}/${checks} checks passed`);
if (failures) throw new Error(`${failures} short-video checks failed`);
