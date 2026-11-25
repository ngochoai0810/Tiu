package vn.edu.dut.tiu.domain

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/** A recognized line and its bounds in the source image. */
data class ReceiptLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

object ReceiptAmountParser {
    private val money = Regex("""(?<![\d.,:/])(?:\d{1,3}(?:[.,]\d{3})+|\d{1,3}(?: \d{3})+|\d{4,9})(?:[.,]00)?(?![\d.,:/])""")
    private val excluded = Regex("""\b(tam tinh|subtotal|tien khach|khach dua|tien mat|tien thua|tra lai|cash|change|dien thoai|tel|phone|mst|ma so thue|so hoa don|hoa don|receipt|invoice|ngay|date|gio|time|ban|so hd|don gia|so luong|sl)\b|https?://|[/@=]""")
    private val strongTotal = Regex("""\b(tong cong|t[. ]*cong|tong tien|tong thanh toan|can thanh toan|phai thanh toan|grand total|amount due|total due|t\.?\s*tien)\b""")
    private val total = Regex("""\b(thanh toan|total|tong)\b""")
    private val lineTotal = Regex("""\bthanh tien\b""")

    private data class Candidate(val amount: Long, val score: Int, val top: Int)

    fun extract(lines: List<ReceiptLine>): Long? {
        val ordered = lines.sortedBy { it.top }
        val candidates = mutableListOf<Candidate>()
        for (label in ordered) {
            val normalized = normalize(label.text)
            if (excluded.containsMatchIn(normalized)) continue
            val priority = when {
                strongTotal.containsMatchIn(normalized) -> 100
                total.containsMatchIn(normalized) -> 90
                lineTotal.containsMatchIn(normalized) -> 65
                else -> continue
            }
            val inline = amounts(label.text)
            if (inline.size == 1) {
                candidates += Candidate(inline.single(), priority + 10, label.top)
                continue
            }
            // Column headings such as "Thành tiền" cannot identify the receipt total alone.
            if (priority < 90 || inline.isNotEmpty()) continue
            val height = (label.bottom - label.top).coerceAtLeast(1)
            val center = (label.top + label.bottom) / 2
            val sameRow = ordered.filter { value ->
                value !== label && value.left >= label.right - height &&
                    abs((value.top + value.bottom) / 2 - center) <= height * 0.7 &&
                    !excluded.containsMatchIn(normalize(value.text))
            }.flatMap { amounts(it.text) }.distinct()
            if (sameRow.size == 1) {
                candidates += Candidate(sameRow.single(), priority + 5, label.top)
                continue
            }
            if (sameRow.isNotEmpty()) continue
            val below = ordered.filter { value ->
                value.top >= label.bottom && value.top - label.bottom <= height * 2 &&
                    value.right >= label.left &&
                    isAmountOnly(value.text)
            }.flatMap { amounts(it.text) }.distinct()
            if (below.size == 1) candidates += Candidate(below.single(), priority, label.top)
        }
        // Có dòng "Tổng ..." đọc được số tiền -> tin dòng đó, không lấy số lớn nhất trên hóa đơn
        // (mã vạch, mã đơn in cuối hóa đơn thường lớn hơn tổng tiền).
        val totalAmounts = candidates.filter { it.score >= 90 }.map { it.amount }
        if (totalAmounts.isNotEmpty()) return totalAmounts.max()

        // OCR often separates the total label and amount into distant blocks.
        // Fallback: largest money-looking amount, skipping long bare digit runs (barcodes, order ids).
        val eligibleAmounts = ordered.filter {
            !excluded.containsMatchIn(normalize(it.text))
        }.flatMap { amounts(it.text, allowBareLong = false) }
        return (eligibleAmounts + candidates.map { it.amount }).maxOrNull()
    }

    private fun amounts(text: String, allowBareLong: Boolean = true): List<Long> = money.findAll(text).mapNotNull { match ->
        val raw = match.value.replace(Regex("""[.,]00$"""), "")
        if (!allowBareLong && raw.length >= 7 && raw.all(Char::isDigit)) return@mapNotNull null
        raw.filter(Char::isDigit).toLongOrNull()?.takeIf { it in 1_000..999_999_999 }
    }.toList()

    private fun isAmountOnly(text: String): Boolean {
        val normalized = normalize(text)
        val remainder = money.replace(normalized, "").replace(Regex("""vnd|dong|[₫d\s:=-]"""), "")
        return remainder.isEmpty() && amounts(text).size == 1
    }

    private fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("""\p{M}+"""), "")
        .lowercase(Locale.ROOT)
        .replace('đ', 'd')
}
