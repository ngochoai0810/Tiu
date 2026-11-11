package vn.edu.dut.tiu.ui

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import vn.edu.dut.tiu.data.model.Budget
import vn.edu.dut.tiu.data.model.Category
import vn.edu.dut.tiu.data.model.DefaultCategories
import vn.edu.dut.tiu.data.model.Transaction
import vn.edu.dut.tiu.data.model.TransactionType
import vn.edu.dut.tiu.domain.TransactionValidator.Field
import vn.edu.dut.tiu.ui.budget.BudgetViewModel
import vn.edu.dut.tiu.ui.category.CategoryViewModel
import vn.edu.dut.tiu.ui.home.HomeItem
import vn.edu.dut.tiu.ui.home.HomeViewModel
import vn.edu.dut.tiu.ui.transaction.TransactionEditorViewModel
import vn.edu.dut.tiu.util.DateUtils
import java.time.LocalDate
import java.time.YearMonth

class TransactionEditorViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val now = System.currentTimeMillis()
    private val food = DefaultCategories.all.first { it.id == "an_uong" }

    private fun newVm(txRepo: FakeTransactionRepository, id: String? = null) =
        TransactionEditorViewModel(txRepo, FakeCategoryRepository(DefaultCategories.all), id) { now }

    @Test fun createTransaction() = runTest {
        val repo = FakeTransactionRepository()
        val vm = newVm(repo)
        assertTrue(vm.state.value.categories.all { it.type == TransactionType.EXPENSE })

        vm.selectCategory(food)
        vm.save("85.000", "  Cà phê  ")

        val saved = repo.items.value.single()
        assertEquals(85_000L, saved.amount)
        assertEquals("an_uong", saved.categoryId)
        assertEquals("Ăn uống", saved.categoryName) // tên được phi chuẩn hóa vào giao dịch
        assertEquals("Cà phê", saved.note)
        assertEquals(TransactionType.EXPENSE, saved.type)
    }

    @Test fun invalidInputIsNotSaved() = runTest {
        val repo = FakeTransactionRepository()
        val vm = newVm(repo)
        vm.save("", "")
        assertTrue(repo.items.value.isEmpty())
        assertEquals(setOf(Field.AMOUNT, Field.CATEGORY), vm.state.value.errors.keys)
    }

    @Test fun switchingTypeClearsCategoryAndFiltersList() = runTest {
        val vm = newVm(FakeTransactionRepository())
        vm.selectCategory(food)
        vm.setType(TransactionType.INCOME)
        assertNull(vm.state.value.selectedCategory)
        assertTrue(vm.state.value.categories.all { it.type == TransactionType.INCOME })
    }

    @Test fun updateAndDeleteExisting() = runTest {
        val existing = Transaction(id = "t1", amount = 50_000, categoryId = "an_uong", categoryName = "Ăn uống", date = now, createdAt = 42)
        val repo = FakeTransactionRepository(listOf(existing))
        val vm = newVm(repo, "t1")

        assertTrue(vm.state.value.isEditing)
        assertEquals(existing, vm.state.value.original)
        assertEquals("an_uong", vm.state.value.selectedCategory?.id)

        vm.save("60.000", "Bún bò")
        val updated = repo.items.value.single()
        assertEquals(60_000L, updated.amount)
        assertEquals("Bún bò", updated.note)
        assertEquals(42L, updated.createdAt) // giữ thời điểm tạo

        vm.delete()
        assertTrue(repo.items.value.isEmpty())
    }

}

class HomeGroupingTest {
    @Test fun groupsByDayNewestFirstWithNetTotal() {
        val today = LocalDate.of(2025, 6, 14)
        fun at(d: LocalDate, hour: Int) = DateUtils.startOfDay(d) + hour * 3_600_000L
        val txs = listOf(
            Transaction(id = "a", amount = 85_000, categoryId = "an_uong", date = at(today.minusDays(1), 9)),
            Transaction(id = "b", amount = 30_000, categoryId = "an_uong", date = at(today, 8)),
            Transaction(id = "c", amount = 200_000, categoryId = "tro_cap", type = TransactionType.INCOME, date = at(today, 20)),
        )
        val items = HomeViewModel.groupByDay(txs, mapOf("an_uong" to "🍜"), today)

        assertEquals(5, items.size)
        val h1 = items[0] as HomeItem.Header
        assertTrue(h1.label.startsWith("Hôm nay"))
        assertEquals(170_000L, h1.net)
        assertEquals("c", (items[1] as HomeItem.Row).transaction.id) // mới nhất trong ngày trước
        assertEquals("🍜", (items[2] as HomeItem.Row).icon)
        assertTrue((items[3] as HomeItem.Header).label.startsWith("Hôm qua"))
    }
}

class BudgetStateTest {
    @Test fun buildsTotalAndCategoryProgress() {
        val june = YearMonth.of(2025, 6)
        val budgets = listOf(
            Budget(id = "2025-06_ALL", month = "2025-06", categoryId = null, limitAmount = 3_000_000),
            Budget(id = "2025-06_an_uong", month = "2025-06", categoryId = "an_uong", limitAmount = 600_000),
        )
        val txs = listOf(
            Transaction(amount = 540_000, categoryId = "an_uong"),
            Transaction(amount = 744_000, categoryId = "mua_sam"),
        )
        val s = BudgetViewModel.buildState(june, budgets, txs, DefaultCategories.all, LocalDate.of(2025, 6, 14))

        assertNotNull(s.totalBudget)
        assertEquals(1_284_000L, s.totalProgress.spent)
        assertEquals(42, s.totalProgress.percent)
        assertEquals("Tháng 6 · còn 17 ngày", s.monthInfo)
        assertEquals(1, s.items.size)
        assertEquals(90, s.items[0].progress.percent)
        assertTrue(s.categoriesWithoutBudget.none { it.id == "an_uong" })
        assertTrue(s.categoriesWithoutBudget.all { it.type == TransactionType.EXPENSE })
    }
}

class CategoryValidationTest {
    private val all = DefaultCategories.all

    @Test fun rejectsBlankLongAndDuplicateNames() {
        assertEquals("Nhập tên danh mục", CategoryViewModel.validateName(" ", TransactionType.EXPENSE, null, all))
        assertNotNull(CategoryViewModel.validateName("x".repeat(31), TransactionType.EXPENSE, null, all))
        assertNotNull(CategoryViewModel.validateName("ăn uống", TransactionType.EXPENSE, null, all))
    }

    @Test fun allowsSameNameWhenEditingItselfOrOtherType() {
        assertNull(CategoryViewModel.validateName("Ăn uống", TransactionType.EXPENSE, "an_uong", all))
        assertNull(CategoryViewModel.validateName("Ăn uống", TransactionType.INCOME, null, all))
        assertNull(CategoryViewModel.validateName("Cà phê", TransactionType.EXPENSE, null, all + Category()))
    }
}
