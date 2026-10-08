# Imported laptop blocklist

`stevenblack-laptop.txt` preserves the StevenBlack hosts list with the porn extension that was installed in `/etc/hosts` on this laptop (upstream header dated 2026-09-04). It includes ads and trackers in addition to adult sites; its upstream project is https://github.com/StevenBlack/hosts (MIT license).

The `stevenblack-laptop` core source in `../sources.json` includes this snapshot in both the signed macOS seed and the browser rules. Local source paths resolve relative to the source configuration. The usual domain normalization, subdomain consolidation and essential-service safety exclusions still apply. Rebuild with `python3 blocklist/seed.py sync --build --sign-key keys/blocklist_ed25519.pem` from the repository root after editing the snapshot.

The snapshot belongs inside Hisn. Do not copy it back to `/etc/hosts`: DNS entries apply independently of Hisn's protection state. The migration retained a backup at `/etc/hosts.before-hisn-list-migration`.
