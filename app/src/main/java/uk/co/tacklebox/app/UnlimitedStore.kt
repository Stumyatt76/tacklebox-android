/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object SessionAllowance {
    const val FREE_LIMIT=2
    fun label(used:Int):String { val left=maxOf(0,FREE_LIMIT-used);return "$left free ${if(left==1)"session" else "sessions"} remaining" }
    fun canStart(used:Int,unlimited:Boolean)=unlimited || used<FREE_LIMIT
}
data class StoreState(val unlimited:Boolean=false,val price:String?=null,val busy:Boolean=false,val message:String?=null,val includedWithPurchase:Boolean=false)

/** One non-consumable. Signed receipts are checked again before using an offline entitlement. */
class UnlimitedStore(context:Context):PurchasesUpdatedListener {
    companion object {
        const val PRODUCT_ID="tacklebox_unlimited"
        /**
         * Tacklebox 1.8–2.1 (versionCode ≤ 12) sold on Google Play as a paid app with everything included. Google Play
         * cannot tell a paid-era buyer from a later free download, so anyone whose Play Store install predates the
         * switch to the free download keeps unlimited sessions without buying again. Sideloaded installs do not qualify.
         */
        val PAID_ERA_END:Long=java.time.Instant.parse("2026-09-12T00:00:00Z").toEpochMilli()
        fun includedWithOriginalPurchase(installer:String?,firstInstallTime:Long,paidEraEnd:Long=PAID_ERA_END):Boolean=
            installer=="com.android.vending" && firstInstallTime<paidEraEnd
        private fun includedWithOriginalPurchase(context:Context):Boolean=runCatching {
            val pm=context.packageManager
            val installer=if(android.os.Build.VERSION.SDK_INT>=30)pm.getInstallSourceInfo(context.packageName).installingPackageName
                else @Suppress("DEPRECATION") pm.getInstallerPackageName(context.packageName)
            includedWithOriginalPurchase(installer,pm.getPackageInfo(context.packageName,0).firstInstallTime)
        }.getOrDefault(false)
    }
    private val preferences=context.getSharedPreferences("tacklebox-purchases",Context.MODE_PRIVATE)
    private val includedWithPurchase=includedWithOriginalPurchase(context)
    val state=MutableStateFlow(StoreState(unlimited=includedWithPurchase,includedWithPurchase=includedWithPurchase))
    private var product:ProductDetails?=null
    private var connecting=false
    private var restoring=false
    fun restore() { restoring=true;state.value=state.value.copy(busy=true,message=null);refresh() }
    private val client=BillingClient.newBuilder(context).setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection().build()
    init {
        val json=preferences.getString("receipt",null);val signature=preferences.getString("signature",null)
        if(json!=null && signature!=null)runCatching { Purchase(json,signature) }.getOrNull()?.let { purchase->
            if(verified(purchase))state.value=state.value.copy(unlimited=true)
        }
        refresh()
    }
    private fun verified(purchase:Purchase):Boolean {
        if(purchase.purchaseState!=Purchase.PurchaseState.PURCHASED || PRODUCT_ID !in purchase.products || BuildConfig.PLAY_BILLING_PUBLIC_KEY.isBlank())return false
        return runCatching {
            val key=KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(BuildConfig.PLAY_BILLING_PUBLIC_KEY)))
            Signature.getInstance("SHA1withRSA").run { initVerify(key);update(purchase.originalJson.toByteArray(Charsets.UTF_8));verify(Base64.getDecoder().decode(purchase.signature)) }
        }.getOrDefault(false)
    }
    fun refresh() {
        if(client.isReady){query();return}
        if(connecting)return
        connecting=true
        client.startConnection(object:BillingClientStateListener {
            override fun onBillingSetupFinished(result:BillingResult) {
                connecting=false
                if(result.responseCode==BillingClient.BillingResponseCode.OK)query()
                else state.value=state.value.copy(busy=false,message="Google Play could not be reached. Your journal is still available.")
            }
            override fun onBillingServiceDisconnected() { connecting=false }
        })
    }
    private fun query() {
        val query=QueryProductDetailsParams.newBuilder().setProductList(listOf(QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT_ID).setProductType(BillingClient.ProductType.INAPP).build())).build()
        client.queryProductDetailsAsync(query) { result,details->
            product=if(result.responseCode==BillingClient.BillingResponseCode.OK)details.productDetailsList.firstOrNull() else null
            val price=product?.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice
            state.value=state.value.copy(price=price,message=if(price==null)"Unlimited is not available from Google Play yet. Please try again later." else if(BuildConfig.PLAY_BILLING_PUBLIC_KEY.isBlank())"Purchase verification is not configured yet." else state.value.message)
        }
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result,purchases->
            if(result.responseCode==BillingClient.BillingResponseCode.OK) {
                val owned=purchases.firstOrNull(::verified)
                if(owned==null) { preferences.edit().remove("receipt").remove("signature").apply();state.value=state.value.copy(unlimited=includedWithPurchase,busy=false,message=if(restoring)"No verified Unlimited purchase was found for this Google Play account." else state.value.message) }
                else accept(owned)
                restoring=false
                if(purchases.any { PRODUCT_ID in it.products && it.purchaseState==Purchase.PurchaseState.PENDING })state.value=state.value.copy(message="Purchase pending approval. Unlimited unlocks when Google Play confirms it.")
            } else state.value=state.value.copy(busy=false,message="Purchases could not be checked. Please try again.")
        }
    }
    private fun accept(purchase:Purchase) {
        preferences.edit().putString("receipt",purchase.originalJson).putString("signature",purchase.signature).apply()
        state.value=state.value.copy(unlimited=true,busy=false,message="Unlimited unlocked.")
        if(!purchase.isAcknowledged)client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) { result->
            if(result.responseCode!=BillingClient.BillingResponseCode.OK)state.value=state.value.copy(message="Unlimited unlocked. Reopen the app online to finish confirming your purchase.")
        }
    }
    fun purchase(activity:Activity) {
        if(state.value.busy)return
        val details=product ?: return
        val offer=details.oneTimePurchaseOfferDetailsList?.firstOrNull() ?: return
        if(BuildConfig.PLAY_BILLING_PUBLIC_KEY.isBlank()){state.value=state.value.copy(message="Purchase verification is not configured yet.");return}
        state.value=state.value.copy(busy=true,message=null)
        val builder=BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details)
        offer.offerToken?.let(builder::setOfferToken)
        val params=builder.build()
        val result=client.launchBillingFlow(activity,BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params)).build())
        if(result.responseCode!=BillingClient.BillingResponseCode.OK)state.value=state.value.copy(busy=false,message="The purchase could not be started. Please try again.")
    }
    override fun onPurchasesUpdated(result:BillingResult,purchases:MutableList<Purchase>?) {
        state.value=state.value.copy(busy=false)
        when(result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases.orEmpty().filter(::verified).forEach(::accept)
                if(purchases.orEmpty().any { it.purchaseState==Purchase.PurchaseState.PENDING })state.value=state.value.copy(message="Purchase pending approval.")
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
            else -> state.value=state.value.copy(message="The purchase could not be completed. Please try again.")
        }
    }
}
