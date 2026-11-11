package vn.edu.dut.tiu.ui.common

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import vn.edu.dut.tiu.R
import vn.edu.dut.tiu.TiuApp
import vn.edu.dut.tiu.data.model.TransactionType
import vn.edu.dut.tiu.data.repository.NotSignedInException
import vn.edu.dut.tiu.databinding.LayoutTopBarBinding
import vn.edu.dut.tiu.di.AppContainer
import vn.edu.dut.tiu.util.MoneyFormat

val Fragment.appContainer: AppContainer
    get() = (requireActivity().application as TiuApp).container

/** Thu thập Flow an toàn theo vòng đời View của Fragment (chỉ khi STARTED). */
fun <T> Fragment.collectOnStarted(flow: Flow<T>, block: (T) -> Unit) {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collect { block(it) }
        }
    }
}

/** Đổi lỗi kỹ thuật thành câu tiếng Việt dễ hiểu. */
fun Throwable.toUserMessage(): String = when (this) {
    is NotSignedInException -> "Phiên đăng nhập đã hết, vui lòng đăng nhập lại"
    is FirebaseAuthInvalidUserException -> "Tài khoản không tồn tại hoặc đã bị khóa"
    is FirebaseAuthInvalidCredentialsException -> "Email hoặc mật khẩu không đúng"
    is FirebaseAuthUserCollisionException -> "Email này đã được đăng ký"
    is FirebaseAuthWeakPasswordException -> "Mật khẩu quá yếu (tối thiểu 6 ký tự)"
    is FirebaseNetworkException -> "Không có kết nối mạng"
    is FirebaseFirestoreException -> when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Không có quyền truy cập dữ liệu (kiểm tra Security Rules)"
        FirebaseFirestoreException.Code.UNAVAILABLE -> "Máy chủ tạm thời không phản hồi, thử lại sau"
        FirebaseFirestoreException.Code.FAILED_PRECONDITION -> "Thiếu index Firestore (xem firestore.indexes.json)"
        else -> "Lỗi dữ liệu: ${code.name}"
    }
    else -> message ?: "Đã có lỗi xảy ra"
}

fun initials(name: String?): String {
    val parts = name.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}

fun TextView.showError(message: String?) {
    text = message
    visibility = if (message.isNullOrEmpty()) View.GONE else View.VISIBLE
}

/** Toggle Chi tiêu / Thu nhập dạng "segmented" theo Figma. */
fun renderTypeToggle(expense: TextView, income: TextView, selected: TransactionType) {
    expense.setBackgroundResource(if (selected == TransactionType.EXPENSE) R.drawable.bg_toggle_selected else 0)
    income.setBackgroundResource(if (selected == TransactionType.INCOME) R.drawable.bg_toggle_selected else 0)
}

/** Thanh trên cùng dùng chung: nút + mở form thêm, avatar mở trang tài khoản. */
fun LayoutTopBarBinding.setup(fragment: Fragment) {
    avatar.text = initials(fragment.appContainer.authRepository.currentDisplayName)
    btnAdd.setOnClickListener { fragment.findNavController().navigate(R.id.transactionEditorFragment) }
    avatar.setOnClickListener { fragment.findNavController().navigate(R.id.accountFragment) }
}

/** Tự chèn dấu chấm phân cách hàng nghìn khi gõ số tiền: 85000 -> 85.000 */
class MoneyTextWatcher(private val editText: EditText) : TextWatcher {
    private var selfChange = false

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

    override fun afterTextChanged(s: Editable?) {
        if (selfChange) return
        val raw = s?.toString().orEmpty()
        val formatted = MoneyFormat.parse(raw)?.let(MoneyFormat::group).orEmpty()
        if (formatted != raw) {
            selfChange = true
            editText.setText(formatted)
            editText.setSelection(formatted.length)
            selfChange = false
        }
    }
}
