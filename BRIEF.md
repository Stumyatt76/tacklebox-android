Build **Tacklebox for Android** — a native Android port of an existing iOS SwiftUI app. It is a private, offline-first fishing app for anglers, sold as a one-time paid download with full access (NO subscriptions, NO in-app purchases). Target feature parity with the iOS app described below.

## Stack
- Kotlin + Jetpack Compose, Material 3 with a custom dark theme (the app is dark-only).
- Room for local persistence (offline-first). MVVM, Kotlin coroutines/Flow.
- Min SDK 26+, target latest. Single-activity, Compose Navigation.
- Optional cloud backup to the user's own Google Drive (app-data scope) — mirrors the iOS iCloud backup. Off by default.

## Design language
- Dark, calm, "heritage" feel. Palette: background #0E1A1E, surface #15272D, surface-inset #1C343B, ink #F3EEE4, muted #9BB0B3, brass #C9A24B, brass-soft #E2C890, teal accent #57B3A6.
- Serif display font (Spectral) for headings, clean sans (Figtree) for body — bundle the .ttf or use closest Google Fonts fallbacks.
- Bottom tab bar: Vault · Waters · [ + Log (centre FAB) ] · Sessions · Insights.

## Data model (Room entities)
- **AppSettings**: unitSystem (METRIC/IMPERIAL, default from locale), activeDisciplines.
- **Species**: name, discipline (COARSE/GAME/SEA…), scientificName?, commonName?, about?, referencePhotoUrl?, photoAttribution?.
- **Water**: name, type (LAKE/RIVER/CANAL/RESERVOIR/SEA…), region, disciplines[], swimNotes; has many FishingSessions.
- **FishingSession**: water?, startAt, endAt?, notes; has many Catches.
- **Catch**: species?, weightGrams?, lengthCm?, returned (bool), photo (local file/blob), rig?, bait?, caughtAt, session?, and a one-to-one **ConditionsSnapshot** (weather stamped at catch time: air temp, wind dir/speed, pressure + trend, moon phase).
- **GearItem**: name, category (ROD/REEL/LINE/…​/OTHER), notes.
- **TacklePreset**: name, kind (RIG/BAIT/…) — quick-pick presets when logging.
Seed a starter set of common UK species and a couple of sample waters on first run (as the iOS app does).

## Screens / features (match iOS)
1. **Onboarding** — brief welcome; start with sample data or empty. Ask permission for location (used only for live conditions; precise spot never stored) and photos/camera when first needed.
2. **Vault (home)** — "Featured Personal Best" hero (photo + weight + species), a "Today on the bank" solunar summary (next bite window, sunrise/sunset, day rating), stat tiles (fish landed / species / waters), and a PB board per species.
3. **Log a Catch** (centre FAB) — add photo (camera or library), pick species (chips + add-new), **AI species-identification from the photo** (calls an external vision service; requires a user-supplied API token in settings, with graceful fallback), weight (lb/oz or kg/g per unit system) and length, rig & bait via saved presets, returned toggle. Auto-stamp current weather/conditions. Save; celebrate a new personal best.
4. **Waters** — list of the user's private waters, plus two live-data entries at the top: **River conditions** and **Tides & sea**. Tapping a water opens its "passport" (stats, private swim notes, per-water insights).
5. **Sessions** — start/stop a fishing session at a water; list past sessions with their catches.
6. **Insights** — headline stats, catches-over-time chart, species breakdown, waters breakdown, conditions insight, and a **"Year on the Water"** year-in-review screen (shareable summary card: fish landed, total weight, biggest fish, best gear, hours on the bank). (Do NOT call this "Wrapped".)
7. **My Tacklebox** — gear inventory and rig/bait presets, plus a gear-performance panel (best rig, top bait by catch-rate, gear behind personal bests).
8. **Bite windows (Solunar)** — major/minor feeding periods, sun & moon times, day rating; computed locally from date + location (no network needed).
9. **Tides & sea** — coastal tide table + wave/sea-state forecast (from a marine weather source). Shows an empty state when inland.
10. **River conditions** — nearest river gauge (level, flow, trend) + nearby gauges list, from the Environment Agency (UK) / USGS where available.
11. **Species record / Catch detail** — species info page and an individual catch detail (photo hero, water, date, rig, bait, conditions).
12. **Settings** — units (metric/imperial), backup toggle, species-ID token, data export/delete.

## External services (all optional/graceful)
- Weather + marine: Open-Meteo (free, no key) for current conditions, marine/wave forecast.
- Tides: a tide source (e.g. Open-Meteo marine or a free tidal API).
- River: Environment Agency real-time flood-monitoring API (UK), USGS where relevant.
- Solunar & sun/moon: computed locally (astronomical calculation), no API.
- Species ID: an external image-recognition API, keyed by a user-provided token; degrade gracefully if absent.
No advertising, no tracking, no analytics SDKs.

## Product rules
- **Offline-first**: all logging, sessions, waters, gear, insights and solunar work fully offline. Only live river/tide/weather need a connection and must fail gracefully ("couldn't update, try again").
- **Private**: no account, no login, no social feed. Data lives on-device; optional user-owned cloud backup only.
- **Paid, full access**: every feature included; NO in-app purchases or subscriptions anywhere in the app or store listing.
- Missing photos (e.g. species reference images) simply fall back to a tasteful fish-glyph placeholder; users can add their own photo.

## Deliverable
A buildable Android Studio project (Gradle) that compiles and runs on an emulator, with the screens above wired to Room and the live-data services. Keep it a distinct, polished product in its own right — not a thin wrapper. Report the module structure and how to run it.
