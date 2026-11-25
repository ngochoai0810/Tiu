package vn.edu.dut.tiu

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import vn.edu.dut.tiu.data.model.Transaction
import vn.edu.dut.tiu.data.model.TransactionType
import vn.edu.dut.tiu.data.model.UserProfile
import vn.edu.dut.tiu.data.repository.AuthRepository
import vn.edu.dut.tiu.domain.BudgetCalculator
import vn.edu.dut.tiu.domain.BudgetProgress
import vn.edu.dut.tiu.domain.ReceiptAmountParser
import vn.edu.dut.tiu.domain.ReceiptLine
import vn.edu.dut.tiu.domain.TransactionValidator
import vn.edu.dut.tiu.domain.TransactionValidator.Field
import vn.edu.dut.tiu.data.model.DefaultCategories
import vn.edu.dut.tiu.ui.FakeCategoryRepository
import vn.edu.dut.tiu.ui.FakeTransactionRepository
import vn.edu.dut.tiu.ui.MainDispatcherRule
import vn.edu.dut.tiu.ui.auth.AuthViewModel
import vn.edu.dut.tiu.ui.home.HomeItem
import vn.edu.dut.tiu.ui.home.SearchViewModel
import vn.edu.dut.tiu.util.DateUtils
import java.time.LocalDate
import java.time.YearMonth

/**
 * Tuần 5 – test case biên / sai / trống cho các chức năng chính.
 * Mã TC-xx trùng với bảng test case trong file Excel kiểm thử.
 */
class TransactionBoundaryTest {
    private val now = System.currentTimeMillis()
    private val day = 24 * 60 * 60 * 1000L
    private fun v(amount: String, cat: String? = "an_uong", note: String = "", date: Long = now) =
        TransactionValidator.validate(amount, cat, note, date, now)

    @Test fun tc_tx_amountBoundaries() {
        assertTrue(v("1").isValid)                                  // biên dưới hợp lệ
        assertTrue(v("1.000.000.000").isValid)                      // biên trên hợp lệ
        assertEquals(Field.AMOUNT, v("1.000.000.001").errors.keys.single()) // vượt biên trên
        assertEquals(Field.AMOUNT, v("0").errors.keys.single())     // biên dưới không hợp lệ
        assertEquals(Field.AMOUNT, v("").errors.keys.single())      // để trống
        assertEquals(Field.AMOUNT, v("abc").errors.keys.single())   // nhập sai kiểu
    }

    @Test fun tc_tx_noteAndCategoryAndDate() {
        assertTrue(v("10000", note = "a".repeat(200)).isValid)
        assertTrue(Field.NOTE in v("10000", note = "a".repeat(201)).errors)
        assertTrue(Field.CATEGORY in v("10000", cat = null).errors)
        assertTrue(Field.CATEGORY in v("10000", cat = " ").errors)
        assertTrue(v("10000", date = now).isValid)                  // hôm nay
        assertTrue(Field.DATE in v("10000", date = now + day).errors) // ngày mai
    }
}

class BudgetBoundaryTest {
    @Test fun tc_budget_warningThresholds() {
        assertEquals(BudgetProgress.Status.OK, BudgetProgress(100, 79).status)
        assertEquals(BudgetProgress.Status.WARNING, BudgetProgress(100, 80).status)
        assertEquals(BudgetProgress.Status.WARNING, BudgetProgress(100, 100).status)
        assertEquals(BudgetProgress.Status.OVER, BudgetProgress(100, 101).status)
        assertEquals(BudgetProgress.Status.OK, BudgetProgress(0, 50).status) // chưa đặt hạn mức
    }

    @Test fun tc_budget_dailyAllowanceEdges() {
        val m = YearMonth.of(2026, 11)
        val p = BudgetProgress(3_000_000, 1_000_000)
        assertEquals(2_000_000L, BudgetCalculator.dailyAllowance(p, m, LocalDate.of(2026, 11, 30))) // ngày cuối
        assertEquals(0L, BudgetCalculator.dailyAllowance(p, m, LocalDate.of(2026, 12, 1)))          // tháng đã qua
        assertEquals(0L, BudgetCalculator.dailyAllowance(BudgetProgress(100, 150), m, LocalDate.of(2026, 11, 10)))
    }
}

/** Auth repo giả: ghi lại lệnh gọi; [hang] = true mô phỏng mất mạng (không bao giờ trả lời). */
private class FakeAuthRepository(private val hang: Boolean = false) : AuthRepository {
    var registered: String? = null
    override val currentUserId: String? = null
    override val currentDisplayName: String? = null
    override val currentEmail: String? = null
    override suspend fun signIn(email: String, password: String) { if (hang) awaitCancellation() }
    override suspend fun register(displayName: String, email: String, password: String) {
        if (hang) awaitCancellation()
        registered = email
    }
    override suspend fun sendPasswordReset(email: String) { if (hang) awaitCancellation() }
    override suspend fun currentProfile(): UserProfile? = null
    override fun signOut() {}
}

class AuthBoundaryTest {
    @get:Rule val mainRule = MainDispatcherRule()

    @Test fun tc_auth_emailAndPasswordValidation() {
        assertNull(AuthViewModel.validateEmail("  sv@dut.udn.vn "))
        assertTrue(AuthViewModel.validateEmail("sv@dut") != null)
        assertTrue(AuthViewModel.validateEmail("") != null)
        assertTrue(AuthViewModel.validatePassword("12345") != null) // 5 ký tự
        assertNull(AuthViewModel.validatePassword("123456"))         // 6 ký tự – biên
    }

    @Test fun tc_auth_registerAcceptsLongPassword() {
        val repo = FakeAuthRepository()
        val vm = AuthViewModel(repo)
        vm.register("An", "an@gmail.com", "matkhau123", "matkhau123") // 10 ký tự
        assertNull(vm.state.value.error)
        assertEquals("an@gmail.com", repo.registered)
    }

    @Test fun tc_auth_registerEmptyConfirm() {
        val vm = AuthViewModel(FakeAuthRepository())
        vm.register("An", "an@gmail.com", "123456", "")
        assertEquals("Vui lòng nhập lại mật khẩu", vm.state.value.error)
        vm.register("An", "an@gmail.com", "123456", "123457")
        assertEquals("Mật khẩu nhập lại không khớp", vm.state.value.error)
    }

    @Test fun tc_auth_offlineStopsLoading() = runTest {
        val vm = AuthViewModel(FakeAuthRepository(hang = true))
        vm.signIn("an@gmail.com", "123456")
        assertTrue(vm.state.value.loading)
        advanceTimeBy(21_000)
        assertFalse("loading phải dừng khi mất mạng", vm.state.value.loading)
        assertTrue(vm.state.value.error != null)
    }
}

class ReceiptParserBoundaryTest {
    private fun line(text: String, top: Int) = ReceiptLine(text, 0, top, 300, top + 20)

    @Test fun tc_ocr_picksTotalNotBarcode() {
        val lines = listOf(
            line("CỬA HÀNG TIỆN LỢI", 0),
            line("Bánh mì 25.000", 40),
            line("Sữa tươi 100.000", 70),
            line("TỔNG CỘNG 125.000", 100),
            line("89350001", 140), // mã vạch / mã đơn in cuối hóa đơn
        )
        assertEquals(125_000L, ReceiptAmountParser.extract(lines))
    }

    @Test fun tc_ocr_noAmountReturnsNull() {
        assertNull(ReceiptAmountParser.extract(listOf(line("Cảm ơn quý khách", 0))))
        assertNull(ReceiptAmountParser.extract(emptyList()))
    }
}

class SearchBoundaryTest {
    @get:Rule val mainRule = MainDispatcherRule()

    @Test fun tc_search_ignoresVietnameseAccents() = runTest {
        val today = LocalDate.now(DateUtils.zone)
        val tx = Transaction(
            id = "t1", type = TransactionType.EXPENSE, amount = 30_000, categoryId = "an_uong",
            categoryName = "Ăn uống", note = "Cà phê sáng", date = System.currentTimeMillis(),
        )
        val vm = SearchViewModel(
            FakeTransactionRepository(listOf(tx)), FakeCategoryRepository(DefaultCategories.all),
        ) { today }
        backgroundScope.launch { vm.searchResults.collect {} }
        vm.search("ca phe")
        advanceUntilIdle()
        assertEquals("ca phe da", SearchViewModel.fold("Cà Phê Đá"))
        val found = vm.searchResults.value.filterIsInstance<HomeItem.Row>()
        assertEquals(1, found.size)
    }
}
