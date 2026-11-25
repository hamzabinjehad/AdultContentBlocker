// Distraction rules in the browser: what is asked, what is reported, what is done.
import { FEED_SELECTORS, MAX_SPAN_SECONDS, clock, feedCSS, hostOf, meterFlush, meterFocus, plan,
         usageHost, watched } from "../lib/distraction.js";
import en from "../lib/locale/en.js";
import ar from "../lib/locale/ar.js";

let checks = 0, failures = 0;
function check(ok, label) { checks++; if (!ok) { failures++; print(`  FAIL  ${label}`); } }

check(hostOf("https://www.YouTube.com/watch?v=1") === "youtube.com", "host is lower-cased without www");
check(hostOf("chrome://settings") === null && hostOf("file:///x") === null && hostOf("nonsense") === null,
      "only http and https pages have a host");

const watch = { all: false, hosts: ["youtube.com", "x.com"], usageHosts: ["youtube.com", "x.com", "reddit.com"] };
check(watched("youtube.com", watch) && watched("m.youtube.com", watch), "a listed host and its subdomains are asked about");
check(!watched("notyoutube.com", watch) && !watched("example.org", watch), "a look-alike or unlisted host is never asked about");
check(watched("example.org", { all: true, hosts: [] }), "an allow-only Focus asks about every page");
check(!watched("youtube.com", null), "no list yet means no questions");
check(usageHost("old.reddit.com", watch) === "reddit.com", "time is reported under the listed host");
check(usageHost("mybank.example", watch) === null, "ordinary browsing is never reported");

check(plan(null, "https://x.com/").kind === "allow" && plan({ ok: false }, "https://x.com/").kind === "allow",
      "a missing or failed reply leaves the page alone");
check(plan({ ok: true, action: "nonsense" }, "https://x.com/").kind === "allow", "an unknown action leaves the page alone");
const wait = plan({ ok: true, action: "wait", rule: "A", ruleName: "Games", seconds: 15, openMinutes: 10, opensLeft: 3,
                    own: "My evenings" }, "https://www.chess.com/play");
check(wait.kind === "moment" && wait.info.mode === "wait" && wait.info.seconds === 15 && wait.info.opensLeft === 3
      && wait.info.host === "chess.com" && wait.info.own === "My evenings", "a wait carries its count, opens and the person's words");
check(plan({ ok: true, action: "wait", seconds: 15 }, "https://x.com/").kind === "allow", "a wait with no rule id is not shown");
const block = plan({ ok: true, action: "block", rule: "B", ruleName: "Work", reason: "scheduled", until: 5000, snoozeDelay: 30 },
                   "https://reddit.com/");
check(block.kind === "moment" && block.info.mode === "closed" && block.info.reason === "scheduled"
      && block.info.until === 5000 && block.info.snoozeDelay === 30, "a block carries its reason, end and snooze delay");
check(plan({ ok: true, action: "block", rule: "B", reason: "made-up" }, "https://reddit.com/").info.reason === "blocked",
      "an unknown reason is shown as plainly closed");
check(plan({ ok: true, action: "block", rule: "B", reason: "limitReached" }, "https://reddit.com/").info.snoozeDelay === null,
      "no snooze delay means no snooze is offered");
const feed = plan({ ok: true, action: "hideFeed", rule: "C", surface: "youtubeHome" }, "https://www.youtube.com/");
check(feed.kind === "hideFeed" && feed.surface === "youtubeHome", "a feed surface is hidden in place");
check(plan({ ok: true, action: "hideFeed", rule: "C", surface: "unknownSurface", reason: "blocked" }, "https://a.b/").kind === "moment",
      "a surface this build cannot hide closes the page instead of doing nothing");

const css = feedCSS("youtubeHome", 'Feed "hidden"\nnow');
check(css.includes("display: none !important") && css.includes("ytd-browse"), "the feed sheet hides the surface's containers");
check(!css.includes('"hidden"') && !css.includes("\nnow"), "the corner label cannot break out of its string");
check(feedCSS("nope", "x") === null, "no sheet for an unknown surface");
for (const selectors of Object.values(FEED_SELECTORS)) check(selectors.length > 0, "every hideable surface has selectors");

let meter = meterFocus(null, "youtube.com", 0);
meter = meterFocus(meter, "x.com", 90_000);
meter = meterFocus(meter, null, 100_500);
check(Math.round(meter.totals["youtube.com"]) === 90 && Math.round(meter.totals["x.com"]) === 11, "time goes to the host in front");
meter = meterFocus(meter, "youtube.com", 200_000);
let flushed = meterFlush(meter, 200_400);
check(flushed.reports.find((r) => r.host === "youtube.com").seconds === 90, "less than a second more is not reported yet");
check(flushed.meter.host === "youtube.com" && flushed.meter.since === 200_400, "the running span carries on after a flush");
check(!("x.com" in flushed.meter.totals), "reported time is cleared");
flushed = meterFlush(meterFocus(null, "x.com", 0), 5 * 3600 * 1000);
check(flushed.reports[0].seconds === MAX_SPAN_SECONDS, "a sleeping Mac is cut to the longest real session");
check(meterFlush(null, 1000).reports.length === 0, "nothing in front reports nothing");

check(clock(65) === "1:05" && clock(0.2) === "0:01" && clock(-4) === "0:00", "the page clock rounds up and never goes negative");

const momentKeys = Object.keys(en).filter((key) => key.startsWith("moment."));
check(momentKeys.length >= 20 && momentKeys.every((key) => typeof ar[key] === "string" && ar[key].length > 0),
      "every moment phrase has Arabic");
for (const reason of ["focus", "limitReached", "noOpensLeft", "scheduled", "blocked"]) {
  check(typeof en[`moment.closed.${reason}`] === "string", `the closed page has words for ${reason}`);
}

print(`  ${checks - failures}/${checks} distraction checks passed`);
if (failures) throw new Error(`${failures} distraction check(s) failed`);
