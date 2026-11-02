package com.laki.daofortune.billing

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The purchase decision is the one place where a reader who has paid can be
 * left locked out, so the rules behind it are pinned down here — on the JVM,
 * without a device and without a Play Store.
 */
class PurchaseClassifierTest {

    private val product = "dao_fortune_full_access"

    private fun purchase(
        vararg products: String,
        status: PurchaseStatus
    ) = PurchaseFacts(products.toList(), status)

    @Test
    fun `a paid purchase unlocks`() {
        val outcome = PurchaseClassifier.classify(
            product,
            CallbackResult.Ok,
            listOf(purchase(product, status = PurchaseStatus.Purchased))
        )
        assertEquals(PurchaseOutcome.Purchased, outcome)
    }

    @Test
    fun `a paid purchase unlocks even when the callback was not ok`() {
        // The money is in the bank whatever Play says about the callback, and
        // the ownership check would find it later anyway.
        val outcome = PurchaseClassifier.classify(
            product,
            CallbackResult.Error,
            listOf(purchase(product, status = PurchaseStatus.Purchased))
        )
        assertEquals(PurchaseOutcome.Purchased, outcome)
    }

    @Test
    fun `a settling payment is pending, not a failure`() {
        val outcome = PurchaseClassifier.classify(
            product,
            CallbackResult.Ok,
            listOf(purchase(product, status = PurchaseStatus.Pending))
        )
        assertEquals(PurchaseOutcome.Pending, outcome)
    }

    @Test
    fun `a settling payment stays pending even when the callback failed`() {
        val outcome = PurchaseClassifier.classify(
            product,
            CallbackResult.Error,
            listOf(purchase(product, status = PurchaseStatus.Pending))
        )
        assertEquals(PurchaseOutcome.Pending, outcome)
    }

    @Test
    fun `a closed sheet is a cancellation, not a failure`() {
        val outcome = PurchaseClassifier.classify(product, CallbackResult.Cancelled, emptyList())
        assertEquals(PurchaseOutcome.Cancelled, outcome)
    }

    @Test
    fun `an empty purchase list with no error is still a failure`() {
        // Play said "ok" but there is nothing to unlock — the reader must be
        // told the purchase did not go through rather than left waiting.
        val outcome = PurchaseClassifier.classify(product, CallbackResult.Ok, emptyList())
        assertEquals(PurchaseOutcome.Failed, outcome)
    }

    @Test
    fun `an error with no purchase is a failure`() {
        val outcome = PurchaseClassifier.classify(product, CallbackResult.Error, emptyList())
        assertEquals(PurchaseOutcome.Failed, outcome)
    }

    @Test
    fun `a purchase of another product never unlocks`() {
        val outcome = PurchaseClassifier.classify(
            product,
            CallbackResult.Ok,
            listOf(purchase("some_other_app_sku", status = PurchaseStatus.Purchased))
        )
        assertEquals(PurchaseOutcome.Failed, outcome)
    }

    @Test
    fun `a purchase of another product does not block our own`() {
        val outcome = PurchaseClassifier.classify(
            product,
            CallbackResult.Ok,
            listOf(
                purchase("some_other_app_sku", status = PurchaseStatus.Purchased),
                purchase(product, status = PurchaseStatus.Purchased)
            )
        )
        assertEquals(PurchaseOutcome.Purchased, outcome)
    }

    @Test
    fun `a purchase in an unknown state is not an unlock`() {
        val outcome = PurchaseClassifier.classify(
            product,
            CallbackResult.Ok,
            listOf(purchase(product, status = PurchaseStatus.Other))
        )
        assertEquals(PurchaseOutcome.Failed, outcome)
    }
}
