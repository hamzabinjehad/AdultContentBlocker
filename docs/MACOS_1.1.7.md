# Mac 1.1.7: protection authority and lock-gated account changes

Development candidate, 2 November 2026. Unsigned and unnotarized. No account
split, standard-account run or notarized-package test was performed for this
change.

## What changed

The app now reads and shows **who can change Hisn at the macOS level**, instead
of asking the person to self-certify it:

- `MacAuthority` reads, read-only:
  - whether the daily account is in the `admin` group;
  - whether the visible `hisn_guardian` account exists, is an administrator and
    holds a secure token;
  - whether `/Library/LaunchDaemons/app.hisn.guardian.plist` is installed and
    loaded in the system domain.
- A missing answer is `nil`, never promoted to verified. The verified state
  requires **all three**: daily standard account, guardian admin with secure
  token, and loaded guardian daemon.
- Setup → Account split now shows a **Protection authority** card with each
  fact independently, plus the guardian setup and restore commands as copyable
  text. The app never creates the guardian account, never stores the password
  and never executes the commands.
- While a protection lock runs:
  - **Open Users & Groups** is disabled;
  - the guardian setup and restore commands are not offered;
  - the account-split confirmation cannot be changed.
  After the lock ends, the guardian commands are shown again. Restoring admin
  remains a guardian action: the daily user cannot do it alone in the app.
- Before a lock starts, the Lock page warns when the daily account is still an
  administrator. The warning is additional to the existing acknowledgement; the
  authority card is the actionable fix.
- **UI/UX pass.** The sidebar now carries a persistent protection/lock status
  footer and a lock badge; the window toolbar has a lock action and page
  subtitle; Overview has one primary action; duplicate lock CTAs were removed;
  Go/Protection menus provide ⌘1–⌘5, ⌘R and ⌘⇧L navigation. The window default
  is 980×700 with a 760×560 floor. See
  [the UI pass record](MACOS_UI_UX_2026-11-02.md).
- The account-split stage is complete only when the authority evidence is
  verified. A checked manual box can no longer make an administrator account
  look split.

This is deliberately **not** the app making itself an administrator. A hidden
or self-granted admin account is a backdoor; one bug or extracted secret would
own every install. The supported design is a visible guardian person plus a
root daemon, which is what the card verifies.

## Verification

On 2 November 2026:

- `./test.sh macos`: **508 tests**, 0 failures, including the new
  `MacAuthorityTests` and the account-split/lock-gate cases in
  `SetupChecklistTests`.
- Localization export: `561 strings, all with Arabic`.
- `python3 script/versions.py --check`: pass.
- The Xcode project was regenerated from `macos/generate_xcodeproj.py`; project
  diff is two new files and the 1.1.7/build 11 version bump.

## Not claimed

- No guardian account was created and no daily account was demoted in this pass.
  The scripts still need the owner's sudo password and a second person.
- No UI screenshot or accessibility pass for the new card.
- No signed/notarized package and no active system filter; those remain the
  owner/Apple-team gates.
- The card is evidence of account configuration, not proof that a person holds
  a password or that every removal route is closed. An administrator or
  guardian can still remove Hisn, and recovery/erase remains outside it.
