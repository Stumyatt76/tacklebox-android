# Tacklebox — six features and session access

Implemented all six approved feature areas in both native apps, plus two complete free fishing sessions followed by a one-time Unlimited purchase. Changes are on review branches. This is a development QA result, not a store-release approval.

## Scope and behaviour

| Area | Result and verification |
|---|---|
| Management and analysis | Android session details/date/water/notes editing, gear and water editing, discipline preferences, chronological charts and species progression; iOS session-date editing and year-boundary hours. Dates must contain existing catches. Session-note edits were saved visibly on both devices. |
| Portable photo backup | Versioned SHA-256 envelope, stable record IDs, photos and relationships, preview, default keep-existing and explicit replacement. Native exports travelled iOS → Android → iOS with matching IDs, media, decimals and relationships. Repeat imports and rollback/error cases pass. Android’s system picker restored the actual iOS fixture: three new records, one existing species, two photos. Both create actions open the system share sheet. Legacy JSON tests remain passing. |
| Species connection | Browser PKCE, state/callback validation, device-secure OAuth storage, renewal, reconnect/disconnect and manual species selection. RFC 7636 and invalid-callback tests pass; Android Keystore encryption/removal is tested on device. Missing iOS client configuration displays an explanatory status. Actual provider login/recognition remains untested without client registration/access. |
| Session glance | iOS ActivityKit timer/count/bite-window route and Android private ongoing notification. iOS Dynamic Island displayed the updated catch count. Android denied/allowed permissions were exercised; notification timer/count appeared and its End session action persisted the end and removed the notification. Relaunch preserved journal allowance. Physical-device lifetime, reboot and cloud behaviour remain outside this simulator pass. |
| Trip planner | Seven-day Open-Meteo forecasts, dated solunar windows, water-filtered monthly journal history and links to available river/tide context. No invented catch-success predictions. Live date changes passed on both devices. Android denied-location fallback and offline cached forecast were exercised; cached data was visibly labelled. iOS offline-cache logic was reviewed but its new planner was not separately exercised offline in the UI. |
| Season Book | Year/catch selection, optional photos/notes, native PDF preview/share, session stories and personal bests. Both native previews/share sheets were exercised. Every page of two six-page stress fixtures and two two-page private variants was rendered and visually inspected. A4 size, end-of-story retention and note omission pass. Android camera EXIF orientation has a device test. |
| Two free sessions | Both second sessions accepted a catch. Third-session entry routes to Unlimited, while read/export remains available. Database tests verify the allowance survives reset/stale settings and failed starts do not spend it. Imports do not grant trial or purchase entitlement. Store products and localized prices are deliberately not fabricated. |

## Verification

- iOS: **105 tests in 13 suites passed**; Debug test build and unsigned simulator Release build passed with local Xcode. No third-party iOS dependency added.
- Android: **133 unit tests passed**, zero failures/errors/skips; **12 native device tests passed** (six prior migration tests, two new migration tests, cross-platform restore, PDF pagination, EXIF orientation and secure-token storage). Debug/Release APK builds and `lintDebug` passed. Lint warnings remain, chiefly existing locale/API/dependency recommendations; no lint errors were suppressed.
- Total: **250 automated tests**. Native UI bridge execution is additional evidence, not counted as an automated assertion suite. Failed automation taps were retried using observed controls; they are not recorded as feature passes.
- Both product-identity gates passed: iOS 82 files, Android 83 files. Bundle/package, app group, CloudKit container, widgets and tests retain Tacklebox identities. The naming check does not prove originality or guarantee Apple guideline 4.3 acceptance.
- iOS accessibility XXXL session/paywall flow and Android font-scale 2 backup controls were checked. Android navigation labels were changed to wrap at enlarged sizes.
- `git diff --check` passed. Generated Xcode project/plists match the build generated from `project.yml`.
- Tests ran only on dedicated Tacklebox devices: iPhone 17 Pro / iOS 26.5 (`9F2AE336-BC12-4506-AA92-091D7F66353F`) and API 35 Android emulator `emulator-5560`. Other app devices were not modified. Font settings and Android network access were restored.

Commands: iOS `xcodebuild ... -scheme Tacklebox -configuration Debug -destination <dedicated simulator> CODE_SIGNING_ALLOWED=NO test`, then unsigned simulator Release build. Android `./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug assembleRelease assembleDebugAndroidTest`, followed by the complete `AndroidJUnitRunner` suite on the dedicated emulator. Identity: `python3 scripts/check-product-identity.py` in each repository.

## Design comparison

[Side-by-side Markdown gallery](six-features-20260909/comparison.md) and [interactive HTML gallery](six-features-20260909/comparison.html). Nine pairs show access, planner, backup, book selection/preview, matching exported pages, session detail and native live displays. Both apps use the same Heritage palette and bundled Spectral/Figtree fonts. Platform sheets, navigation, selectors and some existing screen arrangements still differ; this is not pixel-identical UI.

## Configuration required before release

1. Create non-consumable **`tacklebox_unlimited`** in both stores, set the app download to free, choose its price and provide the Play app’s public licensing key via `tackleboxPlayBillingPublicKey`. No existing products or paying customers were reported. Run real sandbox/license-tester purchase, cancellation, pending, restore, refund/revocation and offline-entitlement tests. No real purchase test was claimed here; purchase remains unavailable until configured. There is no cross-store account entitlement.
2. Register approved public native iNaturalist clients. iOS: build setting `INATURALIST_CLIENT_ID`, callback `tacklebox://oauth/inaturalist`. Android: Gradle property `tackleboxINaturalistClientId`, callback `uk.co.tacklebox.app://oauth/inaturalist`. Confirm computer-vision access and provider commercial-use terms. Never put a client secret in either app.
3. Complete signed physical-device, CloudKit/App Group, real-camera, long-duration Live Activity, notification reboot/recovery and screen-reader testing. The full feature set was reviewed and covered through the matrix above; every hardware/provider permutation has not been certified.
4. Publish the updated privacy policy and store copy with the release. Repository drafts were corrected for billing, OAuth and portable exports, including removal of a false Google Drive integration claim. The separately hosted iOS support policy and live store listings were not changed.

## Migration and remaining limits

Android Room v6 includes a checked-in schema and v5→v6 migration. Full v1→v6 validation passes; existing records gain stable portable IDs and new allowance fields default to zero/false. iOS changes are additive with CloudKit-compatible defaults. Its store-open fallback now preserves the database and shows a recovery screen instead of deleting files on failure. CloudKit schema deployment is a release step, not performed here.

Photo backups are unencrypted, limited to 256 MB, and exclude service credentials, precise coordinates and purchases. Very large real photo libraries near that limit still need physical-device memory/performance testing; iOS model extraction/restoration uses its model context while JSON/checksum encoding/decoding is moved off the main actor. Replaced Android photo files can remain as unreferenced local files; this is a storage-cleanup limitation, not a record-restoration failure. Legacy manually entered Android species tokens remain in the private Room database; the new OAuth path uses Keystore encryption. Restoring exported files is not an account-based allowance transfer.

No GitHub iOS build was dispatched. The existing workflow only permits the macOS build job for deliberate `workflow_dispatch`. No merge, store submission, signing/profile change, real payment or live pricing change was made.

## Review base and artifacts

This feature review is stacked on `codex/qa-integrity-parity-20260909` (draft PR #23 in each product). Merge/review that foundation first. The accompanying diff contains all feature code/configuration/documentation changes against that base, excluding generated review artifacts to avoid recursive diffs. Logs and checksums are included with this report; the complete result bundle remains on the Mac under `/tmp/tacklebox-six-features-20260909/final-tests.xcresult`.

Platform: **android**. Code commit `29fb33c0563c4ac475f58ee2eff1a1ffc2b570ac`; base `3f7c9bec005d8e47a33f0dad73c6817d10ee61f3`.
