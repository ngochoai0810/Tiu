package vn.edu.dut.tiu.ui.home

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import vn.edu.dut.tiu.R
import vn.edu.dut.tiu.data.model.Transaction
import vn.edu.dut.tiu.databinding.FragmentHomeBinding
import vn.edu.dut.tiu.ui.common.appContainer
import vn.edu.dut.tiu.ui.common.collectOnStarted
import vn.edu.dut.tiu.ui.common.setup
import vn.edu.dut.tiu.util.MoneyFormat

/** Nhật ký: xem (Read) giao dịch trong tháng; chạm để sửa, nhấn giữ để xóa. */
class HomeFragment : Fragment(R.layout.fragment_home) {

    private val vm: HomeViewModel by viewModels {
        viewModelFactory {
            initializer { HomeViewModel(appContainer.transactionRepository, appContainer.categoryRepository) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentHomeBinding.bind(view)
        b.topBar.setup(this)

        val adapter = HomeAdapter(
            onClick = { tx ->
                findNavController().navigate(R.id.transactionEditorFragment, bundleOf("transactionId" to tx.id))
            },
            onLongClick = ::confirmDelete,
        )
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter

        b.btnPrevMonth.setOnClickListener { vm.previousMonth() }
        b.btnNextMonth.setOnClickListener { vm.nextMonth() }

        collectOnStarted(vm.state) { s ->
            b.textMonth.text = s.monthLabel
            b.btnNextMonth.alpha = if (s.canGoNext) 1f else 0.3f
            b.textExpense.text = MoneyFormat.format(s.expense)
            b.textIncome.text = MoneyFormat.format(s.income)
            b.textBalance.text = MoneyFormat.format(s.balance)
            b.progress.visibility = if (s.loading) View.VISIBLE else View.GONE
            b.textEmpty.visibility = if (!s.loading && s.items.isEmpty()) View.VISIBLE else View.GONE
            if (s.error != null) b.textEmpty.apply { text = s.error; visibility = View.VISIBLE }
            adapter.submitList(s.items)
        }
        collectOnStarted(vm.messages) { msg -> Snackbar.make(view, msg, Snackbar.LENGTH_SHORT).show() }
    }

    private fun confirmDelete(tx: Transaction) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_tx_title)
            .setMessage(getString(R.string.delete_tx_message, MoneyFormat.format(tx.amount), tx.categoryName))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_confirm_delete) { _, _ -> vm.delete(tx) }
            .show()
    }
}
