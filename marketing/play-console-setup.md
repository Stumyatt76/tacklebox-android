# Tacklebox — Play Console setup walkthrough

Pre-filled values from the prep kit. You (account holder) accept the agreements and submit.

## 1. Create app
Play Console → **All apps → Create app**
- **App name:** `Tacklebox — Fishing Journal`
- **Default language:** English (United Kingdom) – en-GB
- **App or game:** App
- **Free or paid:** **Paid**
- **Declarations:** tick Developer Program Policies + US export laws
- Click **Create app**

## 2. Store listing  (Grow → Store presence → Main store listing)
- **App name:** `Tacklebox — Fishing Journal`
- **Short description:** `Private, offline fishing journal — your catches, waters and days on the bank.`
- **Full description:** paste from `marketing/play-store-listing.md`
- **App icon:** `marketing/store-assets/icon.png` (512×512)
- **Feature graphic:** `marketing/store-assets/feature.png` (1024×500)
- **Phone screenshots:** the 6 in `~/Downloads/tacklebox-play-screenshots/` (1080×2160)
- **App category:** Sports  *(alt: Lifestyle)*
- **Contact email:** myattstuart1976@gmail.com

## 3. App content  (Policy → App content)
Fill each section using `marketing/play-data-safety.md`:
- **Privacy policy:** `https://stumyatt76.github.io/tacklebox-android/privacy/`
- **App access:** All functionality available without special access
- **Ads:** No ads
- **Content rating:** run questionnaire → category Utility, all "No" → expect Everyone/PEGI 3
- **Target audience:** general audiences (13+/18+), NOT designed for children
- **Data safety:** approximate location (ephemeral, App functionality, optional) + photos (optional); nothing else
- **Government / financial / health:** none apply

## 4. Pricing  (Monetize → Products / Pricing)
- Paid app → set up a **payments/merchant profile** first (required for paid apps), then set the price.

## 5. Production release  (Release → Production → Create new release)
- Upload **`tacklebox-1.8.aab`** (from the v1.8 GitHub release)
- Release name: `1.8 (9)` ; add release notes
- Review and roll out

## ⚠️ New-account testing gate
Individual developer accounts created recently must run a **closed test with 12+ testers for
14 days** before the app can be promoted to Production. Plan for that timeline: set up a
**Closed testing** track first, add testers, then promote to Production after the requirement
is met.
