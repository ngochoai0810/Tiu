package vn.edu.dut.tiu.domain

import vn.edu.dut.tiu.util.DateUtils
import vn.edu.dut.tiu.util.MoneyFormat

/** Kiểm tra dữ liệu nhập của form Thêm/Sửa giao dịch trước khi ghi xuống Firestore. */
object TransactionValidator {
    const val MAX_AMOUNT = 1_000_000_000L // 1 tỷ đồng
    const val MAX_NOTE_LENGTH = 200

    enum class Field { AMOUNT, CATEGORY, NOTE, DATE }

    data class Result(val amount: Long?, val errors: Map<Field, String>) {
        val isValid: Boolean get() = errors.isEmpty()
    }

    fun validate(
        amountText: String,
        categoryId: String?,
        note: String,
        dateMillis: Long,
        nowMillis: Long,
    ): Result {
        val errors = mutableMapOf<Field, String>()

        val amount = MoneyFormat.parse(amountText)
        when {
            amountText.isBlank() || amount == null -> errors[Field.AMOUNT] = "Nhập số tiền"
            amount <= 0L -> errors[Field.AMOUNT] = "Số tiền phải lớn hơn 0"
            amount > MAX_AMOUNT -> errors[Field.AMOUNT] = "Số tiền tối đa 1.000.000.000 ₫"
        }

        if (categoryId.isNullOrBlank()) errors[Field.CATEGORY] = "Chọn danh mục"

        if (note.length > MAX_NOTE_LENGTH) errors[Field.NOTE] = "Ghi chú tối đa $MAX_NOTE_LENGTH ký tự"

        val today = DateUtils.toLocalDate(nowMillis)
        if (DateUtils.toLocalDate(dateMillis).isAfter(today)) errors[Field.DATE] = "Không chọn ngày trong tương lai"

        return Result(amount = if (Field.AMOUNT in errors) null else amount, errors = errors)
    }
}
