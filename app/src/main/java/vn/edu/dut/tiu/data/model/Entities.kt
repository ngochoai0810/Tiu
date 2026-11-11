package vn.edu.dut.tiu.data.model

/*
 * Các thực thể trong ERD. Thời gian lưu dạng epoch millis (Long) ở tầng model;
 * Firestore lưu dạng Timestamp (xem FirestoreMapper).
 */

/** Bảng USER – document `users/{uid}`. */
data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val avatarUrl: String? = null,
    val privacy: Privacy = Privacy.PRIVATE,
    val notifyBudget: Boolean = true,
    val notifyDaily: Boolean = true,
    val createdAt: Long = 0L,
)

/** Bảng CATEGORY – document `users/{uid}/categories/{id}`. */
data class Category(
    val id: String = "",
    val name: String = "",
    val icon: String = "",
    val colorHex: String = "#F07D45",
    val type: TransactionType = TransactionType.EXPENSE,
    val isDefault: Boolean = false,
    val createdAt: Long = 0L,
)

/**
 * Bảng TRANSACTION – document `users/{uid}/transactions/{id}`.
 * [categoryName] được phi chuẩn hóa (lưu kèm) để hiển thị danh sách không cần "join".
 */
data class Transaction(
    val id: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val amount: Long = 0L,
    val categoryId: String = "",
    val categoryName: String = "",
    val note: String = "",
    val date: Long = 0L,
    val imageUrl: String? = null,
    val recurringId: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/**
 * Bảng BUDGET – document `users/{uid}/budgets/{month}_{categoryId|ALL}`.
 * [categoryId] = null nghĩa là ngân sách tổng của cả tháng.
 */
data class Budget(
    val id: String = "",
    val month: String = "",
    val categoryId: String? = null,
    val limitAmount: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    val isTotal: Boolean get() = categoryId == null

    companion object {
        const val TOTAL_KEY = "ALL"
        fun idFor(month: String, categoryId: String?) = "${month}_${categoryId ?: TOTAL_KEY}"
    }
}

/* ---- Các thực thể dưới đây đã có trong ERD, CRUD sẽ làm ở tuần 4–5 ---- */

/** Bảng NOTIFICATION – `users/{uid}/notifications/{id}`. */
data class AppNotification(
    val id: String = "",
    val type: NotificationType = NotificationType.REMINDER,
    val title: String = "",
    val body: String = "",
    val isRead: Boolean = false,
    val refId: String? = null,
    val createdAt: Long = 0L,
)

/** Bảng RECURRING_TRANSACTION – `users/{uid}/recurring/{id}`. */
data class RecurringTransaction(
    val id: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val amount: Long = 0L,
    val categoryId: String = "",
    val note: String = "",
    val frequency: RecurringFrequency = RecurringFrequency.MONTHLY,
    val nextDate: Long = 0L,
    val active: Boolean = true,
)

/** Bảng FRIENDSHIP – `friendships/{id}` (collection gốc, dùng chung cho 2 người). */
data class Friendship(
    val id: String = "",
    val requesterId: String = "",
    val addresseeId: String = "",
    val status: FriendshipStatus = FriendshipStatus.PENDING,
    val createdAt: Long = 0L,
)

/** Danh mục mặc định được tạo cho mỗi tài khoản mới. */
object DefaultCategories {
    val all: List<Category> = listOf(
        Category(id = "an_uong", name = "Ăn uống", icon = "🍜", colorHex = "#F07D45", type = TransactionType.EXPENSE),
        Category(id = "mua_sam", name = "Mua sắm", icon = "🛍️", colorHex = "#FFB780", type = TransactionType.EXPENSE),
        Category(id = "di_chuyen", name = "Di chuyển", icon = "🛵", colorHex = "#806B5D", type = TransactionType.EXPENSE),
        Category(id = "hoc_tap", name = "Học tập", icon = "📚", colorHex = "#83C69B", type = TransactionType.EXPENSE),
        Category(id = "nha_o", name = "Nhà ở", icon = "🏠", colorHex = "#4A3427", type = TransactionType.EXPENSE),
        Category(id = "giai_tri", name = "Giải trí", icon = "🎮", colorHex = "#E96F62", type = TransactionType.EXPENSE),
        Category(id = "khac", name = "Khác", icon = "✨", colorHex = "#FBD9BC", type = TransactionType.EXPENSE),
        Category(id = "tro_cap", name = "Trợ cấp gia đình", icon = "👪", colorHex = "#83C69B", type = TransactionType.INCOME),
        Category(id = "lam_them", name = "Lương làm thêm", icon = "💼", colorHex = "#83C69B", type = TransactionType.INCOME),
        Category(id = "hoc_bong", name = "Học bổng", icon = "🎓", colorHex = "#83C69B", type = TransactionType.INCOME),
    ).map { it.copy(isDefault = true) }
}
