package vn.edu.dut.tiu.util

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object DateUtils {
    var zone: ZoneId = ZoneId.systemDefault()

    private val dayMonth = DateTimeFormatter.ofPattern("dd/MM")
    private val fullDate = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun toLocalDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun startOfDay(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    /** Khoảng [đầu tháng, đầu tháng sau) theo millis – dùng cho truy vấn Firestore. */
    fun monthRange(month: YearMonth): Pair<Long, Long> =
        startOfDay(month.atDay(1)) to startOfDay(month.plusMonths(1).atDay(1))

    /** Khóa tháng lưu trong BUDGET: "2025-06". */
    fun monthKey(month: YearMonth): String = "%04d-%02d".format(month.year, month.monthValue)

    fun monthLabel(month: YearMonth): String = "Tháng ${month.monthValue}, ${month.year}"

    /** "Hôm nay, 03/11" · "Hôm qua, 02/11" · "Thứ 2, 01/11". */
    fun dayLabel(date: LocalDate, today: LocalDate = LocalDate.now(zone)): String {
        val dm = date.format(dayMonth)
        return when (date) {
            today -> "Hôm nay, $dm"
            today.minusDays(1) -> "Hôm qua, $dm"
            else -> "${weekday(date)}, $dm"
        }
    }

    fun shortDate(millis: Long, today: LocalDate = LocalDate.now(zone)): String {
        val date = toLocalDate(millis)
        return if (date == today) "Hôm nay, ${date.format(dayMonth)}" else date.format(fullDate)
    }

    private fun weekday(date: LocalDate): String {
        val v = date.dayOfWeek.value // 1 = Thứ 2 ... 7 = Chủ nhật
        return if (v == 7) "Chủ nhật" else "Thứ ${v + 1}"
    }
}
