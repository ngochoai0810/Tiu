package vn.edu.dut.tiu.ui.budget

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import vn.edu.dut.tiu.R
import vn.edu.dut.tiu.data.model.Budget
import vn.edu.dut.tiu.data.model.Category
import vn.edu.dut.tiu.databinding.DialogBudgetBinding
import vn.edu.dut.tiu.databinding.FragmentBudgetBinding
import vn.edu.dut.tiu.databinding.ItemBudgetBinding
import vn.edu.dut.tiu.domain.BudgetProgress
import vn.edu.dut.tiu.ui.common.MoneyTextWatcher
import vn.edu.dut.tiu.ui.common.appContainer
import vn.edu.dut.tiu.ui.common.collectOnStarted
import vn.edu.dut.tiu.ui.common.setup
import vn.edu.dut.tiu.ui.common.showError
import vn.edu.dut.tiu.util.MoneyFormat

/** Ngân sách: tạo / xem / sửa / xóa hạn mức tháng (tổng và theo danh mục). */
class BudgetFragment : Fragment(R.layout.fragment_budget) {

    private val vm: BudgetViewModel by viewModels {
        viewModelFactory {
            initializer {
                BudgetViewModel(
                    appContainer.budgetRepository,
                    appContainer.transactionRepository,
                    appContainer.categoryRepository,
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentBudgetBinding.bind(view)
        b.topBar.setup(this)

        val adapter = BudgetAdapter { item -> openDialog(item.budget.categoryId, item.name, item.budget) }
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter

        b.cardTotal.setOnClickListener { openDialog(null, null, vm.state.value.totalBudget) }
        b.btnAddCategoryBudget.setOnClickListener { openDialog(null, null, null, pickCategory = true) }

        collectOnStarted(vm.state) { s ->
            b.textMonthInfo.text = s.monthInfo
            val p = s.totalProgress
            if (s.totalBudget == null) {
                b.textPercent.text = "–"
                b.progressTotal.progress = 0
                b.textTotalAmounts.text = getString(R.string.budget_no_total)
                b.textDaily.text = "Đã chi ${MoneyFormat.format(p.spent)} trong tháng"
            } else {
                b.textPercent.text = "${p.percent}%"
                b.progressTotal.progress = p.percent.coerceAtMost(100)
                b.textTotalAmounts.text = "${MoneyFormat.format(p.spent)} / ${MoneyFormat.format(p.limit)}"
                b.textDaily.text = when (p.status) {
                    BudgetProgress.Status.OVER -> "⚠ Đã vượt ngân sách ${MoneyFormat.format(-p.remaining)}"
                    else -> "Mỗi ngày còn được tiêu ~${MoneyFormat.format(s.dailyAllowance)}"
                }
            }
            b.cardTotal.setBackgroundResource(
                if (p.status == BudgetProgress.Status.OVER) R.drawable.bg_doodle_card_warning else R.drawable.bg_doodle_card_orange
            )
            adapter.submitList(s.items)
            if (s.error != null) Snackbar.make(view, s.error, Snackbar.LENGTH_LONG).show()
        }
        collectOnStarted(vm.messages) { msg -> Snackbar.make(view, msg, Snackbar.LENGTH_SHORT).show() }
    }

    /**
     * Hộp thoại dùng chung cho Thêm/Sửa/Xóa ngân sách.
     * categoryId = null & !pickCategory -> ngân sách tổng tháng.
     */
    private fun openDialog(categoryId: String?, categoryName: String?, existing: Budget?, pickCategory: Boolean = false) {
        val d = DialogBudgetBinding.inflate(layoutInflater)
        d.inputAmount.addTextChangedListener(MoneyTextWatcher(d.inputAmount))
        existing?.let { d.inputAmount.setText(MoneyFormat.group(it.limitAmount)) }

        var chosen: Category? = null
        if (pickCategory) {
            val options = vm.state.value.categoriesWithoutBudget
            if (options.isEmpty()) {
                Toast.makeText(requireContext(), "Mọi danh mục chi tiêu đều đã có ngân sách", Toast.LENGTH_SHORT).show()
                return
            }
            d.fieldCategory.setOnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setItems(options.map { "${it.icon}  ${it.name}" }.toTypedArray()) { _, i ->
                        chosen = options[i]
                        d.fieldCategory.text = "${options[i].icon} ${options[i].name}"
                    }.show()
            }
        } else {
            d.fieldCategory.visibility = View.GONE
        }

        val title = when {
            pickCategory -> getString(R.string.budget_dialog_category)
            categoryId == null -> getString(R.string.budget_dialog_total)
            else -> "Ngân sách: $categoryName"
        }
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(d.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_ok, null)
        if (existing != null) builder.setNeutralButton(R.string.action_remove) { _, _ -> vm.delete(existing) }

        val dialog = builder.create()
        dialog.setOnShowListener {
            // Ghi đè nút Lưu để không tự đóng khi dữ liệu sai.
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val targetCategory = if (pickCategory) chosen?.id else categoryId
                val error = if (pickCategory && chosen == null) "Chọn danh mục" else vm.saveBudget(targetCategory, d.inputAmount.text.toString())
                if (error == null) dialog.dismiss() else d.textError.showError(error)
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                ?.setTextColor(ContextCompat.getColor(requireContext(), R.color.tiu_expense))
        }
        dialog.show()
    }
}

private class BudgetAdapter(
    private val onClick: (CategoryBudgetItem) -> Unit,
) : ListAdapter<CategoryBudgetItem, BudgetAdapter.VH>(Diff) {

    inner class VH(val b: ItemBudgetBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemBudgetBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val p = item.progress
        val ctx = holder.b.root.context
        holder.b.textName.text = "${item.icon} ${item.name}".trim()
        holder.b.textSpent.text = MoneyFormat.format(p.spent)
        holder.b.progress.progress = p.percent.coerceAtMost(100)
        holder.b.progress.setIndicatorColor(
            ContextCompat.getColor(ctx, if (p.status == BudgetProgress.Status.OK) R.color.tiu_orange else R.color.tiu_expense)
        )
        holder.b.textDetail.text = buildString {
            append("${p.percent}% · hạn mức ${MoneyFormat.format(p.limit)}")
            if (p.status == BudgetProgress.Status.OVER) append(" · vượt ${MoneyFormat.format(-p.remaining)}")
        }
        holder.b.root.setOnClickListener { onClick(item) }
    }

    private object Diff : DiffUtil.ItemCallback<CategoryBudgetItem>() {
        override fun areItemsTheSame(a: CategoryBudgetItem, b: CategoryBudgetItem) = a.budget.id == b.budget.id
        override fun areContentsTheSame(a: CategoryBudgetItem, b: CategoryBudgetItem) = a == b
    }
}
