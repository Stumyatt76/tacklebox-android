# Six Tacklebox features — 9 September 2026

Owner-approved scope: implement all six in both apps, iOS leads, then full regression and feature testing. Build iOS on the owner's Mac only; never dispatch GitHub iOS builds. Preserve product identifiers and private-journal behaviour.

1. Management/analysis parity: session detail, notes and dates; water and gear editing; active disciplines; species weight progression; chronological and categorical charts; annual selection; solunar day navigation and historical matching. Preserve existing records and validate date ranges.
2. Portable photo backup: a versioned Tacklebox backup containing stable record IDs, journal relationships, cover/extra photos and checksums. Preview additions/conflicts; default keep existing records. Validate malformed/oversized input and apply atomically. Keep legacy JSON import/export working. Exclude service credentials and private location coordinates.
3. Species connection: supported browser OAuth PKCE, state validation, secure token storage, renewal/reconnect and disconnect. Never embed a client secret. Manual entry remains available. Public client registration and recognition access are external prerequisites, not pretend passes.
4. Session glance: iOS Live Activity and Android ongoing notification with elapsed timer, catch count, next bite window and route to finish. Recover from app relaunch, end cleanly, respect denied permissions and lock-screen privacy.
5. Trip planner: comparable prospective dates with attributed forecasts, local bite windows and relevant available river/tide context plus descriptive personal catch history. Explain missing/cached data and avoid unsupported catch-success predictions.
6. Season Book: select a year and catches, optionally include notes/photos, preview and share a paginated PDF with photos, waters, session stories and personal bests. Both platforms use the same sections and privacy controls.

Acceptance: unit/integration tests for new calculations, data round trips, imports and error paths; existing full suites and migrations; local Debug/Release builds; dedicated UI flows, large text, offline/denied states; bidirectional backup fixtures and PDF rendering; naming gate and before/after comparison. Explicitly record hardware/provider cases that cannot be exercised. Review branches only, no store submission or merge.


## Confirmed access model
Two free sessions, with all features during each session, followed by a non-consumable Unlimited purchase. Finish the second session in full; gate creation of the third. Existing records and export stay accessible. Free catch logging must belong to an active trial session. Quick logging without a session prompts the user to start/resume one. Journal reset does not renew the allowance. Imported sessions do not carry purchase or trial entitlements. No existing paying customers or store products were reported by the owner. Product ID reserved in code: `tacklebox_unlimited`; localized price comes only from the store. Purchases are store-specific; no cross-store account entitlement is implied.

Release configuration still required: create the non-consumable in both stores, select price, configure the Play public verification key, register public iNaturalist native clients and approved recognition access. Test real purchases and provider callbacks before release. Nothing here authorizes store submission.

Repository release copy and privacy drafts are updated for this model. The separately hosted iOS support policy still needs publication with the release. No live listing, price or policy was published.
