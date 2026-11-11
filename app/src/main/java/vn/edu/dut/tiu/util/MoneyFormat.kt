package vn.edu.dut.tiu.util

import kotlin.math.abs

/** Định dạng tiền Việt: 85000 -> "85.000 ₫". Không phụ thuộc Locale của máy. */
object MoneyFormat {

    fun group(amount: Long): String {
        val digits = abs(amount).toString()
        val sb = StringBuilder()
        digits.forEachIndexed { i, c ->
            if (i > 0 && (digits.length - i) % 3 == 0) sb.append('.')
            sb.append(c)
        }
        return (if (amount < 0) "-" else "") + sb
    }

    fun format(amount: Long): String = "${group(amount)} ₫"

    /** Có dấu +/- theo thu/chi, dùng trong danh sách giao dịch. */
    fun signed(amount: Long, isIncome: Boolean): String =
        (if (isIncome) "+" else "-") + format(abs(amount))

    /** "85.000 ₫" / "85,000" / "85000" -> 85000. Trả null nếu không có chữ số nào hoặc quá dài. */
    fun parse(text: String): Long? {
        val digits = text.filter(Char::isDigit)
        if (digits.isEmpty()) return null
        return digits.toLongOrNull()
    }
}
