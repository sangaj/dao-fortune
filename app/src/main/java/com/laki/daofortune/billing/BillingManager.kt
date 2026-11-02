package com.laki.daofortune.billing

import android.app.Activity
import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** What the paywall is currently doing. */
sealed interface BillingUiState {
    data object Idle : BillingUiState
    data object Connecting : BillingUiState

    /**
     * The connection to Play dropped and a retry is already scheduled.
     *
     * [attempt] counts from 1 and [waitSeconds] says how long until the next
     * try, so the reader can see that the app is working on it.
     */
    data class Reconnecting(val attempt: Int, val waitSeconds: Int) : BillingUiState

    data class Ready(val price: String, val title: String, val description: String) : BillingUiState
    data object Flowing : BillingUiState
    data class Purchased(val restored: Boolean) : BillingUiState

    /**
     * Play is still settling the payment.
     *
     * Not a failure and not an unlock: the reader is told to wait, and the app
     * unlocks on its own once Google Play confirms the purchase.
     */
    data class Pending(val message: String) : BillingUiState

    data class Failed(val message: String) : BillingUiState
}

/**
 * Google Play Billing wrapper for the single one-off product
 * "dao_fortune_full_access".
 *
 * The connection is a state machine rather than a one-shot call:
 *
 *  * a dropped connection is **reconnected with an exponential backoff**
 *    ([ReconnectPolicy]) instead of leaving the reader with a dead paywall;
 *  * a failed setup is retried only when the failure is transient — no Play
 *    Store on the device is not worth retrying;
 *  * **every** query checks its response code, so a network failure is reported
 *    instead of being silently reported as "no purchase";
 *  * ownership is checked **when the connection comes up**, so a purchase that
 *    completed while the app was closed (or a pending one that has since
 *    settled) still unlocks the app.
 *
 * @param onOwnershipConfirmed called when Play reports an existing purchase.
 */
class BillingManager(
    private val context: Context,
    private val onOwnershipConfirmed: () -> Unit = {}
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<BillingUiState>(BillingUiState.Idle)
    val state: StateFlow<BillingUiState> = _state.asStateFlow()

    private var client: BillingClient? = null
    private var connected = false
    private var reconnectAttempt = 0
    private var reconnectJob: Job? = null
    private var purchaseContinuation: CancellableContinuation<Boolean>? = null

    /** The job that looks again for a pending purchase that has settled. */
    private var ownershipRecheckJob: Job? = null

    /**
     * The last product details Play gave us.
     *
     * Kept so the paywall can go back to showing the real price when the
     * reader closes the purchase sheet — a cancelled purchase must not leave
     * the button without its price.
     */
    private var lastDetails: ProductDetails? = null

    /**
     * Opens the connection and pre-loads the product details.
     *
     * Safe to call repeatedly: if the client is already connected nothing
     * happens, and if the client exists but the connection was lost, the
     * existing client is reconnected.
     */
    fun start() {
        if (connected) return
        val existing = client
        if (existing != null) {
            openConnection(existing)
            return
        }
        val pendingParams = PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts()
            .build()
        val billingClient = BillingClient.newBuilder(context)
            .enablePendingPurchases(pendingParams)
            .setListener(PurchasesUpdatedListener { result, purchases ->
                onPurchasesUpdated(result, purchases)
            })
            .build()
        client = billingClient
        openConnection(billingClient)
    }

    private fun openConnection(billingClient: BillingClient) {
        _state.value = BillingUiState.Connecting
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    connected = true
                    reconnectAttempt = 0
                    _state.value = BillingUiState.Idle
                    scope.launch {
                        refreshProduct()
                        checkOwnership()
                    }
                } else {
                    connected = false
                    _state.value = BillingUiState.Failed(
                        billingResult.debugMessage ?: "Play Billing is unavailable on this device."
                    )
                    if (isTransient(billingResult.responseCode)) scheduleReconnect()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Play dropped the connection. The documented recovery is to call
                // startConnection again, so do that on a backoff instead of
                // leaving the reader with a dead paywall.
                connected = false
                _state.value = BillingUiState.Failed("Play Billing disconnected.")
                scheduleReconnect()
            }
        })
    }

    /**
     * Schedules the next connection attempt.
     *
     * The wait doubles every time and is capped by [ReconnectPolicy]; after the
     * last attempt the app stops and asks the reader to try again.
     */
    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        val attempt = reconnectAttempt + 1
        reconnectAttempt = attempt
        if (!ReconnectPolicy.shouldRetry(attempt)) {
            _state.value = BillingUiState.Failed(
                "Could not reach Google Play. Check your connection, then try again or restore your purchase."
            )
            return
        }
        val waitMs = ReconnectPolicy.delayMs(attempt)
        _state.value = BillingUiState.Reconnecting(attempt, (waitMs / 1000L).toInt())
        reconnectJob = scope.launch {
            delay(waitMs)
            val billingClient = client
            if (billingClient != null) openConnection(billingClient) else start()
        }
    }

    /**
     * Whether a failure is worth retrying.
     *
     * A device with no Play Store, or an unsupported Play version, will not
     * start working by waiting; a busy or flaky service might.
     */
    private fun isTransient(responseCode: Int): Boolean =
        responseCode == BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE ||
            responseCode == BillingClient.BillingResponseCode.SERVICE_DISCONNECTED ||
            responseCode == BillingClient.BillingResponseCode.NETWORK_ERROR

    /**
     * Makes sure there is a live connection, reconnecting if needed.
     *
     * Returns the connected client, or null when Play could not be reached
     * within [timeoutMs].
     */
    private suspend fun awaitConnection(timeoutMs: Long = 10_000L): BillingClient? {
        client?.let { if (connected) return it }
        reconnectAttempt = 0
        start()
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while (System.nanoTime() < deadline) {
            delay(200)
            if (connected) return client
        }
        return null
    }

    /** Loads the product price so the paywall can show a real localised price. */
    suspend fun refreshProduct(): ProductDetails? {
        val billingClient = client ?: return null
        if (!connected) return null
        return suspendCancellableCoroutine { cont ->
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(PRODUCT_ID)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build()
                    )
                )
                .build()
            billingClient.queryProductDetailsAsync(params) { result, detailsResult ->
                val details = detailsResult?.productDetailsList?.firstOrNull()
                if (result.responseCode == BillingClient.BillingResponseCode.OK && details != null) {
                    lastDetails = details
                    _state.value = BillingUiState.Ready(
                        price = details.oneTimePurchaseOfferDetails?.formattedPrice ?: "",
                        title = details.title,
                        description = details.description
                    )
                    cont.resume(details)
                } else {
                    if (result.responseCode == BillingClient.BillingResponseCode.SERVICE_DISCONNECTED) {
                        connected = false
                        scheduleReconnect()
                    }
                    cont.resume(null)
                }
            }
        }
    }

    /** Launches the Play purchase sheet and suspends until the result is known. */
    suspend fun purchase(activity: Activity): Boolean {
        val billingClient = awaitConnection()
        if (billingClient == null) {
            _state.value = BillingUiState.Failed(
                "Google Play could not be reached. Check your connection and try again."
            )
            return false
        }
        val details = refreshProduct()
        if (details == null) {
            _state.value = BillingUiState.Failed(
                "The price could not be loaded from Google Play. Try again in a moment."
            )
            return false
        }
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()
        _state.value = BillingUiState.Flowing
        val launched = billingClient.launchBillingFlow(activity, flowParams)
        if (launched.responseCode != BillingClient.BillingResponseCode.OK) {
            _state.value = BillingUiState.Failed(
                launched.debugMessage ?: "The purchase sheet could not be opened."
            )
            return false
        }
        return suspendCancellableCoroutine { cont ->
            purchaseContinuation = cont
            cont.invokeOnCancellation { purchaseContinuation = null }
        }
    }

    /**
     * Re-checks Play for an existing purchase (used by "Restore").
     *
     * The response code is checked: a network failure is reported as a failure
     * rather than being passed off as "you own nothing".
     */
    suspend fun restore(): Boolean {
        val billingClient = awaitConnection()
        if (billingClient == null) {
            _state.value = BillingUiState.Failed(
                "Google Play could not be reached. Try again when you have a connection."
            )
            return false
        }
        return suspendCancellableCoroutine { cont ->
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
            billingClient.queryPurchasesAsync(params) { result, purchases ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    if (result.responseCode == BillingClient.BillingResponseCode.SERVICE_DISCONNECTED) {
                        connected = false
                        scheduleReconnect()
                    }
                    _state.value = BillingUiState.Failed(
                        result.debugMessage ?: "Google Play could not be reached. Try again."
                    )
                    cont.resume(false)
                    return@queryPurchasesAsync
                }
                val owned = ownsProduct(purchases)
                if (owned) {
                    acknowledge(purchases)
                    onOwnershipConfirmed()
                    _state.value = BillingUiState.Purchased(restored = true)
                } else {
                    _state.value = BillingUiState.Idle
                }
                cont.resume(owned)
            }
        }
    }

    /**
     * Checks whether Play already holds a purchase for this app.
     *
     * This runs whenever the connection comes up, so a purchase that completed
     * while the app was closed — or a pending purchase that has since settled —
     * still unlocks the app.
     */
    private suspend fun checkOwnership() {
        val billingClient = client ?: return
        if (!connected) return
        try {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
            val owned = suspendCancellableCoroutine { cont ->
                billingClient.queryPurchasesAsync(params) { result, purchases ->
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        cont.resume(false)
                        return@queryPurchasesAsync
                    }
                    val mine = ownsProduct(purchases)
                    if (mine) acknowledge(purchases)
                    cont.resume(mine)
                }
            }
            if (owned) {
                onOwnershipConfirmed()
                _state.value = BillingUiState.Purchased(restored = true)
            }
        } catch (error: Throwable) {
            Log.w(TAG, "ownership check failed: ${error.message}")
        }
    }

    /**
     * What Play told us about a purchase.
     *
     * The decision itself is made by [PurchaseClassifier], which is pure and
     * unit tested; this method only turns Play's objects into the facts it
     * needs and then acts on the answer.
     */
    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        val facts = purchases.orEmpty().facts()
        val callback = when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> CallbackResult.Ok
            BillingClient.BillingResponseCode.USER_CANCELED -> CallbackResult.Cancelled
            else -> CallbackResult.Error
        }

        when (PurchaseClassifier.classify(PRODUCT_ID, callback, facts)) {
            PurchaseOutcome.Purchased -> {
                acknowledge(purchases.orEmpty())
                purchaseContinuation?.resume(true)
                purchaseContinuation = null
                _state.value = BillingUiState.Purchased(restored = false)
            }

            PurchaseOutcome.Pending -> {
                purchaseContinuation?.resume(false)
                purchaseContinuation = null
                _state.value = BillingUiState.Pending(
                    "The payment is still processing. It will unlock on its own once Google Play confirms it."
                )
                // "On its own" has to be true: look again a little later, so a
                // purchase that settles while the app is open unlocks without
                // the reader doing anything.
                scheduleOwnershipRecheck()
            }

            PurchaseOutcome.Cancelled -> {
                purchaseContinuation?.resume(false)
                purchaseContinuation = null
                // The reader changed their mind; nothing is broken, and the
                // price must still be on the button.
                _state.value = readyState() ?: BillingUiState.Idle
            }

            PurchaseOutcome.Failed -> {
                purchaseContinuation?.resume(false)
                purchaseContinuation = null
                _state.value = BillingUiState.Failed(
                    result.debugMessage ?: "The purchase could not be completed."
                )
            }
        }
    }

    /** The paywall state with the real price, when Play has given us one. */
    private fun readyState(): BillingUiState.Ready? {
        val details = lastDetails ?: return null
        return BillingUiState.Ready(
            price = details.oneTimePurchaseOfferDetails?.formattedPrice ?: "",
            title = details.title,
            description = details.description
        )
    }

    /**
     * Looks again for a pending purchase that has since settled.
     *
     * Some payment methods take days. The promise in the message is that the
     * app unlocks on its own, so the app has to come back and look.
     */
    private fun scheduleOwnershipRecheck() {
        ownershipRecheckJob?.cancel()
        ownershipRecheckJob = scope.launch {
            delay(OWNERSHIP_RECHECK_MS)
            if (connected) checkOwnership()
        }
    }

    /**
     * Whether Play holds a **paid** purchase of our product.
     *
     * The same rule as [PurchaseClassifier] decides this, so there is exactly
     * one definition of "the reader owns it" in the app.
     */
    private fun ownsProduct(purchases: List<Purchase>): Boolean =
        PurchaseClassifier.classify(PRODUCT_ID, CallbackResult.Ok, purchases.facts()) ==
            PurchaseOutcome.Purchased

    /** Copies the two facts the decision needs out of Play's purchase objects. */
    private fun List<Purchase>.facts(): List<PurchaseFacts> = map { purchase ->
        PurchaseFacts(
            products = purchase.products,
            status = when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> PurchaseStatus.Purchased
                Purchase.PurchaseState.PENDING -> PurchaseStatus.Pending
                else -> PurchaseStatus.Other
            }
        )
    }

    private fun acknowledge(purchases: List<Purchase>) {
        val billingClient = client ?: return
        purchases.filter { !it.isAcknowledged }.forEach { purchase ->
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { result ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "acknowledge failed: ${result.debugMessage}")
                }
            }
        }
    }

    companion object {
        private const val TAG = "BillingManager"

        /** Must match the product id created in the Play Console. */
        const val PRODUCT_ID = "dao_fortune_full_access"

        /** How long to wait before looking again for a settling purchase. */
        private const val OWNERSHIP_RECHECK_MS = 15 * 60 * 1000L
    }
}
