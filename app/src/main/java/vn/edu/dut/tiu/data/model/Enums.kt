package vn.edu.dut.tiu.data.model

/** Loại giao dịch / danh mục: chi tiêu hay thu nhập. */
enum class TransactionType { EXPENSE, INCOME }

/** Chế độ chia sẻ nhật ký chi tiêu với bạn bè. */
enum class Privacy { PRIVATE, FRIENDS }

enum class NotificationType { BUDGET_WARNING, REMINDER, WEEKLY_SUMMARY, FRIEND_REQUEST }

enum class RecurringFrequency { WEEKLY, MONTHLY }

enum class FriendshipStatus { PENDING, ACCEPTED }
