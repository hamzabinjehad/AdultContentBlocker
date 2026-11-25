package app.hisn.android.plus

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import app.hisn.android.nativeshell.PlusOffer
import app.hisn.android.nativeshell.PlusPlan
import app.hisn.android.nativeshell.PlusStore
import app.hisn.android.nativeshell.UnrotPlus
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Unrot Plus through Google Play Billing. Lives in the general edition only, so the Islamic
 * edition carries neither the library nor the billing permission. Found by name from
 * [app.hisn.android.nativeshell.PlusStores]; keep the class name and the no-argument constructor.
 *
 * Products to create in the Play Console (ids are fixed here):
 *  - subscription [SUBSCRIPTION] with base plans [YEARLY_PLAN] and [MONTHLY_PLAN]; a free-trial
 *    offer on the yearly plan is picked up by itself and shown as "N days free".
 *  - one-time product [LIFETIME].
 *
 * The app never decides that someone has paid: it asks Play, and writes Play's answer down.
 * A failed question changes nothing, so being offline never switches Plus off.
 */
internal class PlayPlusStore : PlusStore {
    companion object {
        const val SUBSCRIPTION = "unrot_plus"
        const val YEARLY_PLAN = "yearly"
        const val MONTHLY_PLAN = "monthly"
        const val LIFETIME = "unrot_plus_lifetime"
    }

    private class Handle(val details: ProductDetails, val offerToken: String?)

    private val offerState = mutableStateOf<List<PlusOffer>>(emptyList())
    private val reachableState = mutableStateOf(true)
    override val offers: State<List<PlusOffer>> get() = offerState
    override val reachable: State<Boolean> get() = reachableState

    private var client: BillingClient? = null
    private var app: Context? = null
    private var subscriptionOffers = emptyList<PlusOffer>()
    private var lifetimeOffers = emptyList<PlusOffer>()

    private val purchases = PurchasesUpdatedListener { result, list ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && list != null) {
            app?.let { context -> list.forEach { settle(it) }; refresh(context) }
        }
    }

    override fun connect(context: Context) {
        val appContext = context.applicationContext
        app = appContext
        val billing = BillingClient.newBuilder(appContext)
            .setListener(purchases)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
        client = billing
        billing.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) { reachableState.value = false; return }
                reachableState.value = true
                loadOffers()
                refresh(appContext)
            }
            override fun onBillingServiceDisconnected() { reachableState.value = false }
        })
    }

    private fun product(id: String, type: String) =
        QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(type).build()

    private fun loadOffers() {
        val billing = client ?: return
        // Play answers one product type per question.
        billing.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(listOf(product(SUBSCRIPTION, BillingClient.ProductType.SUBS))).build()
        ) { result, list ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            subscriptionOffers = list.flatMap { details ->
                listOf(YEARLY_PLAN to PlusPlan.YEARLY, MONTHLY_PLAN to PlusPlan.MONTHLY).mapNotNull { (planId, plan) ->
                    val plans = details.subscriptionOfferDetails.orEmpty().filter { it.basePlanId == planId }
                    // An offer with a free first phase (the trial) wins over the plain base plan.
                    val best = plans.firstOrNull { o -> o.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L } } ?: plans.firstOrNull()
                    best?.let { o ->
                        val phases = o.pricingPhases.pricingPhaseList
                        val paid = phases.lastOrNull { it.priceAmountMicros > 0L } ?: phases.last()
                        val trial = phases.firstOrNull { it.priceAmountMicros == 0L }?.billingPeriod
                        PlusOffer(plan, paid.formattedPrice, days(trial), Handle(details, o.offerToken))
                    }
                }
            }
            publish()
        }
        billing.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(listOf(product(LIFETIME, BillingClient.ProductType.INAPP))).build()
        ) { result, list ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            lifetimeOffers = list.mapNotNull { details ->
                details.oneTimePurchaseOfferDetails?.let { PlusOffer(PlusPlan.LIFETIME, it.formattedPrice, 0, Handle(details, null)) }
            }
            publish()
        }
    }

    private fun publish() { offerState.value = subscriptionOffers + lifetimeOffers }

    /** Days in an ISO-8601 period such as P7D, P1W or P1M (a month counts as 30). */
    private fun days(period: String?): Int {
        if (period == null) return 0
        val m = Regex("P(?:(\\d+)Y)?(?:(\\d+)M)?(?:(\\d+)W)?(?:(\\d+)D)?").matchEntire(period) ?: return 0
        fun n(i: Int) = m.groupValues[i].toIntOrNull() ?: 0
        return n(1) * 365 + n(2) * 30 + n(3) * 7 + n(4)
    }

    override fun buy(activity: Activity, offer: PlusOffer) {
        val handle = offer.handle as? Handle ?: return
        val item = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(handle.details)
        handle.offerToken?.let { item.setOfferToken(it) }
        client?.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(item.build())).build())
    }

    /** Play wants every purchase acknowledged within three days, or it refunds it. */
    private fun settle(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED || purchase.isAcknowledged) return
        client?.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) { }
    }

    override fun refresh(context: Context) {
        val billing = client ?: return
        val appContext = context.applicationContext
        billing.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { subsResult, subs ->
            if (subsResult.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            billing.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { onceResult, once ->
                if (onceResult.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
                fun owns(list: List<Purchase>, id: String) =
                    list.any { it.purchaseState == Purchase.PurchaseState.PURCHASED && id in it.products }
                (subs + once).forEach { settle(it) }
                UnrotPlus.record(appContext, subscribed = owns(subs, SUBSCRIPTION), lifetime = owns(once, LIFETIME))
            }
        }
    }

    override fun close() {
        runCatching { client?.endConnection() }
        client = null
    }
}
