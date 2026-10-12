# Hisn 1.1.2 — interface and visual-check improvements

The Chromium extension, Mac app and universal iPhone/iPad app use version
1.1.2; native bundles use build 3. This is a source/build version, not proof
that a device or browser has installed or enabled it.

## Use the new interface

Open Hisn in the browser toolbar, then **Manage protection → Visual checks**.
The popup is wider, with a full-page action that remains reachable in compact
viewports. The full-page visual controls are no longer mixed into text settings.
Automatic light/dark colors and English/Arabic direction are retained.

Adult-media checking and person blur are independent. Choose Off, Women, Men or
Everyone in the person-blur cards. Women/men are estimates of facial appearance,
not identity or adult-content labels. Everyone means any detected visible face,
not full-body detection. Matching media is blurred as a whole. These optional
checks remain off by default. Mac-connected commitments retain their existing
cannot-weaken rules; this release does not give standalone browsers a native lock.

The Mac Settings page explains where browser-owned visual preferences live;
it does not claim to read or verify them. iPhone/iPad have clearer Safari setup,
numbered permission steps, expandable diagnostics, and matching blue actions.
Safari extension settings use roomy cards and touch-sized controls; failed or
stalled settings requests now offer recovery rather than indefinite disabled UI.

## Detection changes and limits

The face detector now receives aspect-preserving square padding for portrait and
wide media. Boxes and appearance outputs are validated. Optional face-model
errors cannot discard an already-positive adult-classifier verdict. Lazy media,
picture/srcset replacements, distinct video streams and video resizing are
rechecked. Unchanged images are periodically resampled within the bounded queue;
this improves animated-image opportunities but is not every-frame detection.

No models were added, no media uploads were introduced, and confidence thresholds
were not reduced. Research checked preprocessing against Human's implementation
and the bundled model contract. Real benign-fixture tests are not an adult-media
accuracy benchmark. Specific female mode still missed the recorded face fixture.

Checks apply only to permitted webpages and readable pixels. Cross-origin,
protected media, small/obscured faces and sampled moments can remain unverified.
Set the extension's site access to the sites you want protected; it cannot grant
itself permission. Normal videos remain available unless actually flagged.

On iPhone/iPad, enable **Hisn Text** and website access in Safari extension
settings. The four domain-blocker extensions remain separate. Safari visual
preferences do not inherit the Mac app's commitment enforcement. This is not a
universal extension for every iOS browser or a scanner inside native apps.
Physical-device Safari execution still needs verification.

## Updating and recovery

Reload Hisn on each Chromium browser's Extensions page and confirm **1.1.2**.
The extension already loaded from the repository's `extension` directory can be
reloaded there. If browser-generated metadata prevents loading, use a fresh
browser-specific copy; do not delete an existing folder or reset its preferences:

```sh
python3 extension/prepare_unpacked.py --out dist/hisn-unpacked-helium-1.1.2
python3 extension/prepare_unpacked.py --out dist/hisn-unpacked-chatgpt-1.1.2
```

For the native Mac update, use the tested bundle with the existing safe installer
and ordinary administrator confirmation. It preserves the protected account,
login-agent bytes and an old-bundle backup. Local ad-hoc signing does not enable
the system network filter or provide Developer ID notarization.

This release does not make extensions undeletable or guarantee absolute coverage.
The separate signed-list publishing job still requires the original private key
in GitHub's `list-signing` environment; no key rotation or trust downgrade is part
of this release. See `design-qa.md` for recorded verification and remaining gates.
