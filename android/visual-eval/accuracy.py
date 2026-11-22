"""Shared pieces of the visual accuracy run: labels, phone-sized views and scoring.

Imported by make-views.py, run-accuracy.py and test-accuracy.py. Standard library plus
Pillow only. Nothing here reads or writes pixels outside the folders it is given, and no
report contains pixels, file paths or source URLs of adult material: only ids, labels,
verdicts and counts.
"""
import hashlib
import json
import math
import re
import statistics
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
ID = re.compile(r"[a-zA-Z0-9_-]{1,64}")
SCREEN = (1080, 2400)
VIEWS = ("full", "tall", "feed", "tile", "partial")
# "partial" cuts off the top 45% of the picture, which can remove the very face or body part a
# label is about. It is opt-in, adult sets only, and reported as its own view.
DEFAULT_VIEWS = ("full", "tall", "feed", "tile")
PEOPLE = ("woman", "man")
MODES = ("adult", "people-all", "people-female", "people-male")


def sha256_file(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as source:
        for chunk in iter(lambda: source.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def inside_repo(path):
    resolved = Path(path).resolve()
    return resolved == REPO or REPO in resolved.parents


def load_labels(path):
    """Validated labels.json.

    schema 1, kind "adult" or "people". Every case has id, file (relative to the source
    folder) and sha256 of that file. An adult case has expected "block" or "allow" and a
    category. A people case has people (a list drawn from "woman"/"man", empty for no
    person) and optional attributes (string to string, e.g. headwear, skin, age_group, pose).
    """
    labels = json.loads(Path(path).read_text(encoding="utf-8"))
    if labels.get("schema") != 1 or labels.get("kind") not in ("adult", "people"):
        raise ValueError("labels.json needs schema 1 and kind adult or people")
    seen = set()
    for case in labels["cases"]:
        if not ID.fullmatch(case.get("id", "")) or case["id"] in seen:
            raise ValueError("Invalid or repeated case id: %r" % case.get("id"))
        seen.add(case["id"])
        if not re.fullmatch(r"[0-9a-f]{64}", case.get("sha256", "")):
            raise ValueError("Case %s needs the source file's sha256" % case["id"])
        file = case.get("file", "")
        if not file or Path(file).is_absolute() or ".." in Path(file).parts:
            raise ValueError("Case %s needs a relative file name" % case["id"])
        if labels["kind"] == "adult":
            if case.get("expected") not in ("block", "allow") or not case.get("category"):
                raise ValueError("Adult case %s needs expected block/allow and a category" % case["id"])
        else:
            people = case.get("people")
            if not isinstance(people, list) or any(p not in PEOPLE for p in people):
                raise ValueError("People case %s needs people drawn from %s" % (case["id"], PEOPLE))
            attributes = case.get("attributes", {})
            if not isinstance(attributes, dict) or not all(
                    isinstance(k, str) and isinstance(v, str) for k, v in attributes.items()):
                raise ValueError("People case %s attributes must be strings" % case["id"])
    if not labels["cases"]:
        raise ValueError("No cases")
    return labels


def has_adult_positives(labels):
    return labels["kind"] == "adult" and any(c["expected"] == "block" for c in labels["cases"])


# ---- phone-sized views ---------------------------------------------------------------------

def _fit(image, width, height):
    scale = min(width / image.width, height / image.height)
    return image.resize((max(1, round(image.width * scale)), max(1, round(image.height * scale))), _lanczos())


def _cover(image, width, height):
    scale = max(width / image.width, height / image.height)
    resized = image.resize((max(width, round(image.width * scale)), max(height, round(image.height * scale))), _lanczos())
    left, top = (resized.width - width) // 2, (resized.height - height) // 2
    return resized.crop((left, top, left + width, top + height))


def _lanczos():
    from PIL import Image
    return Image.Resampling.LANCZOS


def render(image, view):
    """One 1080 x 2400 screen showing the picture the way an app commonly would."""
    from PIL import Image, ImageDraw
    image = image.convert("RGB")
    width, height = SCREEN
    if view == "full":
        # A photo viewer or a landscape video: letterboxed on black.
        screen = Image.new("RGB", SCREEN, (0, 0, 0))
        fitted = _fit(image, width, height)
        screen.paste(fitted, ((width - fitted.width) // 2, (height - fitted.height) // 2))
    elif view == "tall":
        # Short video: the picture fills the screen and its edges are cut.
        screen = _cover(image, width, height)
    elif view == "partial":
        # Half scrolled past: the top 45% of the picture is above the screen.
        screen = Image.new("RGB", SCREEN, (0, 0, 0))
        fitted = _fit(image, width, height)
        screen.paste(fitted, ((width - fitted.width) // 2, -round(fitted.height * 0.45)))
    elif view == "feed":
        # A social feed post: app bar, author row, picture at 4:5 to 1.91:1, caption lines.
        screen = Image.new("RGB", SCREEN, (255, 255, 255))
        draw = ImageDraw.Draw(screen)
        draw.rectangle((0, 0, width, 210), fill=(245, 245, 245))
        draw.ellipse((40, 250, 140, 350), fill=(200, 200, 200))
        draw.rectangle((170, 280, 520, 316), fill=(220, 220, 220))
        ratio = min(max(image.width / image.height, 0.8), 1.91)
        post = _cover(image, width, round(width / ratio))
        screen.paste(post, (0, 390))
        y = 390 + post.height + 40
        for line_width in (900, 760, 520):
            draw.rectangle((40, y, 40 + line_width, y + 30), fill=(225, 225, 225))
            y += 60
        draw.rectangle((0, height - 160, width, height), fill=(250, 250, 250))
    elif view == "tile":
        # A search or profile grid: three columns of square tiles, the picture in one of them.
        screen = Image.new("RGB", SCREEN, (255, 255, 255))
        draw = ImageDraw.Draw(screen)
        draw.rectangle((0, 0, width, 260), fill=(245, 245, 245))
        side, gap, top = 356, 6, 300
        for row in range(5):
            for column in range(3):
                x, y = column * (side + gap), top + row * (side + gap)
                if (row, column) == (1, 1):
                    screen.paste(_cover(image, side, side), (x, y))
                else:
                    tone = 150 + 17 * ((row * 3 + column) % 6)
                    draw.rectangle((x, y, x + side - 1, y + side - 1), fill=(tone, tone - 8, tone - 16))
    else:
        raise ValueError("Unknown view " + view)
    return screen


# ---- scoring -------------------------------------------------------------------------------

def wilson(hits, total, z=1.959964):
    """95% Wilson score interval for hits/total, or None for an empty group."""
    if total == 0:
        return None
    p = hits / total
    centre = (p + z * z / (2 * total)) / (1 + z * z / total)
    margin = z * math.sqrt(p * (1 - p) / total + z * z / (4 * total * total)) / (1 + z * z / total)
    return [round(max(0.0, centre - margin), 4), round(min(1.0, centre + margin), 4)]


def rate(hits, total):
    return {"hits": hits, "total": total, "rate": None if total == 0 else round(hits / total, 4),
            "ci95": wilson(hits, total)}


def target(mode, people):
    """Whether a people setting should cover a picture holding these people."""
    if mode == "people-all":
        return bool(people)
    return ("woman" if mode == "people-female" else "man") in people


def score(mode, cases, results):
    """Join device verdicts with the computer-side labels and count.

    A still picture whose verdict is "block" is blocked in the product: a non-strong block is
    confirmed by the next capture of the same unchanged screen. "unknown" is reported on its
    own and counted as not blocked, because the product does not act on it as a block.
    """
    by_id = {r["id"]: r for r in results}
    missing = [c["id"] for c in cases if c["id"] not in by_id]
    if missing:
        raise ValueError("%d views have no device result" % len(missing))
    rows = []
    for case in cases:
        result = by_id[case["id"]]
        rows.append(dict(case, actual=result["actual"], strong=bool(result.get("strong")),
                         person=bool(result.get("person")), cropped=bool(result.get("cropped")),
                         ms=result.get("roundtrip_ms")))
    summary = {"mode": mode, "views": len(rows), "pictures": len({r.get("source", r["id"]) for r in rows}),
               "unknown": sum(r["actual"] not in ("block", "allow") for r in rows)}
    times = [r["ms"] for r in rows if isinstance(r["ms"], (int, float))]
    if times:
        ordered = sorted(times)
        summary["latency_ms"] = {"median": round(statistics.median(ordered), 1),
                                 "p95": round(ordered[min(len(ordered) - 1, math.ceil(0.95 * len(ordered)) - 1)], 1)}
    if mode == "adult":
        def counts(group):
            positives = [r for r in group if r["expected"] == "block"]
            negatives = [r for r in group if r["expected"] == "allow"]
            blocked = [r for r in positives if r["actual"] == "block"]
            return {"recall": rate(len(blocked), len(positives)),
                    "false_block": rate(sum(r["actual"] == "block" for r in negatives), len(negatives)),
                    "strong_share_of_caught": rate(sum(r["strong"] for r in blocked), len(blocked)),
                    "unknown": sum(r["actual"] not in ("block", "allow") for r in group)}
        summary["overall"] = counts(rows)
        summary["by_view"] = {v: counts([r for r in rows if r["view"] == v]) for v in _values(rows, "view")}
        summary["by_category"] = {c: counts([r for r in rows if r["category"] == c]) for c in _values(rows, "category")}
    else:
        def counts(group):
            wanted = [r for r in group if target(mode, r["people"])]
            others = [r for r in group if r["people"] and not target(mode, r["people"])]
            empty = [r for r in group if not r["people"]]
            return {"covered_when_wanted": rate(sum(r["person"] for r in wanted), len(wanted)),
                    "covered_other_people": rate(sum(r["person"] for r in others), len(others)),
                    "covered_no_person": rate(sum(r["person"] for r in empty), len(empty)),
                    "adult_block": rate(sum(r["actual"] == "block" for r in group), len(group))}
        summary["overall"] = counts(rows)
        summary["by_view"] = {v: counts([r for r in rows if r["view"] == v]) for v in _values(rows, "view")}
        keys = sorted({k for r in rows for k in r.get("attributes", {})})
        summary["by_attribute"] = {
            k: {v: counts([r for r in rows if r.get("attributes", {}).get(k) == v])
                for v in sorted({r.get("attributes", {}).get(k) for r in rows} - {None})}
            for k in keys}
    return summary


def _values(rows, key):
    return sorted({r[key] for r in rows})


def markdown(summary):
    """A short table for a dated report; the JSON stays the record."""
    lines = ["Mode `%s`, %d views of %d pictures, %d unknown." % (
        summary["mode"], summary["views"], summary["pictures"], summary["unknown"]), ""]
    if "latency_ms" in summary:
        lines += ["Median %.1f ms, p95 %.1f ms per check." % (
            summary["latency_ms"]["median"], summary["latency_ms"]["p95"]), ""]

    def cell(value):
        if value["total"] == 0:
            return "—"
        low, high = value["ci95"]
        return "%d/%d = %.1f%% (%.1f–%.1f)" % (value["hits"], value["total"], 100 * value["rate"], 100 * low, 100 * high)

    if summary["mode"] == "adult":
        columns = (("recall", "Caught (recall)"), ("false_block", "Benign blocked"))
    else:
        columns = (("covered_when_wanted", "Covered when wanted"), ("covered_other_people", "Other people covered"),
                   ("covered_no_person", "No person, covered"))
    groups = [("All", summary["overall"])] + [("view: " + k, v) for k, v in summary["by_view"].items()]
    groups += [("category: " + k, v) for k, v in summary.get("by_category", {}).items()]
    for key, values in summary.get("by_attribute", {}).items():
        groups += [("%s: %s" % (key, k), v) for k, v in values.items()]
    lines.append("| Group | " + " | ".join(title for _, title in columns) + " |")
    lines.append("|---|" + "---:|" * len(columns))
    for name, value in groups:
        lines.append("| %s | %s |" % (name, " | ".join(cell(value[key]) for key, _ in columns)))
    return "\n".join(lines) + "\n"
