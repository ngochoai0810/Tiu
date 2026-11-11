package vn.edu.dut.tiu.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import vn.edu.dut.tiu.data.model.Budget
import vn.edu.dut.tiu.data.model.Category
import vn.edu.dut.tiu.data.model.Privacy
import vn.edu.dut.tiu.data.model.Transaction
import vn.edu.dut.tiu.data.model.TransactionType
import vn.edu.dut.tiu.data.model.UserProfile
import java.util.Date

/** Chuyển đổi qua lại giữa model Kotlin và document Firestore. */
object FirestoreMapper {

    private fun Long.toTimestamp() = Timestamp(Date(this))

    private fun DocumentSnapshot.millis(field: String): Long =
        getTimestamp(field)?.toDate()?.time ?: 0L

    private inline fun <reified T : Enum<T>> DocumentSnapshot.enumOf(field: String, default: T): T =
        getString(field)?.let { v -> enumValues<T>().firstOrNull { it.name == v } } ?: default

    // ---------- USER ----------
    fun toMap(u: UserProfile): Map<String, Any?> = mapOf(
        "uid" to u.uid,
        "displayName" to u.displayName,
        "email" to u.email,
        "avatarUrl" to u.avatarUrl,
        "privacy" to u.privacy.name,
        "notifyBudget" to u.notifyBudget,
        "notifyDaily" to u.notifyDaily,
        "createdAt" to u.createdAt.toTimestamp(),
    )

    fun toUser(d: DocumentSnapshot): UserProfile? {
        if (!d.exists()) return null
        return UserProfile(
            uid = d.id,
            displayName = d.getString("displayName").orEmpty(),
            email = d.getString("email").orEmpty(),
            avatarUrl = d.getString("avatarUrl"),
            privacy = d.enumOf("privacy", Privacy.PRIVATE),
            notifyBudget = d.getBoolean("notifyBudget") ?: true,
            notifyDaily = d.getBoolean("notifyDaily") ?: true,
            createdAt = d.millis("createdAt"),
        )
    }

    // ---------- CATEGORY ----------
    fun toMap(c: Category): Map<String, Any?> = mapOf(
        "name" to c.name,
        "icon" to c.icon,
        "colorHex" to c.colorHex,
        "type" to c.type.name,
        "isDefault" to c.isDefault,
        "createdAt" to c.createdAt.toTimestamp(),
    )

    fun toCategory(d: DocumentSnapshot): Category? {
        val name = d.getString("name") ?: return null
        return Category(
            id = d.id,
            name = name,
            icon = d.getString("icon").orEmpty(),
            colorHex = d.getString("colorHex") ?: "#F07D45",
            type = d.enumOf("type", TransactionType.EXPENSE),
            isDefault = d.getBoolean("isDefault") ?: false,
            createdAt = d.millis("createdAt"),
        )
    }

    // ---------- TRANSACTION ----------
    fun toMap(t: Transaction): Map<String, Any?> = mapOf(
        "type" to t.type.name,
        "amount" to t.amount,
        "categoryId" to t.categoryId,
        "categoryName" to t.categoryName,
        "note" to t.note,
        "date" to t.date.toTimestamp(),
        "imageUrl" to t.imageUrl,
        "recurringId" to t.recurringId,
        "createdAt" to t.createdAt.toTimestamp(),
        "updatedAt" to t.updatedAt.toTimestamp(),
    )

    fun toTransaction(d: DocumentSnapshot): Transaction? {
        val amount = d.getLong("amount") ?: return null
        return Transaction(
            id = d.id,
            type = d.enumOf("type", TransactionType.EXPENSE),
            amount = amount,
            categoryId = d.getString("categoryId").orEmpty(),
            categoryName = d.getString("categoryName").orEmpty(),
            note = d.getString("note").orEmpty(),
            date = d.millis("date"),
            imageUrl = d.getString("imageUrl"),
            recurringId = d.getString("recurringId"),
            createdAt = d.millis("createdAt"),
            updatedAt = d.millis("updatedAt"),
        )
    }

    // ---------- BUDGET ----------
    fun toMap(b: Budget): Map<String, Any?> = mapOf(
        "month" to b.month,
        "categoryId" to b.categoryId,
        "limitAmount" to b.limitAmount,
        "createdAt" to b.createdAt.toTimestamp(),
        "updatedAt" to b.updatedAt.toTimestamp(),
    )

    fun toBudget(d: DocumentSnapshot): Budget? {
        val month = d.getString("month") ?: return null
        return Budget(
            id = d.id,
            month = month,
            categoryId = d.getString("categoryId"),
            limitAmount = d.getLong("limitAmount") ?: 0L,
            createdAt = d.millis("createdAt"),
            updatedAt = d.millis("updatedAt"),
        )
    }
}
