# Tacklebox Android — test notes

The canonical test pack lives in the **iOS** repo, `Stumyatt76/tacklebox-ios`, under `docs/testing/`:

| File | What it is |
|---|---|
| `TACKLEBOX_TEST_PACK_AI_BRIEF.md` | Paste-this-whole-file brief: mission, repos, environments, rules, reading order, both phases |
| `TACKLEBOX_TEST_MATRIX.md` | Feature inventory (iOS and Android side by side), the matrix rows, and the run log |
| `TACKLEBOX_DEFECTS_2026-09-07.md` | Defect register — 23 Android findings, `TB-A-nn` |
| `TACKLEBOX_PARITY_AND_BACKLOG.md` | iOS ↔ Android parity matrix and the improvement backlog |

The programme rule (brief §8) is that Android is tested **after** iOS, repeating the identical matrix rows, and
measured against `BRIEF.md` — *"Target feature parity with the iOS app described below."* Android rows go in the
run log with an `Android:` prefix.

## Build, install, run
```
JAVA_HOME=$(/usr/libexec/java_home -v 21) ANDROID_HOME=~/Library/Android/sdk \
  ./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain
adb -s $S install -r app/build/outputs/apk/debug/app-debug.apk
adb -s $S shell am start -n uk.co.tacklebox.app/.MainActivity
adb -s $S shell pm clear uk.co.tacklebox.app     # fastest route back to onboarding
```
Things that will otherwise cost you time:
- `:app:testDebugUnitTest` reports **`NO-SOURCE`**. That is not a build failure — there is no `app/src/test` or
  `app/src/androidTest` directory. It is the "no tests exist yet" signature.
- **`adb shell monkey -p uk.co.tacklebox.app … LAUNCHER` does not start this app.** Use `am start` with the
  explicit component.
- The app has **no launcher icon** (TB-A-08), so in the drawer it is the default Android robot, next to the
  other robot-icon apps. Check the label, not the icon.
- Every `.kt` file must contain `All rights reserved` **in its first 5 lines** or the pre-commit hook and CI both
  fail. Enable the hook once per clone: `git config core.hooksPath .githooks`.
- CI (`android.yml`) runs `assembleDebug` and the licence-header script and **nothing else** — a green tick is not
  a test pass.
- There is no Tacklebox AVD. This session used the existing **`Following_Pixel`** (API 35, 1080×2400, density 420).
  Create a `Tacklebox_Pixel` on **API 36** to match `targetSdk` before the next run.

## Reading the database back
Every write must be confirmed in Room, not just on screen. The DB is in **WAL mode**, so you must pull all three
files or you will get an empty database with no tables:
```
S=emulator-5556
adb -s $S exec-out run-as uk.co.tacklebox.app cat databases/tacklebox.db     > /tmp/tb.db
adb -s $S exec-out run-as uk.co.tacklebox.app cat databases/tacklebox.db-wal > /tmp/tb.db-wal
adb -s $S exec-out run-as uk.co.tacklebox.app cat databases/tacklebox.db-shm > /tmp/tb.db-shm
sqlite3 /tmp/tb.db "select id,speciesId,weightGrams,lengthCm,returned,rig,bait,sessionId,waterId from 'Catch';"
```
`run-as` works because the debug build is debuggable; it will not work against a release APK.

This is how TB-A-02 and TB-A-03 were proven. A catch saved through the UI comes back as
`1|11|1814.368||1||||` — the weight converts correctly, but **`sessionId` and `waterId` are both NULL**, because
`MainActivity` passes `null` for the water and `MainViewModel.addCatch` has no session parameter at all.

## The Android findings in one line each
S1: onboarding renders unthemed and near-illegible (TB-A-01) · catches can never record a water (TB-A-02) or a
session (TB-A-03). S2: FAB covers the Bite-windows chip (TB-A-04) · Export JSON (TB-A-05), Share summary
(TB-A-06) and Identify-from-photo (TB-A-07) are inert buttons · no launcher icon (TB-A-08). S3: false
"unavailable offline" copy (TB-A-09) · device location never used (TB-A-10) · no add-water UI (TB-A-11) · Save
button clipped by the nav bar (TB-A-12) · oz-in/lb-out unit mismatch (TB-A-13) · UK defaults to imperial and
`seed()` overwrites settings (TB-A-14) · rotation loses the form (TB-A-15) · solunar rating can never reach 5/5
(TB-A-16) · Auto Backup uploads the journal despite the privacy copy (TB-A-17). S4: unused `CAMERA` permission
(TB-A-18) · off-palette selected chips (TB-A-19) · Room v1 with no migrations or exported schema (TB-A-20) · no
`strings.xml`, so not localisable (TB-A-21) · marketing docs still say `tacklebox-1.8.aab` (TB-A-22) · no
`testTag`s and 11 of 13 icons have no `contentDescription` (TB-A-23).

Full evidence and suggested fixes are in `TACKLEBOX_DEFECTS_2026-09-07.md`.
