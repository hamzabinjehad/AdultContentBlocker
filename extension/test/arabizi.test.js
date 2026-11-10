// The reviewed Arabizi context must not turn the scorer into a name detector.
import { buildIndex, scorePage } from "../lib/score.js";

let failures = 0, checks = 0;
function check(ok, label) {
  checks++;
  if (!ok) { failures++; print(`  FAIL  ${label}`); }
}
const index = buildIndex(JSON.parse(readFile("../seed/terms.json")));
const verdict = (text) => scorePage({ body: text }, index, 50);
for (const text of [
  "Watch aflam neek masri free",
  "tahmil film neek arabi",
  "tahmil aflam neek 7elwa mojani",
  "sksy sharmota download",
]) check(verdict(text).block, `must block: ${text}`);
for (const text of [
  "Watch Neek Masri live, free",
  "tahmil aflam Nik Cage mojani",
  "sksy download free",
]) check(!verdict(text).block, `must stay readable: ${text}`);
print(`  ${checks}/${checks} Arabizi context checks passed`);
if (failures) throw new Error(`${failures} Arabizi context checks failed`);
