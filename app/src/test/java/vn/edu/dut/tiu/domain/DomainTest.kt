package vn.edu.dut.tiu.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.edu.dut.tiu.data.model.Transaction
import vn.edu.dut.tiu.data.model.TransactionType
import vn.edu.dut.tiu.domain.TransactionValidator.Field
import vn.edu.dut.tiu.util.MoneyFormat
import java.time.LocalDate
import java.time.YearMonth

class MoneyFormatTest {
    @Test fun formatsWithDotSeparators() {
        assertEquals("0 ₫", MoneyFormat.format(0))
        assertEquals("850 ₫", MoneyFormat.format(850))
        assertEquals("85.000 ₫", MoneyFormat.format(85_000))
        assertEquals("1.284.000 ₫", MoneyFormat.format(1_284_000))
        assertEquals("-3.000.000 ₫", MoneyFormat.format(-3_000_000))
    }

    @Test fun signedAmounts() {
        assertEquals("+3.000.000 ₫", MoneyFormat.signed(3_000_000, isIncome = true))
        assertEquals("-85.000 ₫", MoneyFormat.signed(85_000, isIncome = false))
    }

    @Test fun parsesUserInput() {
        assertEquals(85_000L, MoneyFormat.parse("85.000 ₫"))
        assertEquals(1_284_000L, MoneyFormat.parse("1,284,000"))
        assertNull(MoneyFormat.parse(""))
        assertNull(MoneyFormat.parse("abc"))
    }
}

class TransactionValidatorTest {
    private val now = System.currentTimeMillis()
    private val day = 24 * 60 * 60 * 1000L

    @Test fun validInput() {
        val r = TransactionValidator.validate("85.000", "an_uong", "Cà phê", now, now)
        assertTrue(r.isValid)
        assertEquals(85_000L, r.amount)
    }

    @Test fun emptyAmountAndMissingCategory() {
        val r = TransactionValidator.validate("", null, "", now, now)
        assertFalse(r.isValid)
        assertEquals(setOf(Field.AMOUNT, Field.CATEGORY), r.errors.keys)
        assertNull(r.amount)
    }

    @Test fun zeroAndTooLargeAmount() {
        assertTrue(Field.AMOUNT in TransactionValidator.validate("0", "c", "", now, now).errors)
        assertTrue(Field.AMOUNT in TransactionValidator.validate("2.000.000.000", "c", "", now, now).errors)
    }

    @Test fun noteTooLongAndFutureDate() {
        val r = TransactionValidator.validate("1000", "c", "x".repeat(201), now + 3 * day, now)
        assertEquals(setOf(Field.NOTE, Field.DATE), r.errors.keys)
    }

    @Test fun pastDateIsAllowed() {
        assertTrue(TransactionValidator.validate("1000", "c", "", now - 30 * day, now).isValid)
    }
}

class BudgetCalculatorTest {
    private fun tx(amount: Long, cat: String, type: TransactionType = TransactionType.EXPENSE) =
        Transaction(amount = amount, categoryId = cat, type = type)

    private val txs = listOf(
        tx(540_000, "an_uong"), tx(420_000, "mua_sam"), tx(100_000, "an_uong"),
        tx(3_000_000, "tro_cap", TransactionType.INCOME),
    )

    @Test fun totalsIgnoreOtherType() {
        assertEquals(1_060_000L, BudgetCalculator.totalExpense(txs))
        assertEquals(3_000_000L, BudgetCalculator.totalIncome(txs))
        assertEquals(mapOf("an_uong" to 640_000L, "mua_sam" to 420_000L), BudgetCalculator.expenseByCategory(txs))
    }

    @Test fun progressStatus() {
        assertEquals(BudgetProgress.Status.OK, BudgetProgress(3_000_000, 1_284_000).status)
        assertEquals(42, BudgetProgress(3_000_000, 1_284_000).percent)
        assertEquals(BudgetProgress.Status.WARNING, BudgetProgress(600_000, 540_000).status) // 90%
        assertEquals(BudgetProgress.Status.OVER, BudgetProgress(500_000, 540_000).status)
        assertEquals(0, BudgetProgress(0, 100).percent) // chưa đặt hạn mức
    }

    @Test fun daysLeftAndDailyAllowance() {
        val june = YearMonth.of(2025, 6)
        val june14 = LocalDate.of(2025, 6, 14)
        assertEquals(17, BudgetCalculator.daysLeft(june, june14)) // 14 -> 30 tính cả hôm nay
        assertEquals(0, BudgetCalculator.daysLeft(june, LocalDate.of(2025, 7, 1)))
        // còn 1.716.000 / 17 ngày
        assertEquals(100_941L, BudgetCalculator.dailyAllowance(BudgetProgress(3_000_000, 1_284_000), june, june14))
        assertEquals(0L, BudgetCalculator.dailyAllowance(BudgetProgress(500_000, 600_000), june, june14))
    }
}
