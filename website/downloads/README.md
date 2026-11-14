# Download staging

This folder is where the signed APK and its checksum file are staged for a
deploy. The binary is **not committed** (see `.gitignore`); build it from
`release.json` with:

```sh
bash website/stage-downloads.sh
```

The script refuses to stage a file whose size or SHA-256 differs from
`release.json`, copies it to the stable public name `hisn-android.apk`, and
writes `SHA256SUMS` next to it. `bash website/build-site.sh`
stages the download and copies the whole static site to
`dist/hisn-website-<date>/` for hosting.
