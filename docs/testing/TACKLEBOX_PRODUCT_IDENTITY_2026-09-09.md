# Tacklebox product identity audit — 9 September 2026

Both apps' shipping source and inspected Release packages identify the product as Tacklebox. No Caddro, Golf Vault, MyApplication or Season Wrapped matches were found in the inspected Release bundle/APK contents. This is a targeted identity audit, not proof of exclusive authorship or guaranteed App Review acceptance.

## Verified identities

| Surface | Verified value |
|---|---|
| iOS display name / main target | Tacklebox |
| iOS bundle | com.tacklebox.ios |
| Widget target / bundle | TackleboxWidget / com.tacklebox.ios.widget |
| Widget display name | Bite windows — describes this Tacklebox feature |
| iOS tests | com.tacklebox.ios.tests |
| Private CloudKit container | iCloud.com.tacklebox.ios |
| App and widget shared group | group.com.tacklebox.ios |
| Android project / application class | Tacklebox / TackleboxApp |
| Android namespace / applicationId | uk.co.tacklebox.app |
| Android source/test packages | uk.co.tacklebox.app and its subpackages |
| Android launcher / theme | Tacklebox / Theme.Tacklebox |
| Android file-sharing authority | uk.co.tacklebox.app.fileprovider |
| Both journal export identifiers | app = Tacklebox |
| Annual feature | Year on the Water |

Established identifiers were preserved for update, storage, sharing and entitlement continuity. The model/type inventory describes fishing: Catch, FishingSession, Water, Species, TacklePreset, GearItem, river/tide services and bite windows. Generic names such as Theme, MainActivity, Repository and ShareSheet correctly describe responsibilities; branding every variable would not improve product identity.

Checked owned Swift/Kotlin source, tests, filenames, project/build settings, manifests/plists, entitlements, export labels, widget declarations, API destinations and current store drafts. Third-party API/library names are legitimate. Spectral and Figtree now carry original SIL Open Font License notices in both packaged apps and the iOS widget.

## Corrections and regression guard

- Replaced obsolete Season Wrapped wording in active TestFlight notes with Year on the Water.
- Current iOS handover points to this product's design system and review workflow, without another application's workspace path or obsolete free/Plus convention.
- Corrected current store/tester copy: journal use is offline; live services require connectivity, photo ID needs a token, weather capture depends on a recent available snapshot, JSON excludes photos, and unverified cloud backup is not advertised as established.
- Added python3 scripts/check-product-identity.py to both repositories and PR CI workflows. Both gates passed; inserting an unrelated-product probe caused each to fail, and removing it restored success.

The gate checks expected identifiers and known unrelated brands/template placeholders in shipping-owned paths. It cannot discover every app name, compare every App Store product, inspect artwork visually or prove originality. Package checks complement it.

## Remaining cross-brand references

support@caddro.co.uk remains the working contact in local policy/store setup documents and both live Tacklebox support/privacy pages. Both pages returned HTTP 200 with Tacklebox titles on 9 September. The owner has been asked for an existing replacement address. Do not invent a mailbox or break support to achieve zero word matches. A shared developer support domain is not evidence of a duplicate app.

Dated internal defect reports and the original test brief retain historical project references and branch names. The old brief is marked superseded; these records are not shipped. Accurate provenance has been preserved. A historical report mentions a previous 4.3(a) naming change, but the actual Apple rejection correspondence was not available.

Current App Store Connect and Play Console fields were not inspected or changed. Repository drafts and public pages do not establish current console metadata.

## Apple 4.3 assessment

Apple's current [App Review Guidelines, 4.3](https://developer.apple.com/app-store/review/guidelines/#spam) address multiple bundle IDs for the same app and apps indistinguishable from what is widely available. Section 4.2.6 addresses commercial templates/app-generation services; 4.1 addresses copycats. Correct names alone do not establish compliance; legitimate reuse of utilities is not itself the duplicate-app criterion.

The reviewed product has a coherent fishing workflow: private catch records/photos, species-specific personal bests, water passports, session membership, rig/bait presets, fish taxonomy, local solunar windows, geographic river/tide sources and a fishing-year summary. These are concrete features to demonstrate, not a claim that no competitor has them.

Suggested factual review demonstration:
1. Start an empty journal; log species, measurements, returned status, rig/bait and a photo.
2. Show catch editing, notes, species records and personal bests.
3. Associate a catch with an explicitly started fishing session and water passport.
4. Show local bite windows and honestly label live-service availability/coverage.
5. Demonstrate journal export/import; disclose that JSON excludes photos.
6. Open Year on the Water with representative fishing records.

Before submission, refresh screenshots, verify a signed physical-device build, confirm actual cloud capabilities and token-dependent features, and promise only demonstrated behaviour. Remaining gaps are recorded in the QA report. No store submission, appeal or guaranteed approval is part of this audit.

Font notices: [Figtree upstream](https://raw.githubusercontent.com/google/fonts/main/ofl/figtree/OFL.txt), [Spectral upstream](https://raw.githubusercontent.com/google/fonts/main/ofl/spectral/OFL.txt).
