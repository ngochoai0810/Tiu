package vn.edu.dut.tiu.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import vn.edu.dut.tiu.data.repository.AuthRepository
import vn.edu.dut.tiu.data.repository.BudgetRepository
import vn.edu.dut.tiu.data.repository.CategoryRepository
import vn.edu.dut.tiu.data.repository.FirebaseAuthRepository
import vn.edu.dut.tiu.data.repository.FirestoreBudgetRepository
import vn.edu.dut.tiu.data.repository.FirestoreCategoryRepository
import vn.edu.dut.tiu.data.repository.FirestoreTransactionRepository
import vn.edu.dut.tiu.data.repository.TransactionRepository

/**
 * Dependency Injection thủ công: nơi DUY NHẤT tạo các Repository.
 * ViewModel nhận Repository qua constructor (xem ViewModelFactories).
 */
class AppContainer {
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val uidProvider: () -> String? = { auth.currentUser?.uid }

    val authRepository: AuthRepository by lazy { FirebaseAuthRepository(auth, db) }
    val categoryRepository: CategoryRepository by lazy { FirestoreCategoryRepository(db, uidProvider) }
    val transactionRepository: TransactionRepository by lazy { FirestoreTransactionRepository(db, uidProvider) }
    val budgetRepository: BudgetRepository by lazy { FirestoreBudgetRepository(db, uidProvider) }
}
