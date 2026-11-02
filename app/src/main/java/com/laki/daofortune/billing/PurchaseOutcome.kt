package com.laki.daofortune.billing

/**
 * How a purchase stands, as far as this app is concerned.
 *
 * Play's own `Purchase` class cannot be built in a JVM test, so the two facts
 * the decision needs — which product it is and what state it is in — are
 * copied out of it first. Everything in this file is plain Kotlin with no
 * Android and no Play types, so the money path can be tested on the JVM
 * instead of only on a device with a real Play Store.
 */
enum class PurchaseStatus {
    /** The payment is complete. */
    Purchased,

    /** Play is still settling the payment — some payment methods take days. */
    Pending,

    /** Anything else Play can report. */
    Other
}

/** The parts of a Play purchase that the decision needs. */
data class PurchaseFacts(
    val products: List<String>,
    val status: PurchaseStatus
)

/** How the Play callback itself went. */
enum class CallbackResult {
    /** Play answered normally. */
    Ok,

    /** The reader closed the purchase sheet. */
    Cancelled,

    /** Play could not complete the purchase. */
    Error
}

/** What a purchases callback means for the reader. */
sealed interface PurchaseOutcome {
    /** The reader bought the product: acknowledge it and unlock the app. */
    data object Purchased : PurchaseOutcome

    /**
     * Play is still settling the payment.
     *
     * It is not an unlock yet, and it is not a failure either — the reader is
     * told to wait, and the app unlocks on its own once Play confirms it.
     */
    data object Pending : PurchaseOutcome

    /** The reader closed the purchase sheet. Nothing happened, and nothing is wrong. */
    data object Cancelled : PurchaseOutcome

    /** The purchase did not go through. */
    data object Failed : PurchaseOutcome
}

/**
 * Works out what a Play purchases callback means.
 *
 * Pure on purpose: this is the one place where a reader who has paid can be
 * left locked out, so the rules are written down where a JVM test can pin
 * them down.
 */
object PurchaseClassifier {

    /**
     * The decision, in order:
     *
     *  1. a purchase of ours in the state *Purchased* is money in the bank —
     *     it unlocks the app, whatever the callback code says;
     *  2. a purchase of ours in the state *Pending* is still settling — the
     *     reader is told to wait, and the unlock happens when Play confirms it;
     *  3. otherwise a cancelled callback is the reader changing their mind;
     *  4. anything else is a failure.
     *
     * A purchase of some *other* product is ignored: this app has exactly one
     * product, and an unrelated purchase must never unlock it.
     */
    fun classify(
        productId: String,
        callback: CallbackResult,
        purchases: List<PurchaseFacts>
    ): PurchaseOutcome {
        val mine = purchases.filter { it.products.contains(productId) }
        return when {
            mine.any { it.status == PurchaseStatus.Purchased } -> PurchaseOutcome.Purchased
            mine.any { it.status == PurchaseStatus.Pending } -> PurchaseOutcome.Pending
            callback == CallbackResult.Cancelled -> PurchaseOutcome.Cancelled
            else -> PurchaseOutcome.Failed
        }
    }
}
