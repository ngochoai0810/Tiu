package vn.edu.dut.tiu.ui.account

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import vn.edu.dut.tiu.R
import vn.edu.dut.tiu.data.model.TransactionType
import vn.edu.dut.tiu.databinding.FragmentAccountBinding
import vn.edu.dut.tiu.ui.common.appContainer
import vn.edu.dut.tiu.ui.common.collectOnStarted
import vn.edu.dut.tiu.ui.common.initials
import vn.edu.dut.tiu.util.DateUtils
import vn.edu.dut.tiu.util.MoneyFormat
import java.time.YearMonth

/** Tài khoản: thông tin hồ sơ, thống kê tổng quan, quản lý danh mục, cài đặt thông báo, đăng xuất. */
class AccountFragment : Fragment(R.layout.fragment_account) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentAccountBinding.bind(view)
        val auth = appContainer.authRepository
        b.textAvatar.text = initials(auth.currentDisplayName)
        b.textName.text = auth.currentDisplayName?.takeIf { it.isNotBlank() } ?: "Bạn"
        b.textEmail.text = auth.currentEmail.orEmpty()

        // Tính toán thống kê tháng hiện tại
        val currentMonth = YearMonth.now(DateUtils.zone)
        collectOnStarted(appContainer.transactionRepository.observeMonth(currentMonth)) { transactions ->
            b.tvTxCount.text = transactions.size.toString()
            val totalExpense = transactions
                .filter { it.type == TransactionType.EXPENSE }
                .sumOf { it.amount }
            b.tvTotalExpense.text = MoneyFormat.format(totalExpense)
        }

        b.btnCategories.setOnClickListener { findNavController().navigate(R.id.categoryFragment) }
        b.btnNotificationSettings.setOnClickListener { findNavController().navigate(R.id.notificationSettingsFragment) }

        b.btnLogout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Đăng xuất?")
                .setMessage("Bạn có chắc chắn muốn đăng xuất khỏi tài khoản?")
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
