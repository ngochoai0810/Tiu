package vn.edu.dut.tiu.ui.transaction

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior
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

class TransactionEditorFragment : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_transaction_editor, container, false)

    override fun onStart() {
        super.onStart()
        val sheetDialog = dialog as? BottomSheetDialog ?: return
        val sheet = sheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        val height = (resources.displayMetrics.heightPixels * 0.5f).toInt()
        sheet.layoutParams = sheet.layoutParams.apply { this.height = height }
        sheet.setBackgroundColor(Color.TRANSPARENT)
        sheetDialog.behavior.apply {
            peekHeight = height
            maxHeight = height
            isFitToContents = true
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

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
        b.btnRemoveImage.setOnClickListener { vm.setImage(null) }

        // Read the navigation image once, including after process recreation.
        if (!vm.imageArgumentConsumed) {
            vm.imageArgumentConsumed = true
            arguments?.getString("imageUri")?.let { uriString ->
                val isOcrMode = arguments?.getBoolean("isOcrMode", false) ?: false
                vm.setImage(uriString, isOcrMode)
                if (isOcrMode) {
                    vm.performOcr(requireContext(), uriString)
                }
            }
        }

        b.btnSelectImage.setOnClickListener {
            findNavController().navigate(R.id.cameraFragment)
        }

        collectOnStarted(vm.state) { s ->
            b.textTitle.setText(if (s.isEditing) R.string.editor_edit_title else R.string.editor_add_title)
            if (!s.isEditing && s.type == TransactionType.INCOME) b.textTitle.text = "Thêm khoản thu"
            b.btnSave.text = if (s.isEditing) "Lưu thay đổi" else "Lưu vào nhật ký"
            b.btnDelete.visibility = if (s.isEditing) View.VISIBLE else View.GONE
            renderTypeToggle(b.toggleExpense, b.toggleIncome, s.type)
            b.fieldCategory.text = s.selectedCategory?.let { "${it.icon} ${it.name}".trim() }
            b.fieldDate.text = DateUtils.shortDate(s.dateMillis)

            b.errorAmount.showError(s.errors[Field.AMOUNT])
            b.errorCategory.showError(s.errors[Field.CATEGORY])
            b.errorDate.showError(s.errors[Field.DATE])
            b.errorNote.showError(s.errors[Field.NOTE])

            b.btnSave.isEnabled = !s.isSaving && !s.isUploadingImage
            b.btnDelete.isEnabled = !s.isSaving && !s.isUploadingImage
            b.progress.visibility = if (s.isSaving || s.isUploadingImage) View.VISIBLE else View.GONE

            if (s.imageUri != null) {
                b.layoutImagePreview.visibility = View.VISIBLE
                b.btnSelectImage.visibility = View.GONE
                Glide.with(this).load(Uri.parse(s.imageUri)).into(b.imagePreview)
            } else {
                b.layoutImagePreview.visibility = View.GONE
                b.btnSelectImage.visibility = View.VISIBLE
            }

            val original = s.original
            if (original != null && !vm.prefillConsumed) {
                vm.prefillConsumed = true
                b.inputAmount.setText(MoneyFormat.group(original.amount))
                b.inputNote.setText(original.note)
                if (original.imageUrl != null && s.imageUri == null) {
                    vm.setImage(original.imageUrl, false)
                }
            }
            
            val ocrAmount = s.ocrAmount?.toLongOrNull()
            if (ocrAmount != null && !vm.ocrPrefillConsumed && b.inputAmount.text.isNullOrEmpty()) {
                vm.consumeOcrPrefill()
                b.inputAmount.setText(MoneyFormat.group(ocrAmount))
            }
            b.textEyebrow.visibility = if (s.isOcrMode) View.VISIBLE else View.GONE
            b.textEyebrow.text = when {
                s.isRecognizing -> "OCR · ĐANG NHẬN DIỆN"
                s.ocrError != null -> "OCR · KHÔNG ĐỌC ĐƯỢC ẢNH"
                ocrAmount != null -> "OCR · ĐÃ NHẬN DIỆN ${MoneyFormat.format(ocrAmount)}"
                else -> "OCR · CHƯA TÌM THẤY SỐ TIỀN"
            }
            b.chipOcrAuto.visibility = if (s.isOcrMode && ocrAmount != null) View.VISIBLE else View.GONE
            b.chipOcrAuto.text = "Đọc từ ảnh: ${ocrAmount?.let(MoneyFormat::format).orEmpty()}"
            b.tooltipOcr.visibility = if (s.isOcrMode) View.VISIBLE else View.GONE
            b.tooltipOcr.text = when {
                s.isRecognizing -> "Đang đọc chữ trong ảnh hóa đơn…"
                s.ocrError != null -> s.ocrError
                ocrAmount != null -> "Kiểm tra lại số tiền ${MoneyFormat.format(ocrAmount)} trước khi lưu."
                else -> "Chưa nhận diện được số tiền. Bạn có thể nhập thủ công."
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
        val labels = categories.map { "${it.icon} ${it.name}".trim() }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.choose_category)
            .setItems(labels) { _, which -> vm.selectCategory(categories[which]) }
            .show()
    }

    private fun pickDate() {
        val current = DateUtils.toLocalDate(vm.state.value.dateMillis)
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.label_date)
            .setSelection(current.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            .setCalendarConstraints(
                CalendarConstraints.Builder().setValidator(DateValidatorPointBackward.now()).build()
            )
            .build()
        picker.addOnPositiveButtonClickListener { utcMillis ->
            val date = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
            val millis = LocalDateTime.of(date, LocalTime.now(DateUtils.zone))
                .atZone(DateUtils.zone).toInstant().toEpochMilli()
            vm.setDate(millis)
        }
        picker.show(childFragmentManager, "date")
    }

    private fun confirmDelete() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_transaction, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setBackground(ColorDrawable(Color.TRANSPARENT))
            .create()

        val current = vm.state.value.original
        if (current != null) {
            val desc = "“${current.note.ifBlank { current.categoryName }}” · ${MoneyFormat.format(current.amount)} · ${DateUtils.shortDate(current.              date)}"
            dialogView.findViewById<TextView>(R.id.dialogMessage).text = "$desc\nẢnh và ghi chú cũng sẽ bị xóa."
        }

        dialogView.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btnConfirmDelete).setOnClickListener {
            dialog.dismiss()
            vm.delete()
        }
        dialog.show()
    }
}
