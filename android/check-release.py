#!/usr/bin/env python3
"""Inspect a built release APK before it is shared.

Fails when the APK is debuggable or test-only, declares or requires the DUMP
permission anywhere, contains a component or class that exists only in the
debug source set, or carries a package name, versionCode or versionName that
differs from app/build.gradle. Uses aapt2 and dexdump from the Android SDK
build-tools; nothing is installed or changed.

    ./gradlew --no-daemon assembleRelease
    python3 check-release.py app/build/outputs/apk/release/app-release-unsigned.apk

Without an argument it checks app-release.apk if present, else
app-release-unsigned.apk. Tools come from --build-tools DIR, else the newest
build-tools under ANDROID_HOME or ANDROID_SDK_ROOT, else PATH.
"""
import argparse
import hashlib
import os
import pathlib
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent
ANDROID = "{http://schemas.android.com/apk/res/android}"
DUMP = "android.permission.DUMP"


def tool(name, build_tools):
    folders = [pathlib.Path(build_tools)] if build_tools else []
    for variable in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        sdk = os.environ.get(variable)
        if sdk and (pathlib.Path(sdk) / "build-tools").is_dir():
            versions = [p for p in (pathlib.Path(sdk) / "build-tools").iterdir() if (p / name).is_file()]
            folders += sorted(versions, key=lambda p: [int(x) if x.isdigit() else 0 for x in re.split(r"[.-]", p.name)], reverse=True)
    for folder in folders:
        if (folder / name).is_file():
            return str(folder / name)
    found = shutil.which(name)
    if not found:
        sys.exit(f"check-release: {name} not found; pass --build-tools or set ANDROID_HOME")
    return found


def run(*command):
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode != 0:
        sys.exit(f"check-release: {os.path.basename(command[0])} failed: {result.stderr.strip()[:500]}")
    return result.stdout


def gradle_values():
    text = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
    found = {}
    for key, pattern in (("package", r"applicationId\s+'([^']+)'"), ("versionCode", r"versionCode\s+(\d+)"),
                         ("versionName", r"versionName\s+'([^']+)'")):
        match = re.findall(pattern, text)
        if len(match) != 1:
            sys.exit(f"check-release: expected one {key} in app/build.gradle, found {len(match)}")
        found[key] = match[0]
    return found


def manifest_elements(xmltree):
    """(element name, {attribute: value}) for every element in aapt2's xmltree dump."""
    elements = []
    for line in xmltree.splitlines():
        element = re.match(r"\s*E: (\S+) \(line=\d+\)", line)
        if element:
            elements.append((element.group(1), {}))
            continue
        attribute = re.match(r"\s*A: (?:\S+:)?([\w-]+)(?:\(0x[0-9a-f]+\))?=(.*)$", line)
        if attribute and elements:
            value = attribute.group(2)
            quoted = re.match(r'"(.*)" \(Raw: ".*"\)$', value) or re.match(r'"(.*)"$', value)
            elements[-1][1][attribute.group(1)] = quoted.group(1) if quoted else value
    return elements


def is_true(value):
    return value is not None and (value == "true" or value.endswith("0xffffffff"))


def debug_only():
    """Components the debug manifest adds, and classes only the debug source set defines."""
    manifest = ET.parse(ROOT / "app/src/debug/AndroidManifest.xml").getroot()
    components = {node.get(ANDROID + "name") for node in manifest.iter()
                  if node.tag in ("activity", "activity-alias", "service", "receiver", "provider")}
    def stems(variant):
        folder = ROOT / f"app/src/{variant}/java"
        return {p.relative_to(folder).with_suffix("").as_posix() for p in folder.rglob("*.java")}
    classes = stems("debug") - stems("release")
    return components, classes


def main():
    parser = argparse.ArgumentParser(description="Check a built release APK for debug-only content.")
    parser.add_argument("apk", nargs="?", help="APK to inspect")
    parser.add_argument("--build-tools", help="SDK build-tools folder that holds aapt2 and dexdump")
    arguments = parser.parse_args()
    if arguments.apk:
        apk = pathlib.Path(arguments.apk)
    else:
        release = ROOT / "app/build/outputs/apk/release"
        apk = next((p for p in (release / "app-release.apk", release / "app-release-unsigned.apk") if p.is_file()), None)
        if apk is None:
            sys.exit("check-release: no release APK; run ./gradlew assembleRelease or pass a path")
    if not apk.is_file():
        sys.exit(f"check-release: {apk} not found")
    aapt2, dexdump = tool("aapt2", arguments.build_tools), tool("dexdump", arguments.build_tools)
    expected = gradle_values()
    components, classes = debug_only()
    failures = []

    badging = run(aapt2, "dump", "badging", str(apk))
    package = re.search(r"^package: name='([^']*)' versionCode='([^']*)' versionName='([^']*)'", badging, re.M)
    if not package:
        sys.exit("check-release: aapt2 badging has no package line")
    for key, actual in zip(("package", "versionCode", "versionName"), package.groups()):
        if actual != expected[key]:
            failures.append(f"{key} is {actual!r}, app/build.gradle says {expected[key]!r}")
    if re.search(r"^application-debuggable", badging, re.M):
        failures.append("aapt2 reports the application as debuggable")

    elements = manifest_elements(run(aapt2, "dump", "xmltree", "--file", "AndroidManifest.xml", str(apk)))
    if not any(name == "application" for name, _ in elements):
        sys.exit("check-release: manifest dump has no application element")
    for name, attributes in elements:
        if name == "application":
            for flag in ("debuggable", "testOnly"):
                if is_true(attributes.get(flag)):
                    failures.append(f"application has android:{flag}=true")
        if name == "manifest" and is_true(attributes.get("testOnly")):
            failures.append("manifest has android:testOnly=true")
        for attribute, value in attributes.items():
            if value == DUMP:
                where = attributes.get("name", "")
                failures.append(f"{name} {where} uses the DUMP permission ({attribute})".replace("  ", " "))
        if attributes.get("name") in components:
            failures.append(f"debug-only {name} {attributes['name']} is declared")
    shipped = [name for name, attributes in elements if name in ("activity", "activity-alias")]

    descriptors = set(re.findall(r"Class descriptor\s*: 'L([^;]+);'", run(dexdump, str(apk))))
    if not descriptors:
        sys.exit("check-release: dexdump listed no classes")
    for descriptor in sorted(descriptors):
        if descriptor.split("$", 1)[0] in classes:
            failures.append(f"debug-only class {descriptor.replace('/', '.')} is in the dex")

    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    if failures:
        print(f"Release check FAILED for {apk.name} ({digest}):")
        for failure in failures:
            print("  - " + failure)
        sys.exit(1)
    print(f"Release check passed: {apk.name} is {expected['package']} {expected['versionName']} ({expected['versionCode']}), "
          f"not debuggable or test-only, no DUMP permission; {len(shipped)} activities and {len(descriptors)} classes "
          f"hold none of the {len(components)} debug-only components or {len(classes)} debug-only classes. SHA-256 {digest}")


if __name__ == "__main__":
    main()
