package vn.edu.dut.tiu.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.edu.dut.tiu.data.repository.AuthRepository
import vn.edu.dut.tiu.ui.common.toUserMessage

class AuthViewModel(private val repo: AuthRepository) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val error: String? = null,
        val success: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun signIn(email: String, password: String) {
        val error = validateEmail(email) ?: validatePassword(password)
        if (error != null) return _state.update { it.copy(error = error) }
        execute { repo.signIn(email.trim(), password) }
    }

    fun register(name: String, email: String, password: String, confirm: String) {
        val error = (if (name.isBlank()) "Nhập tên hiển thị" else null)
            ?: validateEmail(email)
            ?: validatePassword(password)
            ?: (if (password != confirm) "Mật khẩu nhập lại không khớp" else null)
        if (error != null) return _state.update { it.copy(error = error) }
        execute { repo.register(name.trim(), email.trim(), password) }
    }

    fun consumeSuccess() = _state.update { it.copy(success = false) }

    private fun execute(block: suspend () -> Unit) {
        _state.value = UiState(loading = true)
        viewModelScope.launch {
            _state.value = try {
                block()
                UiState(success = true)
            } catch (e: Exception) {
                UiState(error = e.toUserMessage())
            }
        }
    }

    companion object {
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        fun validateEmail(email: String): String? =
            if (EMAIL.matches(email.trim())) null else "Email không hợp lệ"

        fun validatePassword(password: String): String? =
            if (password.length >= 6) null else "Mật khẩu tối thiểu 6 ký tự"
    }
}
