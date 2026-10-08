#!/bin/bash
#
# The one supported way to run this repo's tests.
#
#     ./test.sh              # python seed extension release macos ios (needs macOS + iOS Simulator)
#     ./test.sh python       # blocklist + profile + network suites, any OS
#     ./test.sh seed         # the shipped seed bundle verifies (any OS)
#     ./test.sh extension    # JavaScript suites + a packaged-zip check (macOS)
#     ./test.sh macos        # xcodebuild test (macOS)
#     ./test.sh release      # distribution settings and installer tests (macOS)
#     ./test.sh browser      # the scanner harness in headless Chrome (needs a
#                            # Chromium-based browser; CI always runs it)
#
# CI calls the named suites on the runner that can run them; a developer calls
# it bare. Suites never skip themselves quietly: asking for one whose toolchain
# is missing is a failure, because "the tests passed" must mean the tests ran.
# `browser` is the one suite not in the bare run, because it needs a browser
# this repo does not otherwise depend on — ask for it by name.
set -euo pipefail
cd "$(dirname "$0")"

SCRATCH="${RUNNER_TEMP:-${TMPDIR:-/tmp}}"

suite_python() {
    echo "── python: test-runner regression checks"
    python3 -m unittest discover -s script -p 'test_*.py'
    python3 blocklist/web_protection.py --check
    # Discovery, not the direct runners: `test_terms.py` once had its
    # `unittest.main()` above its last class, and the documented runner
    # silently ran 22 of 29 tests for weeks. Discovery finds every
    # `test_*.py` and every class in it, whatever the file's layout.
    echo "── python: blocklist"
    ( cd blocklist && python3 -m unittest discover -s . -p 'test_*.py' -v 2>&1 | tail -4 \
        | tee /dev/stderr | grep -q "^OK" )
    echo "── python: profile"
    ( cd profile && python3 -m unittest discover -s . -p 'test_*.py' -v 2>&1 | tail -4 \
        | tee /dev/stderr | grep -q "^OK" )
    echo "── python: verified network publication"
    python3 -m unittest discover -s network -p 'test_*.py'
    echo "── python: mobile Safari rules"
    python3 -m unittest discover -s ios -p 'test_*.py'
}

suite_seed() {
    echo "── seed bundle"
    python3 blocklist/seed.py verify
}

suite_release() {
    echo "── macos: release configuration and installer"
    python3 -m unittest discover -s macos/release -p 'test_*.py'
    bash macos/release/release.sh --check
    bash -n macos/release/release.sh macos/release/installer/preinstall \
        macos/release/installer/postinstall macos/install_native_host.sh macos/install.sh \
        script/build_and_run.sh
}

suite_extension() {
    echo "── extension: unit"
    extension/test/run.sh
    echo "── extension: package"
    extension/package.sh --out "$SCRATCH/hisn-extension-check.zip" >/dev/null
    python3 extension/check_package.py "$SCRATCH/hisn-extension-check.zip"
    # The evaluation corpus is a regression gate for one thing only: no
    # benign page — medicine, scholarship, news, forms — may be blocked by
    # the shipped vocabulary. Coverage is reported, not gated.
    echo "── extension: evaluation corpus"
    extension/eval/run.sh | tail -3
}

# Run one check and pass its output through. On GitHub Actions a failure also
# becomes an annotation: anyone can read those on the run page, while the log
# itself is shown only to a signed-in viewer — the first CI failure of this
# suite was a bare "exit code 1" to everyone else.
check() {
    local title="$1"; shift
    local log rc=0
    log="$(mktemp "$SCRATCH/hisn-check.XXXXXX")"
    "$@" 2>&1 | tee "$log" || rc=$?
    if [ "$rc" -ne 0 ] && [ "${GITHUB_ACTIONS:-}" = "true" ]; then
        # Workflow commands take one line: % and newlines are escaped.
        local body
        body="$( { grep -iE 'fail|no verdict|error|browser|DOM bytes|^    ' "$log" || tail -15 "$log"; } | head -40 \
                 | sed 's/%/%25/g' | awk '{ printf "%s%%0A", $0 }')"
        echo "::error title=$title::$body"
    fi
    rm -f "$log"
    return "$rc"
}

suite_browser() {
    # Every check runs even after one fails, so one CI run reports them all.
    local rc=0
    echo "── extension: scanner harness in a real browser"
    check "browser: scanner harness" extension/test/browser/run.sh || rc=1
    echo "── extension: list signature verification (WebCrypto Ed25519)"
    check "browser: signature verification" extension/test/browser/run.sh verify.html || rc=1
    echo "── extension: the settings page, English and Arabic"
    check "browser: settings page" extension/test/browser/run.sh settings.html || rc=1
    echo "── extension: the popup, English and Arabic"
    check "browser: popup" extension/test/browser/run.sh popup.html || rc=1
    echo "── extension: the block page, English and Arabic"
    check "browser: block page" extension/test/browser/run.sh blocked.html || rc=1
    echo "── extension: network rules in a real browser (SafeSearch, blocklist)"
    check "browser: network rules" extension/test/browser/dnr.sh || rc=1
    echo "── extension: the scanner against evasive pages in a real browser"
    check "browser: scanner on live pages" extension/test/browser/scan_live.sh || rc=1
    return "$rc"
}

suite_macos() {
    echo "── macos: xcodebuild test"
    # No team is needed to compile or test; CODE_SIGNING_ALLOWED=NO makes that
    # explicit so a runner without a certificate does not fail on signing.
    # -testLanguage en: the test host is the app, and the app may have been
    # switched to Arabic on this Mac; the assertions read English.
    ( cd macos
      set +e
      xcodebuild -scheme Hisn -configuration Debug test \
        -destination 'platform=macOS' -testLanguage en -testRegion US \
        CODE_SIGNING_ALLOWED=NO \
        ${XCODEBUILD_FLAGS:-} 2>&1 | tee "$SCRATCH/xcodebuild-test.log" \
        | grep -E "^/.*error:|Executed [0-9]+ tests|\*\* TEST"
      pipeline_status=("${PIPESTATUS[@]}")
      set -e
      # A success banner cannot override a failed build or lost test log.
      if [ "${pipeline_status[0]}" -ne 0 ]; then exit "${pipeline_status[0]}"; fi
      if [ "${pipeline_status[1]}" -ne 0 ]; then exit "${pipeline_status[1]}"; fi
      grep -q "\*\* TEST SUCCEEDED \*\*" "$SCRATCH/xcodebuild-test.log" )
    echo "── macos: every string has Arabic"
    # The export reads what the compiled code uses, not the catalog, so a new
    # string without a translation fails here rather than appearing in
    # English inside an Arabic window.
    rm -rf "$SCRATCH/hisn-loc"
    ( cd macos && xcodebuild -exportLocalizations -scheme Hisn \
        -localizationPath "$SCRATCH/hisn-loc" -exportLanguage ar \
        CODE_SIGNING_ALLOWED=NO ${XCODEBUILD_FLAGS:-} > "$SCRATCH/xcodebuild-loc.log" 2>&1 ) \
        || { tail -20 "$SCRATCH/xcodebuild-loc.log"; return 1; }
    python3 macos/check_localization.py \
        --xliff "$SCRATCH/hisn-loc/ar.xcloc/Localized Contents/ar.xliff"
}

suite_ios() {
    echo "── ios: signed-input and Safari rule tests"
    python3 -m unittest discover -s ios -p 'test_*.py'
    local destination="${HISN_IOS_DESTINATION:-}"
    if [ -z "$destination" ]; then
        destination="$(xcrun simctl list devices available -j | python3 -c '
import json, sys
devices = [device for group in json.load(sys.stdin)["devices"].values() for device in group
           if device.get("isAvailable") and device["name"].startswith("iPhone")]
if not devices: raise SystemExit("No available iPhone simulator; install an iOS runtime in Xcode")
devices.sort(key=lambda device: device["state"] != "Booted")
print("platform=iOS Simulator,id=" + devices[0]["udid"])
')"
    fi
    echo "── ios: hosted app tests"
    xcodebuild test -project ios/HisnMobile.xcodeproj -scheme HisnMobile \
        -destination "$destination" CODE_SIGNING_ALLOWED=NO \
        HISN_PYTHON="${HISN_PYTHON:-$(command -v python3)}" \
        ${XCODEBUILD_FLAGS:-}
}

if [ $# -eq 0 ]; then
    set -- python seed extension release macos ios
fi
for s in "$@"; do
    case "$s" in
        python|seed|extension|release|macos|browser|ios) "suite_$s" ;;
        *) echo "unknown suite: $s (python|seed|extension|release|macos|browser|ios)" >&2; exit 2 ;;
    esac
done
echo
echo "all requested suites passed: $*"
