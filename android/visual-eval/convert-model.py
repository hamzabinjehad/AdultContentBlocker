#!/usr/bin/env python3
"""Reproduce the Android TFLite model from the pinned NSFWJS MobileNetV2 package.

The model is a conversion, not a new model: the weights and topology are the
NSFWJS 4.4.0 MobileNetV2 weights already bundled in extension/visual, converted
with the pinned TensorFlow/TensorFlow.js versions. The TFLite output is stored
uncompressed so the Android runtime can map it from the APK.

Requires a Python environment with:
  tensorflow==2.16.2
  tensorflowjs==4.20.0
and a Node.js executable able to `require()` the checked-in NSFWJS model files.

This script uses only local files. It does not download weights, images or
notices. The output SHA-256 is compared with the reviewed value unless
--force is passed.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

EXPECTED_SHA256 = "01215075f6e759bac8931310ed95665295bd662eff547f27a33982bcf2cb4d46"
DEFAULT_OUT = Path(__file__).resolve().parents[2] / "android/app/src/main/assets/nsfw_mobilenet_v2.tflite"
MODEL_DIR = "extension/visual/node_modules/nsfwjs/dist/models/mobilenet_v2"


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def run_node_export(node, repo, destination):
    script = r"""
const fs = require('fs');
const path = require('path');
const dir = process.argv[1];
const out = process.argv[2];
const m = require(path.join(dir, 'model.min.js'));
const w = require(path.join(dir, 'group1-shard1of1.min.js'));
const manifest = JSON.parse(JSON.stringify(m.weightsManifest));
for (const group of manifest) group.paths = group.paths.map(p => p.endsWith('.bin') ? p : p + '.bin');
const json = {modelTopology: m.modelTopology, weightsManifest: manifest,
  format: 'layers-model', generatedBy: 'nsfwjs@4.4.0', convertedBy: 'tensorflowjs_converter'};
fs.writeFileSync(path.join(out, 'model.json'), JSON.stringify(json));
fs.writeFileSync(path.join(out, 'group1-shard1of1.bin'), Buffer.from(w, 'base64'));
"""
    subprocess.run([node, "-e", script, str(repo / MODEL_DIR), str(destination)],
                   check=True, cwd=repo)


def convert(converter, source, keras, output):
    subprocess.run([converter, "--input_format=tfjs_layers_model",
                    "--output_format=keras_saved_model", str(source / "model.json"), str(keras)],
                   check=True)
    import tensorflow as tf
    tflite = tf.lite.TFLiteConverter.from_saved_model(str(keras)).convert()
    output.write_bytes(tflite)
    return len(tflite)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--node", default=shutil.which("node"), help="Node.js executable")
    parser.add_argument("--converter", default=shutil.which("tensorflowjs_converter"),
                        help="tensorflowjs_converter executable from the same environment")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--force", action="store_true", help="replace a model whose digest differs")
    args = parser.parse_args()

    repo = args.repo.resolve()
    if not args.node:
        raise SystemExit("node not found; pass --node")
    if not args.converter:
        raise SystemExit("tensorflowjs_converter not found; activate the pinned environment")
    model = repo / MODEL_DIR
    for name in ("model.min.js", "group1-shard1of1.min.js"):
        if not (model / name).is_file():
            raise SystemExit(f"missing pinned NSFWJS asset: {model / name}")

    with tempfile.TemporaryDirectory(prefix="hisn-tflite-") as temporary:
        temporary = Path(temporary)
        run_node_export(args.node, repo, temporary)
        produced = temporary / "model.tflite"
        size = convert(args.converter, temporary, temporary / "keras", produced)
        digest = sha256(produced)
        print(f"TFLite {size} bytes, sha256 {digest}")
        if not args.force and digest != EXPECTED_SHA256:
            raise SystemExit("converted model differs from the reviewed SHA-256; pass --force to replace it")
        args.out.parent.mkdir(parents=True, exist_ok=True)
        staged = args.out.with_suffix(args.out.suffix + ".tmp")
        shutil.copyfile(produced, staged)
        os.replace(staged, args.out)
        print(f"wrote {args.out} ({args.out.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
