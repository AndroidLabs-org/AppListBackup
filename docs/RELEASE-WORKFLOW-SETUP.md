# Setting up `.github/workflows/release.yml`

## 1. Add the file

Add `.github/workflows/release.yml` (attached) to the GitHub repo — GitHub
draft-only rule, so this needs a maintainer's own push, not mine.

## 2. Add the four secrets

GitHub repo → **Settings → Secrets and variables → Actions → New repository
secret**:

| Secret name | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | the keystore file, base64-encoded (see below) |
| `RELEASE_STORE_PASSWORD` | the store password |
| `RELEASE_KEY_ALIAS` | the key alias |
| `RELEASE_KEY_PASSWORD` | the key password |

To base64-encode the keystore, on macOS:

```
base64 -i your.keystore | pbcopy
```

Paste the clipboard contents as the value of `RELEASE_KEYSTORE_BASE64`. Never
commit the raw `.keystore`/`.jks` file itself anywhere, base64 or not.

## 3. Cut a release the same way as before

Before tagging: add `metadata/en-US/changelogs/<versionCode>.txt` (the
number, not the version name — check `versionCode` in
`app/build.gradle.kts`) if it isn't already there from the same MR that
bumped the version. The workflow uses this file as the GitHub Release's
notes, not just F-Droid's changelog — one file, both places. It fails the
build if that file is missing rather than publishing with empty notes,
which is what every release before this one shipped with.

Push a tag matching the existing pattern (`v2.0.3`, etc.) — same convention
every release from v1.0 through v2.0.2 already used. The workflow triggers
automatically, builds, signs, verifies the signature matches the known
fingerprint (`6b544921...1022d81b64`), and attaches the APK — with real
release notes this time — to that tag's GitHub Release. F-Droid picks it up
from there on its own schedule.

## What happens if the signature doesn't match

The workflow fails before anything is published — the verification step
compares the built APK's certificate fingerprint against the one already
confirmed to match 1.0.1 through 2.0.2, and refuses to upload if they don't
match. Better to catch that here than have F-Droid's own
`AllowedAPKSigningKeys` check reject it later.
