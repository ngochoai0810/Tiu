package vn.edu.dut.tiu.ui.transaction

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.fragment.findNavController
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import vn.edu.dut.tiu.R
import vn.edu.dut.tiu.data.model.TransactionType
import vn.edu.dut.tiu.databinding.FragmentTransactionEditorBinding
import vn.edu.dut.tiu.domain.TransactionValidator.Field
import vn.edu.dut.tiu.ui.common.MoneyTextWatcher
import vn.edu.dut.tiu.ui.common.appContainer
import vn.edu.dut.tiu.ui.common.collectOnStarted
import vn.edu.dut.tiu.ui.common.renderTypeToggle
import vn.edu.dut.tiu.ui.common.showError
import vn.edu.dut.tiu.util.DateUtils
import vn.edu.dut.tiu.util.MoneyFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

class TransactionEditorFragment : Fragment(R.layout.fragment_transaction_editor) {

    private val vm: TransactionEditorViewModel by viewModels {
        viewModelFactory {
            initializer {
                TransactionEditorViewModel(
                    appContainer.transactionRepository,
                    appContainer.categoryRepository,
                    arguments?.getString("transactionId"),
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentTransactionEditorBinding.bind(view)
        b.inputAmount.addTextChangedListener(MoneyTextWatcher(b.inputAmount))

        b.btnClose.setOnClickListener { findNavController().popBackStack() }
        b.toggleExpense.setOnClickListener { vm.setType(TransactionType.EXPENSE) }
        b.toggleIncome.setOnClickListener { vm.setType(TransactionType.INCOME) }
        b.fieldCategory.setOnClickListener { pickCategory() }
        b.fieldDate.setOnClickListener { pickDate() }
        b.btnSave.setOnClickListener {
            vm.save(b.inputAmount.text.toString(), b.inputNote.text.toString())
        }
        b.btnDelete.setOnClickListener { confirmDelete() }

        collectOnStarted(vm.state) { s ->
            b.textTitle.setText(if (s.isEditing) R.string.editor_edit_title else R.string.editor_add_title)
            if (!s.isEditing && s.type == TransactionType.INCOME) b.textTitle.text = "Thêm khoản thu"
            b.btnDelete.visibility = if (s.isEditing) View.VISIBLE else View.GONE
            renderTypeToggle(b.toggleExpense, b.toggleIncome, s.type)
            b.fieldCategory.text = s.selectedCategory?.let { "${it.icon} ${it.name}".trim() }
            b.fieldDate.text = DateUtils.shortDate(s.dateMillis)

            b.errorAmount.showError(s.errors[Field.AMOUNT])
            b.errorCategory.showError(s.errors[Field.CATEGORY])
            b.errorDate.showError(s.errors[Field.DATE])
            b.errorNote.showError(s.errors[Field.NOTE])

            b.btnSave.isEnabled = !s.isSaving
            b.btnDelete.isEnabled = !s.isSaving
            b.progress.visibility = if (s.isSaving) View.VISIBLE else View.GONE

            val original = s.original
            if (original != null && !vm.prefillConsumed) {
                vm.prefillConsumed = true
                b.inputAmount.setText(MoneyFormat.group(original.amount))
                b.inputNote.setText(original.note)
            }
        }

        collectOnStarted(vm.events) { event ->
            when (event) {
                TransactionEditorViewModel.Event.Saved -> {
                    Toast.makeText(requireContext(), "Đã lưu vào nhật ký", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                }
                TransactionEditorViewModel.Event.Deleted -> {
                    Toast.makeText(requireContext(), "Đã xóa giao dịch", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                }
                is TransactionEditorViewModel.Event.Error ->
                    Toast.makeText(requireContext(), event.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun pickCategory() {
        val categories = vm.state.value.categories
        if (categories.isEmpty()) {
            Toast.makeText(requireContext(), "Chưa có danh mục nào, hãy tạo trong Tài khoản > Quản lý danh mục", Toast.LENGTH_LONG).show()
            return
        }
        val labels = categories.map { "${it.icon}  ${it.name}" }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.choose_category)
            .setItems(labels) { _, which -> vm.selectCategory(categories[which]) }
            .show()
    }

    private fun pickDate() {
        val current = DateUtils.toLocalDate(vm.state.value.dateMillis)
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.label_date)
            // MaterialDatePicker làm việc với millis UTC 00:00
            .setSelection(current.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            .setCalendarConstraints(
                CalendarConstraints.Builder().setValidator(DateValidatorPointBackward.now()).build()
            )
            .build()
        picker.addOnPositiveButtonClickListener { utcMillis ->
            val date = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
            // Giữ giờ hiện tại để thứ tự trong ngày hợp lý
            val millis = LocalDateTime.of(date, LocalTime.now(DateUtils.zone))
                .atZone(DateUtils.zone).toInstant().toEpochMilli()
            vm.setDate(millis)
        }
        picker.show(childFragmentManager, "date")
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_tx_title)
            .setMessage("Thao tác này không thể hoàn tác.")
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_confirm_delete) { _, _ -> vm.delete() }
            .show()
    }
}
