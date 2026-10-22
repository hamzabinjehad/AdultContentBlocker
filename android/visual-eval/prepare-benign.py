#!/usr/bin/env python3
"""Prepare pinned, attributed benign controls. No adult material or private images."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import urllib.request
from PIL import Image, ImageDraw


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=False)
    inventory = json.loads(Path(__file__).with_name("benign-sources.json").read_text())
    cases = []
    for source in inventory["sources"]:
        url = inventory["base_url"] + source["id"] + ".png"
        with urllib.request.urlopen(url, timeout=20) as response:
            raw = response.read(4000000)
        if hashlib.sha256(raw).hexdigest() != source["sha256"]:
            raise ValueError("Pinned source digest mismatch: " + source["id"])
        original = Image.open(io.BytesIO(raw)).convert("RGB")
        full = original.copy()
        full.thumbnail((384, 384), Image.Resampling.LANCZOS)
        variants = {"image": full}
        for layout, edge in (("feed", 180), ("tile", 90)):
            canvas = Image.new("RGB", (180, 384), (16, 21, 30))
            draw = ImageDraw.Draw(canvas)
            draw.rounded_rectangle((12, 14, 168, 32), radius=4, fill=(85, 110, 135))
            image = original.copy()
            image.thumbnail((edge, 200), Image.Resampling.LANCZOS)
            canvas.paste(image, ((180-image.width)//2, 80))
            for y in (300, 320, 340):
                draw.rectangle((12, y, 156, y+5), fill=(95, 110, 125))
            variants[layout] = canvas
        for layout, image in variants.items():
            key = source["id"] + "_" + layout
            output = args.out / (key + ".png")
            image.save(output)
            cases.append({"id": key, "source_id": source["id"], "expected": "allow",
                          "sha256": hashlib.sha256(output.read_bytes()).hexdigest(), "layout": layout})
    manifest = {"schema": 1, "kind": "benign-controls", "unique_sources": len(inventory["sources"]),
                "cases": cases, "sources": inventory, "repeats": 3, "interval_ms": 2000,
                "limits": "Four sources with correlated layouts; not a representative benchmark. No positive adult cases."}
    model_inventory = Path(__file__).resolve().parents[2] / "extension/visual/assets.json"
    manifest["engine_sha256"] = json.loads(model_inventory.read_text())["artifacts"]["engine.bundle.js"]["sha256"]
    (args.out / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print("Prepared", len(cases), "benign views from", len(inventory["sources"]), "pinned sources in", args.out)


if __name__ == "__main__":
    main()
