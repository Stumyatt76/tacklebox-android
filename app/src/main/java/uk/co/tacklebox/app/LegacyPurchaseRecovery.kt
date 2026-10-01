/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import android.content.Intent
import android.net.Uri

/** Recovery grants a normal Play entitlement after support verifies the original paid-app order. */
object LegacyPurchaseRecovery {
    const val SUPPORT_EMAIL = "support@caddro.co.uk"

    fun supportIntent(): Intent {
        val subject = Uri.encode("Tacklebox original paid purchase recovery")
        val body = Uri.encode(
            "I bought the original paid Tacklebox Android app and would like to recover Unlimited.\n\n" +
                "Google Play order number (GPA…):\nPurchase date:\n\n" +
                "Please verify my original order and help me obtain a Google Play recovery code.",
        )
        return Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SUPPORT_EMAIL?subject=$subject&body=$body"))
    }

    fun redeemIntent(code: String): Intent {
        require(code.isNotBlank())
        return Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/redeem").buildUpon()
            .appendQueryParameter("code", code.trim()).build())
    }
}
