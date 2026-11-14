#!/usr/bin/env bash
# Stages the signed Android APK named in release.json and writes its checksum.
# The APK stays out of git; a deploy runs this first.
set -euo pipefail
cd "$(dirname "$0")"

python3 - <<'PY'
import hashlib
import json
import pathlib
import shutil
import sys

site = pathlib.Path('.').resolve()
root = site.parent
release = json.loads((site / 'release.json').read_text(encoding='utf-8'))
app = release['android']
source_dir = root / app['source']
source = source_dir / app['file']
if not source.is_file():
    sys.exit(f"missing signed APK: {source}\nBuild it first, or point release.json at an existing record.")
data = source.read_bytes()
digest = hashlib.sha256(data).hexdigest()
if len(data) != app['bytes']:
    sys.exit(f"size mismatch for {source.name}: {len(data)} bytes, release.json says {app['bytes']}")
if digest != app['sha256']:
    sys.exit(f"sha256 mismatch for {source.name}: {digest}, release.json says {app['sha256']}")
out = site / 'downloads'
out.mkdir(exist_ok=True)
for old in out.glob('*.apk'):
    old.unlink()
target = out / app['downloadName']
shutil.copyfile(source, target)
(out / 'SHA256SUMS').write_text(f"{digest}  {app['downloadName']}\n", encoding='utf-8')
print(f"staged {target.relative_to(root)} ({len(data):,} bytes)")
print(f"sha256 {digest}")
PY
