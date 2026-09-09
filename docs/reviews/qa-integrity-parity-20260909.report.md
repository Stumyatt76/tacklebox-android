# Tacklebox QA remediation — 9 September 2026

This follows the 8–9 September baseline audit. iOS remains the design reference. The branch fixes confirmed integrity defects and begins visual alignment; it does not claim complete feature parity or release-wide success.

## Scope

iOS baseline: 611ef5383d3f729ac4c91cfb4bd4b5f281f1ee99, 1.0 build 6.
Android baseline: 97252c155918babcb7ec13ab5bbe661e887813a1, 2.1 code 12.
Both branches: codex/qa-integrity-parity-20260909.
Only dedicated Tacklebox devices and synthetic data were used; no personal phone journal or owner service key.

## Findings addressed

| Finding | Change and evidence |
|---|---|
| F01 wrong species | Both lookups use canonical scientific names, require one exact active fish match, and reject ambiguity. Startup repairs known seeded mismatches and associated metadata. Regression fixtures pass; live iOS Barbel now shows Common Barbel / Barbus barbus and a fish photo. |
| F02 immediate reset | iOS explicit alert, affected counts, photo/JSON explanation, Cancel and save-error handling. Cancel verified in UI; reset tested in isolated SwiftData/preferences. |
| F03 wrong weather | Android no longer saves reference-location fallback as catch weather; no location means no snapshot. Both apps restrict current weather to catches aged 0–15 minutes. Denied-location UI save leaves zero conditions rows. |
| F04 large text | iOS photo actions, species/preset grids, compact tabs and date field adapt. Android photo actions stack at font scale 2. Screenshots inspected; not full accessibility certification. |
| F05 duplicate imports | Reuse identical session content and remap catches; deduplicate repeated/stale plans; preview all entity counts; accept sessions-only imports. SwiftData rollback and Room transactions protect imports. Repeated/partial/stale and forced Android rollback tests pass. |
| F06 water cascade | iOS Water.sessions now nullifies. Graph/photo/condition preservation tested. Original on-disk store upgraded and its water deleted in UI: catch 1, sessions 2, extra-photo row 1 remained; water references cleared. |
| F07 photo ID entry | Identify restored after photo selection, with disclosure/setup route. UI reaches missing-token guidance; token-success/upload not exercised. |
| F08 hidden fields | iOS detail displays saved length and returned/kept status; verified after save. |
| F09 orphan conditions | Android clears conditions transactionally during delete-all. Regression covers orphan records and atomic catch/photo creation. |
| F10 annual hours | Android clamps finished sessions to the local-calendar-year interval; UI and sharing use one calculation. Monthly groups now distinguish years and sort chronologically. |
| F11 implicit sessions | iOS quick logging no longer invents a session. Both attach only to an eligible open session started no later than the catch. UI save produced one catch and zero sessions on each. |
| F12 broader parity | Partial: Android bundles the same Spectral/Figtree fonts and aligns header/card spacing and photo controls/gallery size. Management screens, charts and detailed flows remain backlog. |
| Live data | Android distinguishes request failure from valid empty rivers, propagates cancellation, formats coordinates independently of locale, and labels reference/cached sources. Offline UI gives an actionable connection error. |
| Product identity | Source gates and inspected Release packages pass. Active naming/metadata fixes and factual 4.3 assessment are in TACKLEBOX_PRODUCT_IDENTITY_2026-09-09.md. |

## Validation

| Check | Result |
|---|---|
| iOS automated suite | 94 passed, no failures/skips; 10 new regression tests |
| Android JVM suite | 123 passed, no failures/errors/skips; 14 new regression tests |
| Android migrations | 6 passed on dedicated API 35 AVD |
| Total | 223 automated tests passed; naming gates counted separately |
| iOS builds | Debug tests and simulator Release passed; Release rebuilt with font notices |
| Android builds | Debug and R8/shrunk Release passed; rebuilt with font notices |
| Android lint | 23 warnings, 2 informational findings, no errors |
| Copyright gate | Android passed |
| Identity gates | Both pass and reject intentionally introduced wrong-brand probes |
| Targeted UI/DB | Normal/large capture, reset Cancel, photo-ID setup, catch fields, fish metadata, water-upgrade preservation, denied-location weather and offline river error verified |
| Distribution | Latest physical-device distribution install and store archive/upload not exercised |

Robolectric tests use SDK 34 because the installed version does not support SDK 35. Actual migration/UI checks used API 35. The initial unsupported-SDK attempt failed; corrected tests passed. No double counting.

Mac evidence: /tmp/tacklebox-fixes-20260909/ contains build/test logs, xcresult, identity and DB proof JSON. UI evidence: /tmp/tacklebox-qa-20260908-2027/evidence/fix-*. Windows task tacklebox-qa/remediation/ holds the shareable comparison/report. Named QA screenshots were copied with owner authorization.

## Compatibility and unfinished work

- Import remains schema 1. Session matching uses whole-second start/end, normalized water name and exact notes. This is not portable UUID identity: edited sessions may import as new; indistinguishable same-second sessions collapse. Existing duplicates are not silently deleted.
- Android stays Room schema 5. iOS changes deletion policy without renaming entities/stores. On-disk upgrade proves rows/relationships; its old-store fixture lacked external photo files. The separate regression verifies actual photo data.
- JSON excludes photos. Full photo backup, real cloud sync and service-token success/expiry remain unverified or unimplemented.
- Android is closer visually, not pixel-identical: flow chips differ from the iOS grid; management, charts, year selector and detailed solunar gaps remain.
- Existing iOS whole-centimetre precision can turn 1 inch into 3 cm, displayed as 1.2 inches.
- Physical camera, real-device accessibility/permissions, tablets, oldest OS versions, large datasets and all network/geography permutations are not comprehensively exercised.
- Confirm service commercial-use terms/setup before paid distribution. Owner credentials were not used to simulate success.
- Working shared support email remains until an existing replacement is supplied. Store consoles and original Apple correspondence were not inspected.

## Recommended next features

1. Complete Android session/water/gear editing, discipline preferences, progression charts and yearly selection against iOS.
2. Versioned backup containing photos and portable IDs, with a restore preview.
3. Supported species-ID account connection rather than manually refreshed tokens.
4. Fishing-session planning using attributed forecasts and the angler's own records, with transparent prediction limits.

Changes are prepared on review branches. No main merge, deployment or store submission has occurred. Review the diffs and run signed-device checks before release.

Reviewed android implementation commit: 1c7ae3a81222cbb94798f99b7fcfd72998c25160
Integration baseline: 97252c155918babcb7ec13ab5bbe661e887813a1
Diff generated with git diff --binary BASE HEAD -- . before this review-artifact commit.
