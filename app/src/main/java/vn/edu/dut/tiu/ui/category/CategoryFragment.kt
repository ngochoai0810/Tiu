package vn.edu.dut.tiu.ui.category

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import vn.edu.dut.tiu.R
import vn.edu.dut.tiu.data.model.Category
import vn.edu.dut.tiu.data.model.TransactionType
import vn.edu.dut.tiu.databinding.DialogCategoryBinding
import vn.edu.dut.tiu.databinding.FragmentCategoryBinding
import vn.edu.dut.tiu.databinding.ItemCategoryBinding
import vn.edu.dut.tiu.ui.common.appContainer
import vn.edu.dut.tiu.ui.common.collectOnStarted
import vn.edu.dut.tiu.ui.common.renderTypeToggle
import vn.edu.dut.tiu.ui.common.showError

/** Quản lý danh mục: thêm / sửa (chạm) / xóa (nhấn giữ). */
class CategoryFragment : Fragment(R.layout.fragment_category) {

    private val vm: CategoryViewModel by viewModels {
        viewModelFactory { initializer { CategoryViewModel(appContainer.categoryRepository) } }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentCategoryBinding.bind(view)
        val adapter = CategoryAdapter(onClick = ::openDialog, onLongClick = ::confirmDelete)
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter

        b.btnBack.setOnClickListener { findNavController().popBackStack() }
        b.toggleExpense.setOnClickListener { vm.setType(TransactionType.EXPENSE) }
        b.toggleIncome.setOnClickListener { vm.setType(TransactionType.INCOME) }
        b.btnAdd.setOnClickListener { openDialog(null) }

        collectOnStarted(vm.state) { s ->
            renderTypeToggle(b.toggleExpense, b.toggleIncome, s.type)
            adapter.submitList(s.categories)
        }
        collectOnStarted(vm.messages) { msg -> Snackbar.make(view, msg, Snackbar.LENGTH_SHORT).show() }
    }

    private fun openDialog(editing: Category?) {
        val d = DialogCategoryBinding.inflate(layoutInflater)
        editing?.let {
            d.inputIcon.setText(it.icon)
            d.inputName.setText(it.name)
        }
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (editing == null) R.string.category_dialog_add else R.string.category_dialog_edit)
            .setView(d.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_ok, null)
        if (editing != null) builder.setNeutralButton(R.string.category_delete) { _, _ -> confirmDelete(editing) }

        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val error = vm.save(editing, d.inputName.text.toString(), d.inputIcon.text.toString())
                if (error == null) dialog.dismiss() else d.textError.showError(error)
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                ?.setTextColor(ContextCompat.getColor(requireContext(), R.color.tiu_expense))
        }
        dialog.show()
    }

    private fun confirmDelete(category: Category) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Xóa danh mục \"${category.name}\"?")
            .setMessage("Chỉ xóa được khi chưa có giao dịch nào dùng danh mục này.")
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_confirm_delete) { _, _ -> vm.delete(category) }
            .show()
    }
}

private class CategoryAdapter(
    private val onClick: (Category) -> Unit,
    private val onLongClick: (Category) -> Unit,
) : ListAdapter<Category, CategoryAdapter.VH>(Diff) {

    inner class VH(val b: ItemCategoryBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = getItem(position)
        holder.b.textIcon.text = c.icon
        holder.b.textName.text = c.name
        holder.b.textBadge.visibility = if (c.isDefault) View.VISIBLE else View.GONE
        holder.b.root.setOnClickListener { onClick(c) }
        holder.b.root.setOnLongClickListener { onLongClick(c); true }
    }

    private object Diff : DiffUtil.ItemCallback<Category>() {
        override fun areItemsTheSame(a: Category, b: Category) = a.id == b.id
        override fun areContentsTheSame(a: Category, b: Category) = a == b
    }
}
