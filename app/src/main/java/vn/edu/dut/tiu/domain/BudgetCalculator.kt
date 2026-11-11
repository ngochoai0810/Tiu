package vn.edu.dut.tiu.domain

import vn.edu.dut.tiu.data.model.Transaction
import vn.edu.dut.tiu.data.model.TransactionType
import java.time.LocalDate
import java.time.YearMonth

/** Tiến độ một ngân sách: đã chi bao nhiêu so với hạn mức. */
data class BudgetProgress(val limit: Long, val spent: Long) {
    val percent: Int get() = if (limit <= 0L) 0 else ((spent * 100) / limit).toInt()
    val remaining: Long get() = limit - spent
    val status: Status
        get() = when {
            limit > 0 && spent > limit -> Status.OVER
            percent >= WARNING_PERCENT -> Status.WARNING
            else -> Status.OK
        }

    enum class Status { OK, WARNING, OVER }

    companion object { const val WARNING_PERCENT = 80 }
}

object BudgetCalculator {

    /** Tổng chi theo từng danh mục (chỉ tính giao dịch CHI TIÊU). */
    fun expenseByCategory(transactions: List<Transaction>): Map<String, Long> =
        transactions.filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.categoryId }
            .mapValues { (_, list) -> list.sumOf { it.amount } }

    fun totalExpense(transactions: List<Transaction>): Long =
        transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

    fun totalIncome(transactions: List<Transaction>): Long =
        transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }

    /** Số ngày còn lại trong tháng (tính cả hôm nay). Tháng đã qua = 0, tháng tương lai = cả tháng. */
    fun daysLeft(month: YearMonth, today: LocalDate): Int {
        val current = YearMonth.from(today)
        return when {
            month.isBefore(current) -> 0
            month.isAfter(current) -> month.lengthOfMonth()
            else -> month.lengthOfMonth() - today.dayOfMonth + 1
        }
    }

    /** Hạn mức chi mỗi ngày còn lại để không vượt ngân sách (tính năng "hạn mức chi tiêu mỗi ngày"). */
    fun dailyAllowance(progress: BudgetProgress, month: YearMonth, today: LocalDate): Long {
        val days = daysLeft(month, today)
        if (days <= 0 || progress.remaining <= 0) return 0L
        return progress.remaining / days
    }
}
