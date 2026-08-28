# Tacklebox — Play Console: Data safety & content rating answers

> A practical fill-in guide. You (the developer) make the final declaration — this reflects
> how the app actually behaves. Two nuanced calls are flagged with **⚖️**.

---

## Recommendation before you fill this in

The app currently requests **precise** location (`ACCESS_FINE_LOCATION`). It only needs
**approximate** location for weather/tide/river lookups. Dropping to coarse-only makes the
data-safety form simpler (no "precise location") and matches the privacy story. The answers
below assume the **coarse-only** version. If you keep precise location, change "Approximate
location" to "Precise location" throughout. *(I can make this one-line code change on request.)*

---

## Data safety — overview

| Question | Answer |
|---|---|
| Does your app collect or share any required user-data types? | **Yes** (location for live conditions; optionally photos) |
| Is all collected data encrypted in transit? | **Yes** (all requests use HTTPS) |
| Do you provide a way to request data deletion? | **Yes** — data is on-device; users delete in-app (Settings → delete) or by uninstalling. No server-side account data exists. |

---

## Data types collected / shared

### Location — Approximate location
- **Collected:** Yes
- **Shared:** No*
- **Processed ephemerally:** **Yes** — coordinates are used in the moment to fetch conditions and are not stored by the app.
- **Purpose:** App functionality
- **Required or optional:** Optional (only when the user enables live conditions)
- ⚖️ **Note (\*shared):** When you use live conditions, approximate coordinates are sent to
  data providers (Open-Meteo, UK Environment Agency) purely to return weather/river data.
  Google treats transfers to a provider acting on your behalf as *not* "sharing." These are
  public data sources queried in the moment, no account identifiers attached — so **No** to
  "shared" is defensible. If you prefer maximum caution, mark Shared: Yes → purpose App functionality.

### Photos and videos — Photos
- **Collected:** Yes (only if the user uses an upload feature — see note)
- **Shared:** No*
- **Processed ephemerally:** No
- **Purpose:** App functionality
- **Required or optional:** Optional
- ⚖️ **Note:** Catch photos are stored **on-device** by default (that alone is not "collection").
  Two optional, user-initiated paths transmit a photo:
  1. **Google Drive backup** (off by default) — copies data to the **user's own** Drive account,
     which you don't access. Transfers to the user's own cloud generally need not be declared as
     collection/sharing by you.
  2. **AI species identification** (opt-in, needs a token the user supplies) — sends the chosen
     photo to the third-party recognition service the user configured.
  Because at least one path can transmit a photo, declaring Photos as **Collected: Yes / optional /
  App functionality** is the safe, honest choice. Mark **Shared: No** on the basis that these are
  user-initiated to the user's own account / a provider they configure — or Yes if you prefer caution.

### Everything else — NOT collected
The app has no accounts and no analytics, so **none** of these apply:
- Personal info (name, email, address, IDs) — **No**
- Financial info — **No**
- Health & fitness — **No**
- Messages / contacts / calendar — **No**
- App activity, browsing, search history — **No**
- Device or other identifiers — **No**
- Crash logs / diagnostics / analytics — **No** (no analytics or crash-reporting SDKs)

---

## Content rating questionnaire (IARC)

Category: **Utility / Productivity / Other** (not a game).

| Question | Answer |
|---|---|
| Violence (cartoon, realistic, blood) | **No** |
| Sexual or suggestive content / nudity | **No** |
| Profanity or crude humour | **No** |
| References to drugs, alcohol, tobacco | **No** |
| Gambling / simulated gambling | **No** |
| Fear / horror content | **No** |
| Does the app let users interact or exchange content? | **No** (no accounts, no social feed, no messaging) |
| Does the app share the user's current physical location with other users? | **No** |
| Does the app allow purchase of digital goods? | **No** (no in-app purchases) |
| User-generated content shared publicly? | **No** |

**Expected result:** *Everyone / PEGI 3* (lowest rating tier).

---

## Also worth setting in the Console
- **Ads:** This app contains **no ads** → answer "No" to the ads declaration.
- **App access:** All functionality is available without special/login access → no test credentials needed.
- **Government / financial / health:** None apply.
- **Target audience & content:** General audiences; **not** designed for children (so it is not in the "Designed for Families" programme).
- **Pricing:** Paid app, one-time purchase, no subscriptions or in-app purchases.
