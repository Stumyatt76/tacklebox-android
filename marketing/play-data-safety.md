# Tacklebox — data-flow review for the next Play release

Updated 9 September 2026. This replaces the earlier questionnaire draft, which incorrectly described Google Drive backup and no billing. Recheck the live Console questionnaire against the shipped build before submission; no Console answers have been changed.

- Journal records and photos remain on-device. File backups and PDF exports go only to the destination the user chooses in Android’s share sheet. There is no direct Google Drive integration.
- Approximate coordinates go to requested weather, river and tide providers. A rounded location and timestamp can be retained locally for the session notification; location is not attached to exported journal records.
- Optional iNaturalist browser sign-in associates requests with that provider account. OAuth tokens use Android Keystore encryption. Recognition sends only the selected photo and applicable approximate location. The legacy manually supplied API token remains in the app’s private database; it is excluded from exported backups. There is no Tacklebox account or developer journal server.
- Google Play Billing processes the optional non-consumable `tacklebox_unlimited`. The app verifies and caches a signed receipt locally and queries Google Play for entitlement and revocation. No card data is handled by Tacklebox. Purchase of digital goods must be disclosed.
- There are no ads, analytics or tracking SDKs. Do not infer final data-safety classifications solely from this summary; review provider terms and the current questionnaire.
- App access review must explain the two-session allowance, Unlimited restoration, and optional provider configuration. Do not claim every feature needs no setup.

Release gates: free download, non-consumable product/price, Play public verification key, license-tester purchase lifecycle, public iNaturalist client registration and recognition access, updated privacy page and screenshots. No subscription is offered.
