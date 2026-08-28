# Releasing Tacklebox

How to cut a signed release of the Android app. Debug builds need none of this —
they're built and run straight from Android Studio or `./gradlew :app:assembleDebug`.

## Overview

- **Debug CI** (`.github/workflows/android.yml`) builds a debug APK on every push/PR to `main`.
- **Release CI** (`.github/workflows/release.yml`) builds a **signed** APK + AAB on every
  `v*` tag and attaches them to that tag's GitHub Release.
- Signing material is never committed. It comes from an untracked `keystore.properties`
  (local builds) or four GitHub Secrets (CI). With neither present the release build is
  simply left unsigned, so open/PR builds still work.

## One-time setup

### 1. Generate the upload keystore

```bash
keytool -genkeypair -v -keystore ~/tacklebox-release.jks -alias tacklebox \
  -keyalg RSA -keysize 2048 -validity 10000
```

**Keep the `.jks` file and both passwords safe and backed up.** If you lose them you cannot
publish updates under the same key. Store them in a password manager, not in this repo.

### 2. Add the GitHub Secrets (for CI signing)

Run from the repo root:

```bash
gh secret set KEYSTORE_BASE64  --repo Stumyatt76/tacklebox-android < <(base64 -i ~/tacklebox-release.jks)
gh secret set KEYSTORE_PASSWORD --repo Stumyatt76/tacklebox-android   # prompts for the value
gh secret set KEY_ALIAS         --repo Stumyatt76/tacklebox-android --body tacklebox
gh secret set KEY_PASSWORD      --repo Stumyatt76/tacklebox-android   # prompts for the value
```

| Secret | Value |
|--------|-------|
| `KEYSTORE_BASE64` | the `.jks` file, base64-encoded |
| `KEYSTORE_PASSWORD` | the keystore (store) password |
| `KEY_ALIAS` | the key alias (`tacklebox` above) |
| `KEY_PASSWORD` | the key password |

## Cutting a release

1. Bump the version in `app/build.gradle.kts`:
   - `versionName` — the human-facing string (e.g. `"1.2"`).
   - `versionCode` — the integer Play uses to order updates; **must increase** every upload.
2. Commit and push to `main`.
3. Tag and push:
   ```bash
   git tag -a v1.2 -m "Tacklebox 1.2" && git push origin v1.2
   ```
4. The **Release** workflow builds the signed APK + AAB and attaches them to the
   GitHub Release for `v1.2` (creating the Release if it doesn't exist). Watch it with:
   ```bash
   gh run watch "$(gh run list --workflow=release.yml --limit 1 --json databaseId -q '.[0].databaseId')"
   ```

## Building a signed release locally

Copy the template and fill it in (the file is gitignored):

```bash
cp keystore.properties.example keystore.properties
# edit keystore.properties: storeFile / storePassword / keyAlias / keyPassword
./gradlew :app:assembleRelease   # -> app/build/outputs/apk/release/app-release.apk
./gradlew :app:bundleRelease     # -> app/build/outputs/bundle/release/app-release.aab
```

`storeFile` is resolved relative to the `app/` module, or may be an absolute path.

## Verifying an APK

```bash
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --print-certs app/build/outputs/apk/release/app-release.apk
"$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging app/build/outputs/apk/release/app-release.apk | grep package:
```

## Play App Signing

If you distribute through Google Play with **Play App Signing** enabled, Google holds the
final app-signing key and re-signs your uploaded AAB. Your keystore above then acts as the
**upload key** — still important, but resettable via Play support if lost. Without Play App
Signing, the keystore above *is* the app-signing key and losing it is unrecoverable.

## Notes

- Release builds run R8 (`isMinifyEnabled`) and resource shrinking. Keep rules live in
  `app/proguard-rules.pro` — extend them if you add libraries that rely on reflection.
- The build requires **JDK 21** (CI uses Temurin 21); the app targets Java 21 bytecode.
