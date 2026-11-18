#!/usr/bin/env python3
"""Black-box check of Hisn's blocking on a connected phone or emulator, any build.

Drives only harmless fixtures (the GuardFixtureActivity test screens, a Chrome Incognito
tab on about:blank, YouTube's Shorts link) and watches what Android reports: which activity
is in front, whether Hisn's cover window is up, and the secure display settings. It never
changes Hisn's rules, grants a permission or turns on accessibility: the checks to test must
already be on in Hisn, and a case whose feature is off fails with the reason instead of
passing quietly.

    python3 android/test-live-checks.py --serial SERIAL [--only NAME ...]
"""
import argparse
import os
import re
import subprocess
import sys
import time

HISN = "app.hisn.android"
FIXTURES = ("app.hisn.android.test", "app.hisn.android.visualcheck.test")
RESTRICTION = HISN + "/.RestrictionActivity"
ADULT_APPS = ("com.hdvideo.player.offline", "com.lite.tera.iplayerbox", "iplayer.link.mdisk.terabox.com",
              "iplayer.video.mdisk.terabox.com", "iplayer.and.new.com")

parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
parser.add_argument("--serial", required=True)
parser.add_argument("--adb", default=os.environ.get("ADB", os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")))
parser.add_argument("--only", nargs="*", help="Run only these cases")
args = parser.parse_args()


def adb(*parts, timeout=30):
    return subprocess.run([args.adb, "-s", args.serial, *parts], capture_output=True, text=True,
                          timeout=timeout).stdout


def front():
    out = adb("shell", "dumpsys", "activity", "activities")
    m = re.search(r"topResumedActivity=ActivityRecord\{\S+ u0 (\S+)", out)
    return m.group(1) if m else ""


def cover_up():
    windows = re.split(r"\n  Window #", adb("shell", "dumpsys", "window", "windows"))
    return any("ty=ACCESSIBILITY_OVERLAY" in w and HISN + "}" in w.split("\n")[0] for w in windows)


def home():
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    time.sleep(1.8)  # Hisn's intervention cooldown, so each case starts fresh.


def installed(package):
    return adb("shell", "pm", "path", "--user", "0", package).startswith("package:")


def watch(seconds, until):
    """Polls front()/cover_up() until `until(front, cover)` is true; returns the last pair."""
    end = time.monotonic() + seconds
    state = ("", False)
    while time.monotonic() < end:
        state = (front(), cover_up())
        if until(*state):
            return state, True
        time.sleep(0.4)
    return state, False


FIXTURE = next((f for f in FIXTURES if installed(f)), None)


def fixture(mode):
    adb("shell", "am", "start", "--user", "0", "-W", "-n", FIXTURE + "/app.hisn.android.GuardFixtureActivity",
        "-f", "0x10008000", "--es", "mode", mode)


def blocked(top, cover):
    # Hisn shows its restriction screen, or (inside the rest period after a block, or within its
    # cooldown) only presses Home: either way the fixture is no longer in front.
    return top.startswith(RESTRICTION) or "GuardFixtureActivity" not in top


def expect_blocked(mode, seconds=6):
    def run():
        fixture(mode)
        time.sleep(0.6)
        (top, cover), ok = watch(seconds, lambda t, c: blocked(t, c))
        return ok, ("restriction screen" if top.startswith(RESTRICTION) else "sent home") if ok else "still open: " + top
    return run


def expect_allowed(mode, seconds=5):
    def run():
        fixture(mode)
        time.sleep(seconds)
        top, cover = front(), cover_up()
        ok = "GuardFixtureActivity" in top and not cover
        return ok, "stayed open" if ok else ("covered" if cover else "front: " + top)
    return run


def expect_cover(mode, seconds=6):
    def run():
        fixture(mode)
        (top, cover), ok = watch(seconds, lambda t, c: c)
        return ok, "cover shown" if ok else "no cover; front " + top
    return run


def ui_nodes():
    xml = ""
    for attempt in range(4):  # A playing video can keep uiautomator from finding an idle moment.
        adb("shell", "uiautomator", "dump", "/sdcard/hisn_check.xml")
        xml = adb("shell", "cat", "/sdcard/hisn_check.xml")
        adb("shell", "rm", "-f", "/sdcard/hisn_check.xml")
        if "<node" in xml:
            break
        time.sleep(0.7)
    nodes = []
    for m in re.finditer(r"<node [^>]*>", xml):
        n = m.group(0)
        get = lambda k: (re.search(k + r'="([^"]*)"', n) or [None, ""])[1]
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        nodes.append({"text": get("text"), "desc": get("content-desc"), "id": get("resource-id"),
                      "b": tuple(map(int, b.groups())) if b else None})
    return nodes


def tap_label(*labels):
    """Taps the first node whose text, description or view id (after the slash) is one of labels."""
    for node in ui_nodes():
        names = {node["text"].lower(), node["desc"].lower(), node["id"].split("/")[-1].lower()}
        if node["b"] and any(l.lower() in names for l in labels):
            b = node["b"]
            adb("shell", "input", "tap", str((b[0] + b[2]) // 2), str((b[1] + b[3]) // 2))
            return True
    return False


def tap_open_on_wait():
    """Taps Open on Hisn's wait screen. Its breathing animation can keep uiautomator from reading
    the screen, so after the countdown the button is tapped where Hisn draws it: centred, at the
    bottom of the screen."""
    for node in ui_nodes():
        if node["b"] and node["text"].startswith("Open") and "second" not in node["text"]:
            b = node["b"]
            adb("shell", "input", "tap", str((b[0] + b[2]) // 2), str((b[1] + b[3]) // 2))
            return
    size = re.search(r"(\d+)x(\d+)", adb("shell", "wm", "size").split("\n")[-2] if "Override" in adb("shell", "wm", "size") else adb("shell", "wm", "size"))
    width, height = int(size.group(1)), int(size.group(2))
    time.sleep(4)  # The countdown.
    if "OpenWaitActivity" in front():
        adb("shell", "input", "tap", str(width // 2), str(int(height * 0.933)))


def through_wait(package, seconds=20):
    """Hisn's 'wait before opening' screen: wait out the countdown and tap Open, as a person would."""
    # The app is briefly in front before Hisn's wait screen covers it, so it counts only once it
    # has stayed in front for two readings in a row.
    end, steady = time.monotonic() + seconds, 0
    while time.monotonic() < end:
        top = front()
        if package in top:
            steady += 1
            if steady >= 2:
                return True
        else:
            steady = 0
            if "OpenWaitActivity" in top:
                tap_open_on_wait()
        time.sleep(1)
    return False


def adult_app():
    present = [p for p in ADULT_APPS if installed(p)]
    if not present:
        return None, "no listed adult app installed to try (install one, e.g. the XPlayer clone)"
    adb("shell", "monkey", "-p", present[0], "-c", "android.intent.category.LAUNCHER", "1")
    (top, cover), ok = watch(8, lambda t, c: t.startswith(RESTRICTION))
    return ok, (present[0] + " blocked") if ok else (present[0] + " not blocked; front " + top)


def adult_named(package, what):
    def run():
        if not installed(package):
            return None, package + " is not installed (an empty dummy app with that name, built for this test)"
        adb("shell", "monkey", "-p", package, "-c", "android.intent.category.LAUNCHER", "1")
        (top, cover), ok = watch(8, lambda t, c: t.startswith(RESTRICTION) or (t and package not in t and "OpenWait" not in t))
        return ok, what + " blocked" if ok else what + " not blocked; front " + top
    return run


def gray_screen():
    enabled = adb("shell", "settings", "get", "secure", "accessibility_display_daltonizer_enabled").strip()
    mode = adb("shell", "settings", "get", "secure", "accessibility_display_daltonizer").strip()
    granted = "WRITE_SECURE_SETTINGS: granted=true" in adb("shell", "dumpsys", "package", HISN)
    ok = enabled == "1" and mode == "0"
    why = "screen is gray" if ok else ("Android has not given Hisn permission; run: adb shell pm grant "
                                       + HISN + " android.permission.WRITE_SECURE_SETTINGS"
                                       if not granted else "gray is off (is Gray screen on in Hisn?)")
    return ok, why


def private_tab():
    if not installed("com.android.chrome"):
        return None, "Chrome is not installed"
    adb("shell", "monkey", "-p", "com.android.chrome", "-c", "android.intent.category.LAUNCHER", "1")
    if not through_wait("com.android.chrome"):
        return None, "Chrome did not come to the front"
    time.sleep(2)
    if not tap_label("menu_button", "More options"):
        # A loading page can keep uiautomator from reading Chrome: the menu is top-right.
        width, height = map(int, re.findall(r"(\d+)x(\d+)", adb("shell", "wm", "size"))[-1])
        adb("shell", "input", "tap", str(int(width * 0.94)), str(int(height * 0.0735)))
    time.sleep(1.2)
    if not tap_label("New Incognito tab", "Neuer Inkognito-Tab", "New incognito tab"):
        return None, "Chrome's menu has no New Incognito tab item"
    time.sleep(1)
    # Hisn presses Back on an Incognito tab; the marker disappears or Chrome leaves the front.
    def gone(top, cover):
        if "com.android.chrome" not in top:
            return True
        dump = adb("shell", "uiautomator", "dump", "/sdcard/hisn_check.xml")
        xml = adb("shell", "cat", "/sdcard/hisn_check.xml")
        adb("shell", "rm", "-f", "/sdcard/hisn_check.xml")
        return "ERROR" not in dump and "incognito" not in xml.lower()
    _, ok = watch(10, gone)
    return ok, "Incognito tab closed" if ok else "Incognito tab stayed open"


def short_video():
    if not installed("com.google.android.youtube"):
        return None, "YouTube is not installed"
    adb("shell", "monkey", "-p", "com.google.android.youtube", "-c", "android.intent.category.LAUNCHER", "1")
    if not through_wait("com.google.android.youtube"):
        return None, "YouTube did not open past Hisn's wait screen"
    time.sleep(3)
    # The Shorts tab, second in YouTube's bottom bar. A playing feed keeps uiautomator from
    # reading YouTube, so it is tapped by its place in the bar.
    width, height = map(int, re.findall(r"(\d+)x(\d+)", adb("shell", "wm", "size"))[-1])
    adb("shell", "input", "tap", str(int(width * 0.373)), str(int(height * 0.942)))
    (top, cover), ok = watch(8, lambda t, c: t.startswith(RESTRICTION))
    return ok, "Shorts left" if ok else "Shorts stayed open; front " + top


# Every "allowed" case runs before the first block: after a block, Hisn keeps the blocked app
# (here the fixture) closed for its rest period, so an allowed page after it could not open.
def shorts_in_chrome():
    if not installed("com.android.chrome"):
        return None, "Chrome is not installed"
    # Instagram's Reels page: it opens in Chrome when the Instagram app is not installed.
    adb("shell", "am", "start", "-a", "android.intent.action.VIEW", "-d", "https://www.instagram.com/reels/",
        "-p", "com.android.chrome")
    if not through_wait("com.android.chrome"):
        return None, "Chrome did not come to the front"
    def left(top, cover):
        if top.startswith(RESTRICTION) or "com.android.chrome" not in top:
            return True
        return not any("reels" in n["text"] for n in ui_nodes() if n["id"].endswith("url_bar"))
    _, ok = watch(10, left)
    return ok, "Reels page left" if ok else "Reels page stayed open in Chrome"


def other_browser():
    others = [p for p in ("com.sec.android.app.sbrowser", "org.mozilla.firefox", "com.opera.browser",
                          "com.brave.browser", "com.duckduckgo.mobile.android", "com.microsoft.emmx",
                          "com.vivaldi.browser", "com.kiwibrowser.browser") if installed(p)]
    if not others:
        return None, "no browser other than Chrome is installed to try"
    adb("shell", "monkey", "-p", others[0], "-c", "android.intent.category.LAUNCHER", "1")
    (top, cover), ok = watch(8, lambda t, c: t.startswith(RESTRICTION) or (others[0] not in t and "OpenWait" not in t and t))
    return ok, (others[0] + " blocked") if ok else (others[0] + " stayed open")


CASES = [
    ("ordinary page allowed", "words", expect_allowed("ordinary")),
    ("health education allowed", "words", expect_allowed("medical")),
    ("porn-blocker ad allowed", "words", expect_allowed("protective")),
    ("recovery resources allowed", "words", expect_allowed("recovery")),
    ("password field not read", "words", expect_allowed("password")),
    ("normal image allowed", "images", expect_allowed("visual-normal", 8)),
    # The fixture neither opens web pages nor plays video, like a bank or TAN app: while every
    # app is read, its private window is left usable rather than covered.
    ("private non-media window usable", "images", expect_allowed("visual-secure", 8)),
    ("English adult text blocked", "words", expect_blocked("adult-en")),
    ("Arabic adult text blocked", "words", expect_blocked("adult-ar")),
    ("dotted spelling blocked", "words", expect_blocked("adult-compact-en")),
    ("built-in adult app blocked", "apps", adult_app),
    ("app of an adult website blocked", "apps", adult_named("com.xvideos.hisntest", "com.xvideos.* dummy")),
    ("app with an adult name blocked", "apps", adult_named("com.example.hisnnametest", "'Porn Videos' dummy")),
    ("Incognito tab closed", "private", private_tab),
    ("YouTube Shorts left", "shorts", short_video),
    ("Reels in Chrome left", "shorts", shorts_in_chrome),
    ("other browser blocked", "browsers", other_browser),
    ("gray screen applied", "gray", gray_screen),
]

if not installed(HISN):
    sys.exit("Hisn is not installed on " + args.serial)
if HISN + "/" not in adb("shell", "settings", "get", "secure", "enabled_accessibility_services"):
    sys.exit("Hisn's accessibility service is off; turn it on in Android settings first")
if FIXTURE is None:
    sys.exit("No Hisn test fixture installed (" + " or ".join(FIXTURES) + ")")
print("Device", args.serial, "· fixture", FIXTURE, flush=True)

# A block from an earlier run keeps the fixture closed for Hisn's rest period (5 minutes by
# default). Wait for it to end rather than reading that as a wrong block.
waited = 0
while True:
    home()
    fixture("ordinary")
    time.sleep(3)
    if "GuardFixtureActivity" in front():
        break
    if waited >= 600:
        sys.exit("The test screen stayed closed for 10 minutes; is it on Hisn's block list?")
    if waited == 0:
        print("The test screen is still in Hisn's rest period from an earlier block; waiting…", flush=True)
    time.sleep(20)
    waited += 23

results = []
for name, group, run in CASES:
    if args.only and name not in args.only and group not in args.only:
        continue
    home()
    for attempt in range(2):
        try:
            ok, why = run()
        except Exception as error:  # A broken case is a failure, never a skip.
            ok, why = False, type(error).__name__ + ": " + str(error)
        # NOT RUN means the runner could not drive the phone (a slow app, a screen it could not
        # read): try once more. A real FAIL is reported as it is.
        if ok is not None:
            break
        home()
    label = "PASS" if ok else "NOT RUN" if ok is None else "FAIL"
    results.append((label, name, why))
    print(f"{label:8} {name:30} {why}", flush=True)
home()

failed = [r for r in results if r[0] != "PASS"]
print(f"\n{len(results) - len(failed)} of {len(results)} passed", flush=True)
sys.exit(1 if failed else 0)
