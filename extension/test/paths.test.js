// Single-site path rules (rules/paths.json): Reddit communities and profiles
// whose name carries a word the keyword list can only use on a hostname —
// "nsfw" in /r/nsfw_gifs is unambiguous; "nsfw" anywhere in any URL is not.
//
// JavaScript's RegExp stands in for Chrome's RE2 (the pattern uses only what
// the two share); test/browser/dnr.sh checks the rule in a real browser.

const rules = JSON.parse(readFile("../rules/paths.json"));
const manifest = JSON.parse(readFile("../manifest.json"));
let checks = 0;
function check(ok, label) { checks++; if (!ok) throw new Error(label); }

check(manifest.declarative_net_request.rule_resources
        .some((r) => r.id === "paths" && r.enabled && r.path === "rules/paths.json"),
      "the ruleset is declared and enabled from install");
check(rules.every((r) => r.priority === 4),
      "path rules sit on the keyword rung (4): the person's own allowlist still outranks them");
check(rules.every((r) => r.condition.isUrlFilterCaseSensitive === false), "case-insensitive, as Reddit is");

const reddit = rules[0];
const blocks = (url) => new RegExp(reddit.condition.regexFilter, "i").test(url);

for (const url of ["https://www.reddit.com/r/nsfw_gifs/", "https://old.reddit.com/r/NSFW/top",
                   "https://reddit.com/r/Boobs", "https://www.reddit.com/user/someone_nsfw",
                   "https://np.reddit.com/u/xxx_account", "https://www.reddit.com/r/gonewild30plus",
                   "https://www.reddit.com/r/lewdanime", "https://www.reddit.com/r/nudes/comments/1"]) {
  check(blocks(url), `blocks ${url}`);
}
for (const url of ["https://www.reddit.com/r/nosleep", "https://www.reddit.com/r/bluetits",
                   "https://www.reddit.com/r/AskHistorians/comments/nsfw_question",
                   "https://www.reddit.com/search?q=nsfw+policy", "https://www.notreddit.com/r/nsfw",
                   "https://example.com/r/nsfw", "https://www.reddit.com/r/Thoth"]) {
  check(!blocks(url), `leaves ${url}`);
}

print(`  ${checks}/${checks} path-rule checks passed`);
