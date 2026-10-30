package app.arthik.core

import app.arthik.core.match.Deduper
import app.arthik.core.match.DuplicateStrength
import app.arthik.core.match.TransferMatcher
import app.arthik.core.match.TxnRef
import app.arthik.core.model.Direction.CREDIT
import app.arthik.core.model.Direction.DEBIT
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Spec 11.1 / 11.2 / 13.6: duplicates, self-transfers, card payments and refunds. */
class MatchingTest {
    private val t0: LocalDateTime = LocalDateTime.of(2026, 10, 23, 10, 0)

    private fun ref(
        id: Long, account: Long = 1, dir: app.arthik.core.model.Direction = DEBIT, amount: Long = 310500,
        at: LocalDateTime = t0, reference: String? = null, merchant: String? = null,
    ) = TxnRef(id, account, dir, amount, at, reference, merchant)

    // ---- Deduper ----
    @Test fun `same reference and amount is a strong duplicate`() {
        // Spec 13.1: the ICICI CRED Club debit arrived twice with the same UPI reference.
        val first = ref(1, reference = "666206435157")
        val again = ref(2, reference = "666206435157", at = t0.plusHours(3))
        assertEquals(first to DuplicateStrength.STRONG, Deduper.find(again, listOf(first)))
    }

    @Test fun `leading zeros and case do not hide a strong duplicate`() {
        val first = ref(1, reference = "000000000001")
        assertEquals(DuplicateStrength.STRONG, Deduper.find(ref(2, reference = "1", at = t0.plusDays(1)), listOf(first))?.second)
        val utr = ref(3, reference = "hdfch00000000000")
        assertEquals(DuplicateStrength.STRONG, Deduper.find(ref(4, reference = "HDFCH00000000000"), listOf(utr))?.second)
    }

    @Test fun `same amount on the same account within 15 minutes without a conflicting reference is probable`() {
        val bank = ref(1, reference = "120807593798")
        val noRef = ref(2, at = t0.plusMinutes(14))
        assertEquals(bank to DuplicateStrength.PROBABLE, Deduper.find(noRef, listOf(bank)))
        assertEquals(DuplicateStrength.PROBABLE, Deduper.find(ref(3, at = t0.minusMinutes(15)), listOf(bank))?.second)
    }

    @Test fun `outside the window, on another account or in the other direction is not a duplicate`() {
        val bank = ref(1)
        assertNull(Deduper.find(ref(2, at = t0.plusMinutes(16)), listOf(bank)))
        assertNull(Deduper.find(ref(3, account = 2), listOf(bank)))
        assertNull(Deduper.find(ref(4, dir = CREDIT), listOf(bank)))
        assertNull(Deduper.find(ref(5, amount = 310600), listOf(bank)))
    }

    @Test fun `same amount and day with different references are two real transactions`() {
        // Spec 13.1: two Rs 440 debits to the same payee with different references.
        val a = ref(1, amount = 44000, reference = "213416112187")
        val b = ref(2, amount = 44000, reference = "213416112188", at = t0.plusMinutes(2))
        assertNull(Deduper.find(b, listOf(a)))
    }

    @Test fun `same reference with a different amount is not a strong duplicate`() {
        val a = ref(1, reference = "123456789012", account = 1)
        assertNull(Deduper.find(ref(2, reference = "123456789012", amount = 100, account = 2), listOf(a)))
    }

    // ---- TransferMatcher ----
    @Test fun `debit and credit of the same amount on two own accounts within 30 minutes is a self-transfer`() {
        val debit = ref(1, account = 1, dir = DEBIT, amount = 2100000)
        val credit = ref(2, account = 2, dir = CREDIT, amount = 2100000, at = t0.plusMinutes(29))
        assertEquals(credit, TransferMatcher.findSelfTransfer(debit, listOf(credit)))
        assertEquals(debit, TransferMatcher.findSelfTransfer(credit, listOf(debit)))
    }

    @Test fun `self-transfer needs another account, opposite direction and the window or the same UTR`() {
        val debit = ref(1, account = 1, amount = 2100000, reference = "627317559036")
        assertNull(TransferMatcher.findSelfTransfer(debit, listOf(ref(2, account = 1, dir = CREDIT, amount = 2100000))))
        assertNull(TransferMatcher.findSelfTransfer(debit, listOf(ref(3, account = 2, dir = DEBIT, amount = 2100000))))
        assertNull(TransferMatcher.findSelfTransfer(debit, listOf(ref(4, account = 2, dir = CREDIT, amount = 2100000, at = t0.plusHours(2)))))
        val sameUtrLater = ref(5, account = 2, dir = CREDIT, amount = 2100000, at = t0.plusHours(5), reference = "627317559036")
        assertEquals(sameUtrLater, TransferMatcher.findSelfTransfer(debit, listOf(sameUtrLater)))
    }

    @Test fun `card payment pairs with the closest card credit within 3 days`() {
        // Spec 13.6: ICICI debit Rs 1046 to CRED Club on 23-Oct, SBI Card payment received of the same amount.
        val bankDebit = ref(1, account = 1, amount = 104600)
        val far = ref(2, account = 9, dir = CREDIT, amount = 104600, at = t0.plusDays(3))
        val near = ref(3, account = 9, dir = CREDIT, amount = 104600, at = t0.plusHours(20))
        val tooLate = ref(4, account = 9, dir = CREDIT, amount = 104600, at = t0.plusDays(4))
        val otherAmount = ref(5, account = 9, dir = CREDIT, amount = 104500, at = t0.plusHours(1))
        assertEquals(near, TransferMatcher.findCardPayment(bankDebit, listOf(far, tooLate, otherAmount, near)))
        assertEquals(far, TransferMatcher.findCardPayment(bankDebit, listOf(far, tooLate)))
        assertNull(TransferMatcher.findCardPayment(bankDebit, listOf(tooLate, otherAmount)))
        assertEquals(far, TransferMatcher.findCardPayment(bankDebit, listOf(ref(6, account = 9, dir = CREDIT, amount = 104600, at = t0.minusDays(4)), far)))
    }

    @Test fun `refund matches the latest earlier debit to the same merchant within 60 days`() {
        val refund = ref(10, dir = CREDIT, amount = 86216, at = t0, merchant = "amazon")
        val older = ref(1, amount = 90000, at = t0.minusDays(20), merchant = "amazon")
        val latest = ref(2, amount = 86216, at = t0.minusDays(5), merchant = "amazon")
        val smaller = ref(3, amount = 50000, at = t0.minusDays(2), merchant = "amazon")
        val otherShop = ref(4, amount = 90000, at = t0.minusDays(1), merchant = "flipkart")
        val tooOld = ref(5, amount = 90000, at = t0.minusDays(61), merchant = "amazon")
        val afterRefund = ref(6, amount = 90000, at = t0.plusDays(1), merchant = "amazon")
        val credit = ref(7, dir = CREDIT, amount = 90000, at = t0.minusDays(1), merchant = "amazon")
        assertEquals(latest, TransferMatcher.findRefundOrigin(refund, listOf(older, latest, smaller, otherShop, tooOld, afterRefund, credit)))
        assertNull(TransferMatcher.findRefundOrigin(refund, listOf(smaller, otherShop, tooOld, afterRefund, credit)))
        assertNull(TransferMatcher.findRefundOrigin(refund.copy(merchantKey = null), listOf(older)))
    }
}
