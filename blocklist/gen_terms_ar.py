#!/usr/bin/env python3
"""
Generate the bulk Arabic term tables.

WHY A GENERATOR AND NOT A HAND-WRITTEN LIST
-------------------------------------------
Arabic adult content is not written in one language. It is written in Egyptian,
Levantine, Gulf, Iraqi, Maghrebi, Sudanese and Yemeni — which share a root
system but very little explicit vocabulary — and it is written twice, once in
Arabic script and once in Franco-Arabic (`ni7`, `sks`, `2ohba`). A hand list
covers whichever dialect its author speaks and silently misses the rest.

So the vocabulary is curated by hand, per dialect, and the *spellings* are
generated. That split matters: the curated half is a judgement about what a
word means, which a machine cannot make, and the generated half is a mechanical
enumeration of how people type it, which a human always does incompletely.

WHAT THIS DELIBERATELY DOES NOT DO
----------------------------------
It does not pad. `terms.token_variants` already strips one bounded prefix and
one suffix at match time, so emitting `الكس` next to `كس` adds a row that can
never fire — a bigger number and identical coverage.

Expanding the affixes of two-letter stems looked like the exception, since the
stripper needs 3+ characters to remain and so never reaches `كس` inside `الكس`
at all. It was tried and reverted: the affixed forms of a two-letter stem are
also what innocent longer words strip down to, and `الزبون` (the customer)
lands on `الزب`. `short_stem_affixes` documents that in full.

Run:
    python3 gen_terms_ar.py            # writes the two generated tables
    python3 gen_terms_ar.py --check    # verify only, no writes (used by tests)
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from terms import normalize, token_variants  # noqa: E402

ROOT = Path(__file__).parent
TERMS_DIR = ROOT / "terms"

# Weight bands, same meaning as terms.en.tsv documents:
#   8 unambiguous · 6 strong · 4 moderate · 2 tie-breaker only
UNAMBIGUOUS, STRONG, MODERATE, WEAK = 8.0, 6.0, 4.0, 2.0


# --------------------------------------------------------------------------- #
# The curated core, by dialect
# --------------------------------------------------------------------------- #
#
# Only words whose *primary* everyday sense is explicit belong at 8.0. A word
# that is also fiqh, medicine or law lives at 4.0 or lower, or does not appear
# here at all and is left to the phrase list — see FIQH_REGISTER below for why
# that line is drawn where it is.

MSA = {
    # Straightforwardly explicit in every register.
    "إباحية": UNAMBIGUOUS, "إباحي": UNAMBIGUOUS, "الإباحية": UNAMBIGUOUS,
    "اباحيه": UNAMBIGUOUS, "اباحيات": UNAMBIGUOUS,
    "خلاعة": UNAMBIGUOUS, "خلاعي": UNAMBIGUOUS, "خليع": STRONG,
    "خليعة": STRONG, "ماجن": MODERATE, "مجون": MODERATE,
    "فاضح": STRONG, "فاضحة": STRONG, "فاحشة": MODERATE, "فواحش": MODERATE,
    "عاري": STRONG, "عارية": STRONG, "عريان": STRONG, "عريانة": STRONG,
    "عاريات": UNAMBIGUOUS, "عريانات": UNAMBIGUOUS,
    "تعري": STRONG, "التعري": STRONG, "متعرية": UNAMBIGUOUS,
    "شهواني": STRONG, "شهوانية": STRONG, "شبق": STRONG, "شبقة": STRONG,
    "دعارة": UNAMBIGUOUS, "الدعارة": UNAMBIGUOUS, "بغاء": STRONG,
    "مومس": STRONG, "مومسات": UNAMBIGUOUS,
    "عاهرة": UNAMBIGUOUS, "عاهرات": UNAMBIGUOUS, "عهر": STRONG,
    "ساقطة": MODERATE, "فاجرة": MODERATE, "منحلة": WEAK,
    "مضاجعة": STRONG, "يضاجع": STRONG, "تضاجع": STRONG,
    "مواقعة": MODERATE, "معاشرة": WEAK,
    "إثارة جنسية": STRONG, "نشوة": MODERATE, "لذة جنسية": STRONG,
    "شذوذ": MODERATE, "شاذ جنسيا": STRONG,
}

EGYPTIAN = {
    "سكس": UNAMBIGUOUS, "سكسي": UNAMBIGUOUS, "سكسية": UNAMBIGUOUS,
    "سيكس": UNAMBIGUOUS, "سكساوي": UNAMBIGUOUS,
    "نيك": UNAMBIGUOUS, "نيج": UNAMBIGUOUS, "نياكة": UNAMBIGUOUS,
    "ينيك": UNAMBIGUOUS, "تنيك": UNAMBIGUOUS, "ينيكها": UNAMBIGUOUS,
    "بينيك": UNAMBIGUOUS, "اتناكت": UNAMBIGUOUS, "يتناك": UNAMBIGUOUS,
    "متناك": UNAMBIGUOUS, "متناكة": UNAMBIGUOUS, "متناكين": UNAMBIGUOUS,
    "منيوك": UNAMBIGUOUS, "منيوكة": UNAMBIGUOUS, "منايك": UNAMBIGUOUS,
    "شرموطة": UNAMBIGUOUS, "شراميط": UNAMBIGUOUS, "شرموط": UNAMBIGUOUS,
    "شرمطة": UNAMBIGUOUS, "متشرمطة": UNAMBIGUOUS,
    "لبوة": STRONG, "قحبة": UNAMBIGUOUS, "قحاب": UNAMBIGUOUS,
    "خول": STRONG, "خولات": STRONG, "مخنث": MODERATE,
    "طيز": UNAMBIGUOUS, "طيزها": UNAMBIGUOUS, "طياز": UNAMBIGUOUS,
    "بزاز": UNAMBIGUOUS, "بزازها": UNAMBIGUOUS, "بز": MODERATE,
    "زب": MODERATE, "زبر": STRONG, "زبره": STRONG,
    "كس": MODERATE, "كسها": UNAMBIGUOUS, "كوس": MODERATE,
    "بزها": UNAMBIGUOUS, "مؤخرتها": STRONG, "نهودها": STRONG,
    "حلماتها": STRONG, "صدرها العاري": UNAMBIGUOUS,
    "يمص": MODERATE, "تمص": MODERATE, "مص": WEAK, "مصاصة": WEAK,
    "شاذ": MODERATE, "شواذ": MODERATE, "ديوث": STRONG, "قواد": MODERATE,
    "يقذف": MODERATE, "قذف": WEAK, "منوي": WEAK,
}

LEVANTINE = {
    "شرموطه": UNAMBIGUOUS, "شراميت": UNAMBIGUOUS,
    "عرص": STRONG, "معرص": STRONG, "معرصين": STRONG,
    "زانية": STRONG, "كسك": UNAMBIGUOUS, "كسمك": UNAMBIGUOUS,
    "طيزك": UNAMBIGUOUS, "منيوكه": UNAMBIGUOUS, "منيوكين": UNAMBIGUOUS,
    "عير": MODERATE, "عيري": STRONG, "أير": MODERATE, "ايره": STRONG,
    "لحس": WEAK, "يلحس": MODERATE, "تلحس": MODERATE,
    "مثيرة": MODERATE, "شحاطة": WEAK, "بغي": MODERATE,
    "منيك": UNAMBIGUOUS, "نيكني": UNAMBIGUOUS, "نيكها": UNAMBIGUOUS,
}

GULF = {
    "قحبه": UNAMBIGUOUS, "قحبات": UNAMBIGUOUS, "قحبان": UNAMBIGUOUS,
    "زبي": UNAMBIGUOUS, "زبك": UNAMBIGUOUS, "كسي": UNAMBIGUOUS,
    "لوطي": MODERATE, "لوطية": MODERATE, "سحاقية": MODERATE,
    "سحاقيات": STRONG, "بنات ليل": STRONG, "خرابيط": WEAK,
    "طقاق": WEAK, "مبادلة": WEAK, "تبادل ازواج": STRONG,
    "شمشون": WEAK, "دبه": WEAK,
}

IRAQI = {
    "كحبة": UNAMBIGUOUS, "كحبه": UNAMBIGUOUS, "كحاب": UNAMBIGUOUS,
    "لوطية": MODERATE, "عكروت": MODERATE, "جحش": WEAK,
    "منيوج": UNAMBIGUOUS, "نيج": UNAMBIGUOUS, "كسج": UNAMBIGUOUS,
    "طيزج": UNAMBIGUOUS, "زبج": UNAMBIGUOUS,
}

MAGHREBI = {
    "زامل": MODERATE, "زوامل": MODERATE, "طبون": UNAMBIGUOUS,
    "نيكة": UNAMBIGUOUS, "قحبة مغربية": UNAMBIGUOUS,
    "زوق": WEAK, "حشومة": WEAK, "تقوادة": MODERATE,
    "شرمولة": WEAK, "خرج": WEAK, "زبي ديالي": UNAMBIGUOUS,
    "طابون": UNAMBIGUOUS, "قحبه مغربيه": UNAMBIGUOUS,
}

SUDANESE_YEMENI = {
    "شرمطة": UNAMBIGUOUS, "منيوكين": UNAMBIGUOUS,
    "كضاب": WEAK, "دلوعة": WEAK, "شفشفة": WEAK,
}

# Genre / site-furniture vocabulary. On their own these are ordinary words —
# `أفلام` is cinema, `مترجم` is subtitling — so they sit low and earn their
# keep in the phrase list, where they are unambiguous.
GENRE = {
    "محارم": MODERATE, "مراهقات": MODERATE, "عذراء": WEAK,
    "مثليين": WEAK, "مثليات": WEAK, "متحولين": WEAK,
    "بورن": UNAMBIGUOUS, "بورنو": UNAMBIGUOUS, "بورنهاب": UNAMBIGUOUS,
    "هنتاي": UNAMBIGUOUS, "كاميرا مباشرة": WEAK,
    "جنس جماعي": STRONG, "علاقة حميمة": MODERATE, "حميمي": WEAK,
    "شرجي": MODERATE, "فموي": MODERATE, "جماعي": WEAK,
    "استمناء": STRONG, "عادة سرية": STRONG, "مثيرات": MODERATE,
    "تدليك جنسي": UNAMBIGUOUS, "مساج جنسي": UNAMBIGUOUS,
    "بث مباشر جنسي": UNAMBIGUOUS, "دردشة جنسية": UNAMBIGUOUS,
    "فضيحة جنسية": STRONG, "مسرب": WEAK, "مسربة": WEAK,
    "بدون ملابس": STRONG, "بلا ملابس": STRONG, "شبه عارية": STRONG,
    "ملابس داخلية مثيرة": STRONG, "قصص محارم": UNAMBIGUOUS,
    "سحاق": MODERATE, "لواط": MODERATE,
}

DIALECTS = {
    "msa": MSA, "eg": EGYPTIAN, "sham": LEVANTINE, "gulf": GULF,
    "iq": IRAQI, "mgh": MAGHREBI, "sd-ye": SUDANESE_YEMENI, "genre": GENRE,
}


# --------------------------------------------------------------------------- #
# The fiqh / medical / legal register — what must stay reachable
# --------------------------------------------------------------------------- #
#
# This is the most important list in the file, and the reason several obvious
# words are missing from the tables above.
#
# `نكاح` is the Islamic marriage contract. `زنا`, `لواط`, `سحاق`, `عورة`,
# `جنابة`, `حيض` are core fiqh vocabulary that appears in every tafsir, hadith
# collection, fatwa archive and family-law text. A filter that blocks them does
# not block pornography — it blocks Islamic scholarship, for the exact audience
# a tool called حصن is built for. They are therefore NOT positive terms at any
# weight, and these phrases actively cancel anything that co-occurs with them.
FIQH_REGISTER = [
    "أحكام النكاح", "عقد النكاح", "النكاح في الإسلام", "شروط النكاح",
    "الطلاق", "العدة", "المهر", "الولي", "الشهود",
    "حد الزنا", "الزنا في الإسلام", "كفارة", "التوبة", "الاستغفار",
    "الطهارة", "الجنابة", "الحيض", "النفاس", "الغسل", "الوضوء",
    "العورة", "ستر العورة", "الحجاب", "النظر", "الخلوة",
    "فتوى", "الفتوى", "فقه", "الفقه", "أصول الفقه", "الشريعة",
    "تفسير", "التفسير", "الحديث", "صحيح البخاري", "صحيح مسلم",
    "سورة", "آية", "القرآن", "السنة", "الشيخ", "العلامة",
    "حلال", "حرام", "المذهب", "الحنفي", "المالكي", "الشافعي", "الحنبلي",
    "إسلام ويب", "الإسلام سؤال وجواب", "دار الإفتاء",
    # Structural markers of a fatwa or lesson, rather than its subject. These
    # are what separate `ما حكم الزنا؟` on a fatwa site from the same words on
    # a page that is not scholarship: a Q-and-A frame, an isnad, a closing
    # formula. A four-line fatwa carries almost no other negative signal, and
    # before these were added it was blocked on the strength of its title.
    "والله أعلم", "الله أعلم", "السؤال", "الجواب", "الحمد لله",
    "صلى الله عليه وسلم", "رضي الله عنه", "قال تعالى", "روى", "الراجح",
    "كبائر", "الذنوب", "حرمه الله", "حرم الله", "من الكبائر",
    "أهل العلم", "جمهور الفقهاء", "ابن تيمية", "ابن باز", "ابن عثيمين",
    "اللجنة الدائمة", "حكم", "الحكم الشرعي", "دليل", "الأدلة",
]

MEDICAL_LEGAL_REGISTER = [
    "الصحة الإنجابية", "طب النساء", "أمراض النساء", "الحمل", "الولادة",
    "الرضاعة", "سرطان الثدي", "فحص الثدي", "الدورة الشهرية",
    "الغدد", "الهرمونات", "البلوغ", "المراهقة", "علم النفس",
    "التوعية", "التثقيف", "وزارة الصحة", "منظمة الصحة",
    "القانون", "المادة", "العقوبات", "المحكمة", "النيابة",
    "الاتجار بالبشر", "حماية الطفل", "الإبلاغ", "مكافحة",
]


# --------------------------------------------------------------------------- #
# Innocent vocabulary that must never match
# --------------------------------------------------------------------------- #
#
# Arabic is templatic, so short explicit roots collide with common words that
# merely share consonants. Matching is whole-token, which handles most of it —
# `كسر` is a different token from `كس` — but `token_variants` strips affixes,
# and that is where a collision can still be manufactured. Every word here is
# asserted un-matched by the test suite.
INNOCENT = [
    # كس collides
    "كسر", "كسرة", "مكسور", "كسب", "مكاسب", "اكتساب", "كسل", "كسول",
    "كساء", "كسوة", "كسوف", "انكسار", "كاسر", "مكسرات",
    # نيك collides
    "نيكولا", "بيكنيك", "تكنيك", "الكترونيك", "نيكل",
    # زب collides
    "زبون", "زبائن", "زبدة", "زبيب", "زبالة", "زبد", "زبرجد",
    # بز collides
    "بزر", "بزور", "مبزر",
    # طيز / طز collides
    "طازج", "طيران", "طيبة",
    # مص / لحس collides
    "مصر", "مصري", "مصنع", "مصباح", "ملحس", "مصحف", "مصلحة",
    # عير / عرص collides
    "معيار", "عيار", "شعير", "عرصة", "قرص", "عريض",
    # حب / قحب collides
    "قحط", "حبة", "محبة", "صاحب", "قحطان",
    # general high-traffic
    "الجنسية", "جنسية", "جنس", "الجنس", "زواج", "الزواج", "خطوبة",
    "أسرة", "عائلة", "أطفال", "تربية", "مدرسة", "جامعة",
]


# --------------------------------------------------------------------------- #
# Expansion: orthographic variants normalize() does not already fold
# --------------------------------------------------------------------------- #
#
# normalize() folds hamza forms, alef maqsura, taa marbuta, tashkeel and
# tatweel. What it does NOT fold is dialectal *consonant* substitution, which is
# how the same word is spelled differently in different countries: Iraqi and
# Gulf writers render ق as ك or g, Egyptians often write ق as أ, and ث/ذ/ظ
# collapse toward ت/د/ز across most spoken dialects.
CONSONANT_SWAPS = [
    ("ق", "ك"), ("ق", "غ"), ("ث", "ت"), ("ث", "س"),
    ("ذ", "د"), ("ذ", "ز"), ("ظ", "ض"), ("ض", "ظ"),
    ("ج", "ق"),
]


def orthographic_variants(term: str) -> set[str]:
    """One substitution deep, never compounded.

    Two substitutions produce strings no one actually types and start colliding
    with unrelated words, so the expansion stops at one.
    """
    out = set()
    for src, dst in CONSONANT_SWAPS:
        if src in term:
            out.add(term.replace(src, dst))
    return out


def short_stem_affixes(term: str) -> set[str]:
    """Deliberately empty. Kept as documentation of an idea that was wrong.

    The reasoning looked sound: `token_variants` only strips a prefix when 3+
    characters remain, so a two-letter stem like `كس` is never recovered from
    `الكس`, and listing the affixed forms outright would close a real gap.

    It closes that gap and opens a worse one. The affixed forms of a two-letter
    stem are exactly what innocent longer words strip *down* to. `الزبون`
    (the customer) ends in `ون`, which is a suffix the stripper removes, leaving
    `الزب` — so emitting `الزب` blocks every page with a customer-service
    section. That was caught by scoring a real page, not by the unit check,
    because the check tested `زبون` and the collision only appears in the
    definite form.

    The two-letter stems this was meant to help (`كس`, `زب`, `بز`, `مص`) are all
    ambiguous enough to sit at MODERATE or below anyway. Their unambiguous
    inflections — `كسها`, `زبك`, `بزازها` — are curated explicitly above, are
    3+ characters, and are reached by the stripper without help.
    """
    return set()


# --------------------------------------------------------------------------- #
# Expansion: phrases
# --------------------------------------------------------------------------- #
#
# Phrases are matched EXACTLY — `buildIndex` stores them joined by a single
# space and `variants()` is never applied to them — so unlike single tokens
# these genuinely have to be enumerated. They are also the highest-precision
# rows in the file: `أفلام سكس` cannot plausibly mean anything else, which is
# why the generic halves (`أفلام`, `تحميل`) can stay out of the token list
# entirely and still contribute.

PHRASE_HEADS = [
    "أفلام", "فيلم", "مقاطع", "مقطع", "صور", "صورة", "فيديو", "فيديوهات",
    "قصص", "قصة", "مواقع", "موقع", "تحميل", "مشاهدة", "عرض", "شاهد",
    "أحدث", "أجمل", "أحلى", "مجموعة", "سلسلة", "روابط", "منتدى", "البوم",
]

PHRASE_CORES = [
    "سكس", "إباحية", "إباحي", "نيك", "خلاعة", "دعارة", "بورن", "عاري",
    "عارية", "شرموطة", "قحبة", "محارم", "سكسي", "عاهرات", "متناكة",
    "طيز", "كس", "بزاز", "شرجي", "استمناء", "تعري", "نياكة",
]

PHRASE_QUALIFIERS = [
    "عربي", "عربية", "مصري", "مصرية", "خليجي", "سعودي", "مغربي", "لبناني",
    "سوري", "عراقي", "تونسي", "جزائري", "أردني", "فلسطيني", "كويتي",
    "إماراتي", "قطري", "يمني", "سوداني", "ليبي",
    "مترجم", "محارم", "مراهقات", "ساخن", "ساخنة", "نار", "حقيقي",
    "مسرب", "فضيحة", "محجبات", "منقبات", "بنات", "نساء",
    "مجاني", "مجانا", "اون لاين", "جديد", "كامل", "hd",
]


def phrases() -> dict[str, float]:
    """head+core, and head+core+qualifier.

    Capped at three words because `maxPhrase` drives the scanner's inner loop —
    every extra word is another pass over every token on every page, and a
    four-word explicit phrase is vanishingly rare on a page that a two-word one
    has not already caught.
    """
    out: dict[str, float] = {}
    for head in PHRASE_HEADS:
        for core in PHRASE_CORES:
            out[f"{head} {core}"] = UNAMBIGUOUS
    for core in PHRASE_CORES:
        for qual in PHRASE_QUALIFIERS:
            out[f"{core} {qual}"] = UNAMBIGUOUS
    # Three-word titles, from the most productive heads and cores only. This is
    # the one place the count grows faster than the coverage does: a page titled
    # `أفلام سكس مصري` already trips the single token `سكس`, so the phrase adds
    # only the title zone's ×3 weighting rather than a new catch. Kept because
    # it is exact-match and therefore cannot misfire, but deliberately not
    # expanded across the full cross-product.
    for head in PHRASE_HEADS[:12]:
        for core in PHRASE_CORES[:12]:
            for qual in PHRASE_QUALIFIERS[:16]:
                out[f"{head} {core} {qual}"] = UNAMBIGUOUS
    return out


# Franco-Arabic phrases. Worth enumerating separately rather than romanising
# the Arabic ones: Franco writers pick different heads (`aflam`, not `فيلم`)
# and the spellings are conventionalised rather than derived.
FRANCO_HEADS = ["aflam", "film", "sowar", "sour", "video", "fedio", "qesas",
                "mawqe3", "tahmil", "moshahda"]
FRANCO_CORES = ["sks", "sex", "neek", "nik", "porn", "ebahy", "shrmota",
                "qahba", "kahba", "3ahra"]
FRANCO_QUALS = ["arab", "arabi", "masry", "masri", "khaliji", "saudi",
                "maghribi", "lebnani", "souri", "3iraqi", "motarjam",
                "mahareem", "mojani", "hd", "jadid"]


def franco_phrases() -> dict[str, float]:
    out: dict[str, float] = {}
    for head in FRANCO_HEADS:
        for core in FRANCO_CORES:
            out[f"{head} {core}"] = UNAMBIGUOUS
    for core in FRANCO_CORES:
        for qual in FRANCO_QUALS:
            out[f"{core} {qual}"] = UNAMBIGUOUS
    for head in FRANCO_HEADS[:6]:
        for core in FRANCO_CORES[:6]:
            for qual in FRANCO_QUALS[:10]:
                out[f"{head} {core} {qual}"] = UNAMBIGUOUS
    return out


# --------------------------------------------------------------------------- #
# Expansion: Franco-Arabic
# --------------------------------------------------------------------------- #
#
# This tier matters more than the Arabic one for hostnames, which are ASCII: an
# Arabic-audience site is `sks-arab.com`, never `سكس-عرب.com`. The digit
# substitutions are not decoration — 3/7/5/9/2 are how ع/ح/خ/ق/ء are typed on a
# Latin keyboard, and a word is routinely spelled five different ways by five
# people.

TRANSLIT = {
    "ا": ["a", ""], "ب": ["b"], "ت": ["t"], "ث": ["th", "s"],
    "ج": ["j", "g"], "ح": ["7", "h"], "خ": ["5", "kh"], "د": ["d"],
    "ذ": ["th", "z"], "ر": ["r"], "ز": ["z"], "س": ["s"],
    "ش": ["sh", "ch"], "ص": ["s"], "ض": ["d"], "ط": ["t", "6"],
    "ظ": ["z"], "ع": ["3", "a"], "غ": ["gh", "3'"], "ف": ["f"],
    "ق": ["9", "q", "k", "g"], "ك": ["k"], "ل": ["l"], "م": ["m"],
    "ن": ["n"], "ه": ["h", "a"], "و": ["w", "o", "u"], "ي": ["y", "i", "e"],
    "ء": ["2", ""],
}

MAX_TRANSLIT_PER_WORD = 12
MIN_TRANSLIT_LEN = 4

# Romanisations that are also ordinary English words or names.
#
# Every one of these was produced by the transliterator and caught by the
# dictionary check in `test_terms.py`: `عريان` romanises to `aryan`, `معرص` to
# `mars`, `طيز` to `tare` and `tari`. A three-letter Franco string is especially
# prone to this, which is why MIN_TRANSLIT_LEN exists alongside the list —
# short generated forms collide with something innocent far more often than
# they catch anything the curated `terms.ar-latn.tsv` has not already covered.
FRANCO_EXCLUDE = {
    "aryan", "arian", "arean", "aira", "mars", "tare", "tari", "tez", "tiza",
    "nig", "sit", "tit",
    "ass", "anal", "bass", "mass", "lass", "kiss", "miss", "boss", "bar",
    "car", "tar", "war", "far", "star", "near", "bear", "hear", "year",
    "shark", "share", "sharia", "arab", "arabi", "aziz", "amir", "samir",
    "nasr", "badr", "sabr", "zahra", "bahr", "nahr", "shams", "qamar",
}


def transliterate(term: str) -> list[str]:
    """Plausible Latin spellings, breadth-first and capped.

    Uncapped this is a product of every letter's options and produces thousands
    of strings per word, almost all of which nobody has ever typed. The cap
    keeps the common spellings — the ones generated from the first option of
    each letter outward — and discards the tail.
    """
    if " " in term:
        return []
    forms = [""]
    for ch in term:
        opts = TRANSLIT.get(ch)
        if opts is None:
            return []
        forms = [f + o for f in forms for o in opts][: MAX_TRANSLIT_PER_WORD * 6]
    out = []
    for f in forms:
        if (MIN_TRANSLIT_LEN <= len(f) <= 20
                and f not in out
                and f not in FRANCO_EXCLUDE):
            out.append(f)
        if len(out) >= MAX_TRANSLIT_PER_WORD:
            break
    return out


# --------------------------------------------------------------------------- #
# Assembly
# --------------------------------------------------------------------------- #

def build() -> tuple[dict[str, float], dict[str, float]]:
    arabic: dict[str, float] = {}

    def add(term: str, weight: float) -> None:
        t = normalize(term)
        if not t:
            return
        # Never downgrade a weight already set by a more confident source.
        if t not in arabic or arabic[t] < weight:
            arabic[t] = weight

    for table in DIALECTS.values():
        for term, weight in table.items():
            add(term, weight)
            for variant in orthographic_variants(normalize(term)):
                # A generated spelling is one step less certain than the
                # curated one it came from.
                add(variant, max(WEAK, weight - 2.0))
            for affixed in short_stem_affixes(normalize(term)):
                add(affixed, weight)

    for phrase, weight in phrases().items():
        add(phrase, weight)

    latin: dict[str, float] = {}
    for table in (MSA, EGYPTIAN, LEVANTINE, GULF, IRAQI, MAGHREBI,
                  SUDANESE_YEMENI):
        for term, weight in table.items():
            if weight < STRONG:
                # Only confident words are worth romanising: a Latin spelling
                # of an ambiguous Arabic word is ambiguous twice over.
                continue
            for form in transliterate(normalize(term)):
                if form not in latin or latin[form] < weight:
                    latin[form] = weight

    for phrase, weight in franco_phrases().items():
        if phrase not in latin or latin[phrase] < weight:
            latin[phrase] = weight

    return arabic, latin


# Prefixes an innocent word is routinely written with. Checking only the bare
# form is what let `الزب` through: `زبون` is clean, and `الزبون` — the form
# actually written on a page — strips its `ون` suffix down to a term.
INNOCENT_PREFIXES = ["", "ال", "وال", "بال", "لل", "و", "ب", "ل", "ف", "ك"]
INNOCENT_SUFFIXES = ["", "ات", "ون", "ين", "ها", "هم", "ك", "ي", "ه"]


# Collisions that are real but accepted, each with the reason.
#
# `حبة` (a pill, a grain) takes the prefix `ك` (like/as) to give `كحبة`, which
# is also how Iraqi spells the word for a prostitute. Both readings exist. The
# vulgar one is overwhelmingly the more common as a standalone token — "like a
# pill" is nearly always written `كحبة دواء`, with the noun it modifies — and
# dropping the Iraqi term would leave a whole dialect's most common explicit
# word uncovered. Kept, and the medical register in negatives.ar-generated.tsv
# is what pulls a genuine pharmacology page back down.
ALLOWED_COLLISIONS = {"كحبه"}


def innocent_forms() -> set[str]:
    """Every spelling of an innocent word a page might realistically contain.

    Prefix-only and suffix-only, never both at once. Stacking them produces
    strings like `كحبههم` that are not words in any register, and a check that
    fails on non-words is a check people learn to switch off.
    """
    out = set()
    for word in INNOCENT:
        base = normalize(word)
        out.add(base)
        out.update(p + base for p in INNOCENT_PREFIXES if p)
        out.update(base + s for s in INNOCENT_SUFFIXES if s)
    return out - ALLOWED_COLLISIONS


def false_positives(arabic: dict[str, float]) -> list[tuple[str, str]]:
    """Innocent words that a generated term would match. Must be empty.

    Runs each innocent word through the same `token_variants` the scorer uses,
    so this asks the question the runtime will ask rather than a simpler one.
    """
    hits = []
    for form in sorted(innocent_forms()):
        for variant in token_variants(form):
            if variant in arabic:
                hits.append((form, variant))
    return hits


HEADER_AR = """\
# Arabic explicit terms — GENERATED, do not edit by hand.
#
#   python3 gen_terms_ar.py
#
# Curated vocabulary lives in gen_terms_ar.py, organised by dialect; the
# spellings below are expanded from it. Edit the generator, not this file.
#
# terms.py reads this AFTER terms.ar.tsv, and the compiler keeps the first
# weight it sees, so any hand-set weight in terms.ar.tsv always wins over the
# generated one here.
"""

HEADER_LATN = """\
# Franco-Arabic explicit terms — GENERATED, do not edit by hand.
#
#   python3 gen_terms_ar.py
#
# Latin spellings expanded from the curated Arabic vocabulary in
# gen_terms_ar.py. Read after terms.ar-latn.tsv, which wins on weight.
"""

HEADER_NEG = """\
# Arabic negative terms — GENERATED, do not edit by hand.
#
#   python3 gen_terms_ar.py
#
# These CANCEL explicit terms that co-occur with them, and they are the reason
# this filter can carry 5,000 Arabic terms without blocking the Arabic web.
#
# The fiqh register is the point. نكاح is the Islamic marriage contract; زنا,
# لواط, سحاق, عورة, جنابة and حيض are core jurisprudence vocabulary appearing in
# every tafsir, hadith collection and fatwa archive. A filter that treats them
# as pornography does not block pornography — it blocks Islamic scholarship,
# for precisely the audience a tool called حصن exists to serve. So those words
# are absent from the positive tables at any weight, and the phrases below
# actively pull a page back below threshold when it reads like scholarship.
#
# The medical and legal register is here for the same reason: reproductive
# health, gynaecology, child-protection reporting and criminal-code text all
# discuss sex explicitly and none of it is adult content.
"""


NEGATIVE_WEIGHT = 7.0


def write(path: Path, header: str, rows: dict[str, float]) -> None:
    lines = [header]
    for term in sorted(rows):
        lines.append(f"{term}\t{rows[term]:.1f}")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--check", action="store_true",
                    help="verify only; do not write")
    args = ap.parse_args()

    arabic, latin = build()

    hits = false_positives(arabic)
    if hits:
        print("FALSE POSITIVES — innocent words that would be blocked:",
              file=sys.stderr)
        for word, via in hits:
            print(f"  {word}  matches term  {via}", file=sys.stderr)
        return 1

    if args.check:
        print(f"ok: {len(arabic)} arabic, {len(latin)} franco, "
              f"no false positives")
        return 0

    negatives = {
        normalize(t): NEGATIVE_WEIGHT
        for t in FIQH_REGISTER + MEDICAL_LEGAL_REGISTER
        if normalize(t)
    }

    write(TERMS_DIR / "terms.ar-generated.tsv", HEADER_AR, arabic)
    write(TERMS_DIR / "terms.ar-latn-generated.tsv", HEADER_LATN, latin)
    write(TERMS_DIR / "negatives.ar-generated.tsv", HEADER_NEG, negatives)
    print(f"wrote {len(arabic)} arabic terms -> terms.ar-generated.tsv")
    print(f"wrote {len(latin)} franco terms  -> terms.ar-latn-generated.tsv")
    print(f"wrote {len(negatives)} negatives -> negatives.ar-generated.tsv")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
