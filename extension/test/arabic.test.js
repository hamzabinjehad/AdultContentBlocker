/**
 * The generated Arabic tier.
 *
 * `gen_terms_ar.py` expands a few hundred curated dialect words into ~5,500 of
 * the ~5,800 terms in the compiled list. That size is only safe while the
 * expansion cannot reach something innocent, so almost every case here is a
 * page that must stay READABLE. The two that block are here to prove the list
 * still works at all after the negatives were added.
 *
 * Two of these are regressions from real failures, both found by scoring a page
 * rather than by any unit check:
 *
 *   `الزب`   a two-letter-stem expansion emitted it, and `الزبون` (the
 *            customer) strips its `ون` suffix down to exactly that — every page
 *            with a support section scored as pornography.
 *
 *   `زنا`    carried weight 6.0. A four-line islamqa fatwa titled `ما حكم
 *            الزنا؟` scored 77 against a threshold of 7.5, on its title alone.
 *
 * The SHORT cases matter more than the long ones. A long article accumulates
 * enough of the fiqh or medical register to cancel itself; a four-line fatwa
 * carries almost nothing but its own subject, which is exactly when a
 * too-confident weight shows up.
 */

import { buildIndex, scorePage } from "../lib/score.js";

let failures = 0, checks = 0;
function check(ok, label) {
  checks++;
  if (!ok) { failures++; print(`  FAIL  ${label}`); }
}

const index = buildIndex(JSON.parse(readFile("../seed/terms.json")));

function verdict(zones) {
  const r = scorePage(zones, index, 50);
  return `score ${r.score.toFixed(0)}, density ${r.density.toFixed(0)}, ` +
         `threshold ${r.threshold}`;
}
function mustBlock(name, zones) {
  const r = scorePage(zones, index, 50);
  check(r.block, `${name} must block (${verdict(zones)})`);
}
function mustNotBlock(name, zones) {
  const r = scorePage(zones, index, 50);
  check(!r.block, `${name} must stay readable (${verdict(zones)})`);
}

// ── must block ─────────────────────────────────────────────────────────────

mustBlock("an Arabic tube-site landing page", {
  url: "aflam-sks-arab.com/category/nik",
  title: "أفلام سكس عربي مترجم - مقاطع نيك ساخنة",
  heading: "أحدث أفلام سكس مصري ومقاطع إباحية",
  body: "موقع أفلام سكس عربي يقدم أحدث مقاطع النيك والفيديوهات الإباحية " +
        "بجودة عالية. شاهد سكس مصري وسكس خليجي وقصص محارم مترجمة. " +
        "أفلام إباحية جديدة يوميا ومقاطع شرموطة وقحبة عربية.",
});

// The tier that matters most for this audience: an Arabic-script-only list
// matches almost no hostnames, because Arabic adult sites are ASCII.
mustBlock("a Franco-Arabic porn page", {
  url: "sks-arab.net/aflam-sks-masry",
  title: "aflam sks arab - neek masry hd",
  heading: "sks arabi jadid",
  body: "aflam sks arab motarjam. shof neek masry w sks khaliji hd. " +
        "shrmota arabi w qahba lebnani. sks arabi mojani kol yom.",
});

// ── must NOT block: Islamic scholarship ────────────────────────────────────
//
// نكاح is the marriage contract. زنا, لواط, سحاق, عورة, جنابة and حيض are core
// fiqh, appearing in every tafsir, hadith collection and fatwa archive. A
// filter that scores them as pornography does not block pornography — it
// blocks scholarship, for exactly the audience a tool called حصن is built for.

mustNotBlock("a fatwa on the rules of marriage", {
  url: "islamweb.net/ar/fatwa/ahkam-alnikah",
  title: "أحكام النكاح في الإسلام - فتوى",
  heading: "شروط عقد النكاح والولي والشهود",
  body: "الحمد لله والصلاة والسلام على رسول الله. أحكام النكاح في الشريعة " +
        "الإسلامية تقوم على عقد النكاح بين الزوجين بشروط الولي والشهود " +
        "والمهر. وقد بين الفقهاء في المذهب الحنفي والمالكي والشافعي " +
        "والحنبلي شروط النكاح الصحيح. وأما حد الزنا فقد ورد في القرآن " +
        "والسنة، وذكر أهل التفسير في تفسير الآية أحكامه. وستر العورة " +
        "واجب، والطهارة من الجنابة والحيض شرط للصلاة. والله أعلم.",
});

// REGRESSION: this is the one that was actually broken.
mustNotBlock("a four-line fatwa on the ruling of zina", {
  url: "islamqa.info/ar/answers/1234",
  title: "ما حكم الزنا؟",
  heading: "حكم الزنا",
  body: "السؤال: ما حكم الزنا في الإسلام؟ الجواب: الزنا من كبائر الذنوب، " +
        "وقد حرمه الله تعالى. والله أعلم.",
});

mustNotBlock("a short fiqh answer on covering the awrah", {
  url: "islamweb.net/ar/fatwa/5678",
  title: "حكم ستر العورة في الصلاة",
  heading: "ستر العورة",
  body: "ستر العورة شرط من شروط صحة الصلاة عند جمهور الفقهاء. والله أعلم.",
});

// ── must NOT block: medicine, law, ordinary business ───────────────────────

mustNotBlock("a ministry-of-health reproductive health page", {
  url: "moh.gov.sa/awareness/reproductive-health",
  title: "الصحة الإنجابية - وزارة الصحة",
  heading: "التوعية بالصحة الإنجابية وطب النساء",
  body: "تهتم وزارة الصحة بالصحة الإنجابية وطب النساء وأمراض النساء. يشمل " +
        "ذلك متابعة الحمل والولادة والرضاعة وفحص الثدي للكشف عن سرطان " +
        "الثدي. كما تشمل التوعية بالدورة الشهرية والهرمونات والغدد ومرحلة " +
        "البلوغ والمراهقة. منظمة الصحة العالمية توصي بالتثقيف الصحي.",
});

mustNotBlock("a news report on a trafficking prosecution", {
  url: "news.example.com/law/trafficking-case",
  title: "المحكمة تصدر حكما في قضية الاتجار بالبشر",
  heading: "النيابة العامة ومكافحة الاتجار بالبشر",
  body: "أصدرت المحكمة حكما وفق القانون في قضية الاتجار بالبشر. وقالت " +
        "النيابة إن المادة من قانون العقوبات تنص على العقوبة. وأكدت الجهات " +
        "المعنية أهمية حماية الطفل والإبلاغ عن الحالات ومكافحة هذه الجرائم.",
});

// `جنس` is reachable from `الجنسية`, which is why the root carries a low
// weight and the nationality family is in negatives.tsv.
mustNotBlock("a passport and nationality page", {
  url: "gov.example/passport/nationality",
  title: "قانون الجنسية وتجديد جواز السفر",
  heading: "شروط منح الجنسية",
  body: "يوضح قانون الجنسية شروط منح الجنسية وسحبها وحالات ازدواج الجنسية " +
        "وعديم الجنسية. كما تشرح الصفحة إجراءات تجديد جواز السفر " +
        "والتأشيرات لمتعددي الجنسيات.",
});

// REGRESSION: `الزبون` -> strip `ون` -> `الزب`.
mustNotBlock("a customer-service page", {
  url: "shop.example/support",
  title: "خدمة الزبون",
  heading: "الزبون دائما على حق",
  body: "يسعدنا خدمة الزبون والرد على الزبائن. الزبون يستطيع التواصل معنا. " +
        "نبيع الزبدة والزبيب والمكسرات، وفي حال كسر أي منتج يرجى إبلاغنا.",
});

// ── the vocabulary itself ──────────────────────────────────────────────────

check(index.positive.size >= 5000,
      `the compiled list should carry the expanded Arabic tier ` +
      `(${index.positive.size} positive terms)`);

// Named directly rather than via a page: if any of these ever becomes a
// positive term, every page above starts depending on luck.
for (const fiqh of ["نكاح", "جنابة", "حيض", "طهاره", "محرم"]) {
  check(!index.positive.has(fiqh),
        `${fiqh} is fiqh vocabulary and must never be a positive term`);
}

print(`  ${checks - failures}/${checks} checks passed`);
if (failures) throw new Error(`${failures} failed`);
