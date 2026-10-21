/** Local evidence cannot be cancelled by unrelated context elsewhere. */
import { buildIndex, scorePage, withoutTerms } from "../lib/score.js";
let checks = 0, failures = 0;
function check(ok, label) { checks++; if (!ok) { failures++; print(`  FAIL ${label}`); } }
const index = buildIndex(JSON.parse(readFile("../seed/terms.json")));
const padding = ("public health breast screening contraception clinical trial ").repeat(6);
const gap = "ordinary ".repeat(40);
for (const text of ["Watch porn and hentai now", "Download porno and pornography now",
  "Watch p0rn and hent41 now", "Watch p.o.r.n and hentai now", "شاهد سكس و اباحي الان"]) {
  for (const zone of ["body", "alt"]) {
    const r = scorePage({ [zone]: padding + gap + text + gap + padding }, index, 50);
    check(r.block, `distant negative padding cannot cancel a promotional cluster in ${zone}: ${text}`);
    check(r.localEvidence?.score >= r.threshold && r.localEvidence?.zone === zone,
      "a rescued local verdict has bounded numeric evidence, not a misleading global score");
    check(r.score < r.threshold, "fixture genuinely defeats the previous whole-zone score");
  }
}
for (const text of [
  "Watch porn and hentai: public health sexual health education",
  "Public health sexual health education: watch porn and hentai",
  "Research definitions include porn and hentai", // no promotional wording
  "Watch porn porn porn", // repeated single term is not diverse evidence
  "Watch porn " + gap + "hentai", // evidence is not nearby
]) {
  const r = scorePage({ body: padding + gap + text + gap + padding }, index);
  check(!r.block && !r.localEvidence, "nearby context and diversity/promotion/window guards remain effective");
}
check(!scorePage({ body: padding + gap + "Watch porn and hentai" }, withoutTerms(index, ["hentai"])).block,
  "an ignored term cannot provide local diversity");
check(!scorePage({ body: padding + gap + "Watch porn", alt: "hentai" }, index).block,
  "local evidence cannot combine unrelated body and caption zones");
check(!scorePage({ title: "Watch porn and hentai", body: padding }, index).localEvidence,
  "the new contextual lane is limited to body/captions, not invented metadata evidence");

for (const tag of ["#FreePorn", "#WATCHP0RN", "#FreeHent41", "#watchpоrn"])
  check(scorePage({ body: "Latest " + tag }, index).block, `reviewed promotional hashtag: ${tag}`);
for (const tag of ["#Freeport", "#FreePornResearch", "#WatchpornographyResearch", "freeporn", "name#FreePorn"])
  check(!scorePage({ body: tag }, index).block, `whole hashtag boundaries: ${tag}`);
check(!scorePage({ body: "#FreeP0rn" }, withoutTerms(index, ["porn"])).block,
  "a promotional hashtag does not restore an ignored canonical word");
check(scorePage({ body: "#FreePorn #WATCHP0RN p0rn #FreePorn" }, index).hits.get("porn") === 3,
  "hashtags and other spellings share the canonical occurrence cap");

const r = scorePage({ body: padding + gap + "Watch porn and hentai now" }, index);
check(!r.localEvidence || Object.keys(r.localEvidence).sort().join(",") === "score,terms,words,zone",
  "local evidence contains no raw text, URLs or persistent page identifiers");
print(`  ${checks - failures}/${checks} local text context checks passed`);
if (failures) throw new Error(`${failures} check(s) failed`);
