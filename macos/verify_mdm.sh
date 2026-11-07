#!/bin/bash
#
# Verify the MDM layer on this Mac. Read-only; installs nothing.
#
#     macos/verify_mdm.sh [--strict]
#
# Exit 0  MDM-enrolled (user approved) AND the Hisn managed payload is forced.
#         With --strict, every profile layer below must also be present.
# Exit 1  a required fact is false: not enrolled, not user-approved, Hisn is
#         not under organization management, or --strict found a missing layer.
# Exit 2  the Mac could not be asked.
#
# `profiles status` reports enrollment, not supervision. A DEP line is a
# supervision signal to confirm in the MDM console, not proof that
# ProhibitDisablement and other supervised-only keys are in force. The profile
# layer checks read the managed-preferences plists and the live system
# extension list; they are evidence that a payload was delivered and an
# extension loaded, not proof that a browser accepted every policy.
set -uo pipefail
export LC_ALL=C

PROFILES=/usr/bin/profiles
SYSTEMEXTENSIONSCTL=/usr/bin/systemextensionsctl

STRICT=0
case "${1:-}" in
    -h|--help)
        sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'
        exit 0
        ;;
    --strict) STRICT=1 ;;
    "") ;;
    *)
        printf 'unknown argument: %s\n' "$1" >&2
        exit 2
        ;;
esac

status_output=$("$PROFILES" status -type enrollment 2>/dev/null)
status_rc=$?
if [ "$status_rc" -ne 0 ]; then
    printf 'UNKNOWN: could not read enrollment state (%s exited %s).\n' \
        "$PROFILES" "$status_rc" >&2
    exit 2
fi

# Parse the command's documented output. Unknown lines are ignored; an
# unrecognized answer is "unknown", never "not enrolled".
parsed=$(python3 - "$status_output" <<'STATUS'
import sys

dep = None
dep_invalid = False
mdm = None
for line in sys.argv[1].splitlines():
    key, separator, value = line.partition(":")
    if not separator:
        continue
    key = key.strip()
    value = value.strip().lower()
    if key == "Enrolled via DEP":
        dep_invalid = value not in ("yes", "no")
        dep = {"yes": True, "no": False}.get(value, None)
    elif key == "MDM enrollment":
        mdm = value

if dep_invalid or mdm is None:
    state = "unknown"
elif mdm == "no":
    # DEP without MDM enrollment is incomplete, not evidence of management.
    state = "none" if dep is not True else "unknown"
elif mdm == "yes (user approved)":
    state = "approved"
elif mdm == "yes":
    # DEP implies an approved enrollment; "DEP yes, approved no" is
    # contradictory, so do not present it as usable.
    state = "unapproved" if dep is not True else "unknown"
else:
    state = "unknown"

dep_out = "yes" if dep is True else "no" if dep is False else "unknown"
print(f"{dep_out}\t{state}")
STATUS
)

IFS=$'\t' read -r dep_state mdm_state <<< "$parsed"
: "${dep_state:=unknown}"
: "${mdm_state:=unknown}"

printf 'Device management\n'
printf '  Enrolled via DEP: %s\n' "$dep_state"
case "$mdm_state" in
    approved)   printf '  MDM enrollment: yes (user approved)\n' ;;
    unapproved) printf '  MDM enrollment: yes (not user approved)\n' ;;
    none)       printf '  MDM enrollment: no\n' ;;
    *)          printf '  MDM enrollment: unknown\n' ;;
esac

# Read the forced layer directly. `defaults read app.hisn.Hisn` would also show
# a value the person wrote themselves, which is exactly what the app's
# forced-layer check exists to reject. The managed-preferences plists are the
# profile's own output; a missing file is "not delivered", not "false".
facts=$(python3 - "$USER" "${HISN_MANAGED_PREFERENCES_ROOT:-/Library/Managed Preferences}" <<'LAYERS'
import plistlib
import sys
from pathlib import Path

user = sys.argv[1]
root = Path(sys.argv[2])

def load(domain):
    for path in (root / user / f"{domain}.plist", root / f"{domain}.plist"):
        try:
            with path.open("rb") as handle:
                return path, plistlib.load(handle)
        except Exception:
            continue
    return None, None

def yn(value):
    return "yes" if value is True else "no" if value is False else "unknown"

hisn_path, hisn = load("app.hisn.Hisn")
hisn = hisn or {}
_, access = load("com.apple.applicationaccess")
access = access or {}
_, dns_payload = load("com.apple.dnsSettings.managed")
dns = (dns_payload or {}).get("DNSSettings") or {}
_, chrome = load("com.google.Chrome")
chrome = chrome or {}
_, firefox = load("org.mozilla.firefox")
firefox = firefox or {}

relay = access.get("allowCloudPrivateRelay")
erase = access.get("allowEraseContentAndSettings")
version = hisn.get("HisnProfileVersion")
print("managed_path=" + (str(hisn_path) if hisn_path else ""))
print("managed=" + yn(hisn.get("HisnManaged")))
print("organization=" + str(hisn.get("OrganizationName") or ""))
print("version=" + (str(version) if isinstance(version, int) else ""))
print("filter_declared=" + yn(hisn.get("HisnFilterManaged")))
print("dns=" + yn(bool(dns.get("DNSProtocol")
                       and (dns.get("ServerURL") or dns.get("ServerAddresses")))))
print("dns_prohibit=" + yn(dns.get("ProhibitDisablement")))
print("relay=" + ("off" if relay is False else "on" if relay is True else "unknown"))
print("erase=" + ("off" if erase is False else "on" if erase is True else "unknown"))
print("chrome=" + yn(chrome.get("DnsOverHttpsMode") == "off"
                     and chrome.get("IncognitoModeAvailability") == 1))
print("firefox=" + yn(firefox.get("EnterprisePoliciesEnabled") is True))
LAYERS
)

fact() {
    printf '%s\n' "$facts" | sed -n "s/^$1=//p" | head -1
}
managed_path=$(fact managed_path)
managed=$(fact managed)
organization=$(fact organization)
profile_version=$(fact version)
filter_declared=$(fact filter_declared)
dns_layer=$(fact dns)
dns_prohibit=$(fact dns_prohibit)
relay=$(fact relay)
erase=$(fact erase)
chrome_layer=$(fact chrome)
firefox_layer=$(fact firefox)
: "${managed:=unknown}"
: "${filter_declared:=unknown}"
: "${dns_layer:=unknown}"
: "${dns_prohibit:=unknown}"
: "${relay:=unknown}"
: "${erase:=unknown}"
: "${chrome_layer:=unknown}"
: "${firefox_layer:=unknown}"

case "$managed" in
    yes) printf '  Hisn managed payload: forced (%s)\n' "$managed_path" ;;
    no)  printf '  Hisn managed payload: present but false (%s)\n' "$managed_path" ;;
    *)   printf '  Hisn managed payload: not installed in /Library/Managed Preferences\n' ;;
esac
if [ -n "$organization" ]; then
    printf '  Organization: %s\n' "$organization"
fi
printf '  Protection profile version: %s\n' "${profile_version:-not reported}"
printf '  Managed filter declared: %s\n' "$filter_declared"

printf 'Profile layers\n'
printf '  DNS payload: %s\n' "$dns_layer"
printf '  DNS disablement prohibited (supervised): %s\n' "$dns_prohibit"
printf '  iCloud Private Relay: %s\n' "$relay"
printf '  Erase All Content and Settings: %s\n' "$erase"
printf '  Chrome policy (DNS + private windows): %s\n' "$chrome_layer"
printf '  Firefox policy: %s\n' "$firefox_layer"

extension_lines=$("$SYSTEMEXTENSIONSCTL" list 2>/dev/null |
    grep -F 'app.hisn.Hisn.HisnFilter' || true)
extension_line=$(printf '%s\n' "$extension_lines" |
    grep -F '[activated enabled]' | head -1 || true)
if [ -z "$extension_line" ]; then
    extension_line=$(printf '%s\n' "$extension_lines" | head -1)
fi
case "$extension_line" in
    *'[activated enabled]'*)
        extension_state='enabled'
        printf '  Hisn filter extension: activated and enabled\n'
        ;;
    '')
        extension_state='missing'
        printf '  Hisn filter extension: not installed or not requested\n'
        ;;
    *)
        extension_state='inactive'
        printf '  Hisn filter extension: installed but not active (%s)\n' \
            "$(printf '%s' "$extension_line" | sed 's/.*\[\(.*\)\]/\1/')"
        ;;
esac

visible_profiles=$("$PROFILES" list -type configuration 2>/dev/null |
    sed -n 's/^.*profileIdentifier: \(app\.hisn\.profile.*\)$/    \1/p' || true)
printf '  Hisn profiles visible to this user:\n'
if [ -n "$visible_profiles" ]; then
    printf '%s\n' "$visible_profiles"
else
    printf '    none\n'
fi

resolvers=$(/usr/sbin/scutil --dns 2>/dev/null |
    awk '/nameserver\[[0-9]+\]/ {print $3}' | sort -u)
printf 'System DNS resolvers\n'
if [ -n "$resolvers" ]; then
    printf '%s\n' "$resolvers" | sed 's/^/  /'
else
    printf '  none reported\n'
fi

if [ "$(id -u)" -eq 0 ]; then
    printf 'Installed profiles (root)\n'
    "$PROFILES" show -type configuration 2>/dev/null |
        grep -E 'profileIdentifier: (app\.hisn\.profile|com\.apple\.mdm)' |
        sed 's/^.*attribute: /  /' || printf '  none matched\n'
else
    printf 'Installed profiles: run as root for the device-level list.\n'
fi

# The verdict. Enrollment without the managed payload means the Mac is
# managed but Hisn is not protected by that management.
case "$mdm_state" in
    approved) ;;
    unapproved)
        printf '\nPARTIAL: MDM enrollment is not user-approved. A user can remove the enrollment, and the MDM cannot apply every control.\n' >&2
        exit 1
        ;;
    none)
        printf '\nNOT MANAGED: this Mac is not enrolled in MDM. A profile installed by hand can be removed by a local administrator; use an MDM and a standard account for real removal resistance.\n' >&2
        exit 1
        ;;
    *)
        printf '\nUNKNOWN: the enrollment answer was not recognized. No verdict.\n' >&2
        exit 2
        ;;
esac

case "$managed" in
    1|true|TRUE|yes) ;;
    no)
        printf '\nNOT MANAGED: the Mac is enrolled in MDM, but HisnManaged is false. Deploy the Hisn MDM profile or fix the payload.\n' >&2
        exit 1
        ;;
    *)
        printf '\nPARTIAL: the Mac is enrolled in MDM, but the Hisn managed payload is not installed. Deploy the MDM profile to this Mac.\n' >&2
        exit 1
        ;;
esac

# The strongest claims need the layer that owns the filter, not just the flag
# that hides app controls. Each check below is something the management layer
# delivered; none of them proves that a browser accepted a policy.
strict_problems=""
add_problem() {
    strict_problems="${strict_problems}  - $1"$'\n'
}
[ "$profile_version" = "2" ] || add_problem "profile version is ${profile_version:-missing}, not 2"
[ "$filter_declared" = "yes" ] || add_problem "the profile does not declare the managed filter"
[ "$dns_layer" = "yes" ] || add_problem "no forced DNS payload"
[ "$dns_prohibit" = "yes" ] || add_problem "DNS disablement is not prohibited (supervised-only key)"
[ "$relay" = "off" ] || add_problem "iCloud Private Relay is not disabled"
[ "$extension_state" = "enabled" ] || add_problem "the Hisn filter extension is not activated"
[ "$erase" = "off" ] || add_problem "Erase All Content and Settings is not disabled"

if [ -n "$strict_problems" ]; then
    if [ "$STRICT" -eq 1 ]; then
        printf '\nSTRICT: not every managed protection layer is present.\n' >&2
        printf '%s' "$strict_problems" >&2
        printf 'Deploy the current profile and verify on a test Mac before claiming enforcement.\n' >&2
        exit 1
    fi
    printf '\nWARNING: some managed layers are missing or not reported:\n' >&2
    printf '%s' "$strict_problems" >&2
fi

printf '\nVERIFIED: MDM-enrolled and the Hisn managed payload is forced. '
if [ "$STRICT" -eq 1 ]; then
    printf 'Strict layer checks passed.\n'
else
    printf 'This does not prove supervision, filtering or effective policy; check the app layer and the profile on a test Mac, or run --strict for the full layer set.\n'
fi
exit 0
