package vn.edu.dut.tiu.ui.auth

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import vn.edu.dut.tiu.R
import vn.edu.dut.tiu.databinding.FragmentLoginBinding
import vn.edu.dut.tiu.databinding.FragmentRegisterBinding
import vn.edu.dut.tiu.ui.common.appContainer
import vn.edu.dut.tiu.ui.common.collectOnStarted
import vn.edu.dut.tiu.ui.common.showError

private fun Fragment.authViewModelFactory() = viewModelFactory {
    initializer { AuthViewModel(appContainer.authRepository) }
}

/** Sau khi đăng nhập/đăng ký thành công: vào Nhật ký và xóa màn auth khỏi back stack. */
private fun Fragment.goHome() {
    findNavController().navigate(
        R.id.homeFragment, null,
        navOptions { popUpTo(R.id.nav_graph) { inclusive = true } }
    )
}

class LoginFragment : Fragment(R.layout.fragment_login) {
    private val vm: AuthViewModel by viewModels { authViewModelFactory() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentLoginBinding.bind(view)
        b.btnLogin.setOnClickListener {
            vm.signIn(b.inputEmail.text.toString(), b.inputPassword.text.toString())
        }
        b.linkRegister.setOnClickListener { findNavController().navigate(R.id.registerFragment) }

        collectOnStarted(vm.state) { s ->
            b.progress.visibility = if (s.loading) View.VISIBLE else View.GONE
            b.btnLogin.isEnabled = !s.loading
            b.textError.showError(s.error)
            if (s.success) {
                vm.consumeSuccess()
                goHome()
            }
        }
    }
}

class RegisterFragment : Fragment(R.layout.fragment_register) {
    private val vm: AuthViewModel by viewModels { authViewModelFactory() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentRegisterBinding.bind(view)
        b.btnRegister.setOnClickListener {
            vm.register(
                b.inputName.text.toString(),
                b.inputEmail.text.toString(),
                b.inputPassword.text.toString(),
                b.inputConfirm.text.toString(),
            )
        }
        b.linkLogin.setOnClickListener { findNavController().popBackStack() }

        collectOnStarted(vm.state) { s ->
            b.progress.visibility = if (s.loading) View.VISIBLE else View.GONE
            b.btnRegister.isEnabled = !s.loading
            b.textError.showError(s.error)
            if (s.success) {
                vm.consumeSuccess()
                goHome()
            }
        }
    }
}
