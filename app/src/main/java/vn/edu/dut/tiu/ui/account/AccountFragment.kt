package vn.edu.dut.tiu.ui.account

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import vn.edu.dut.tiu.R
import vn.edu.dut.tiu.databinding.FragmentAccountBinding
import vn.edu.dut.tiu.ui.common.appContainer
import vn.edu.dut.tiu.ui.common.initials

/** Tài khoản (bản tối giản cho tuần 3): thông tin, quản lý danh mục, đăng xuất. */
class AccountFragment : Fragment(R.layout.fragment_account) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentAccountBinding.bind(view)
        val auth = appContainer.authRepository
        b.textAvatar.text = initials(auth.currentDisplayName)
        b.textName.text = auth.currentDisplayName?.takeIf { it.isNotBlank() } ?: "Bạn"
        b.textEmail.text = auth.currentEmail.orEmpty()

        b.btnCategories.setOnClickListener { findNavController().navigate(R.id.categoryFragment) }
        b.btnLogout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Đăng xuất?")
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.account_logout) { _, _ ->
                    auth.signOut()
                    findNavController().navigate(
                        R.id.loginFragment, null,
                        navOptions { popUpTo(R.id.nav_graph) { inclusive = true } }
                    )
                }
                .show()
        }
    }
}
