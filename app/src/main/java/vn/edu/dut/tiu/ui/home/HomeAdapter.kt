package vn.edu.dut.tiu.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import vn.edu.dut.tiu.R
import vn.edu.dut.tiu.data.model.Transaction
import vn.edu.dut.tiu.data.model.TransactionType
import vn.edu.dut.tiu.databinding.ItemDayHeaderBinding
import vn.edu.dut.tiu.databinding.ItemTransactionBinding
import vn.edu.dut.tiu.util.MoneyFormat

class HomeAdapter(
    private val onClick: (Transaction) -> Unit,
    private val onLongClick: (Transaction) -> Unit,
) : ListAdapter<HomeItem, RecyclerView.ViewHolder>(Diff) {

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is HomeItem.Header -> TYPE_HEADER
        is HomeItem.Row -> TYPE_ROW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderVH(ItemDayHeaderBinding.inflate(inflater, parent, false))
        } else {
            RowVH(ItemTransactionBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is HomeItem.Header -> (holder as HeaderVH).bind(item)
            is HomeItem.Row -> (holder as RowVH).bind(item)
        }
    }

    class HeaderVH(private val b: ItemDayHeaderBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: HomeItem.Header) {
            b.textDay.text = item.label
            b.textDayTotal.text = MoneyFormat.signed(item.net, item.net >= 0)
        }
    }

    inner class RowVH(private val b: ItemTransactionBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: HomeItem.Row) {
            val tx = item.transaction
            val isIncome = tx.type == TransactionType.INCOME
            b.textIcon.text = item.icon
            b.textCategory.text = tx.categoryName
            b.textNote.text = tx.note
            b.textNote.visibility = if (tx.note.isBlank()) View.GONE else View.VISIBLE
            b.textAmount.text = MoneyFormat.signed(tx.amount, isIncome)
            b.textAmount.setTextColor(
                ContextCompat.getColor(b.root.context, if (isIncome) R.color.tiu_income else R.color.tiu_ink)
            )
            b.root.setOnClickListener { onClick(tx) }
            b.root.setOnLongClickListener { onLongClick(tx); true }
        }
    }

    private object Diff : DiffUtil.ItemCallback<HomeItem>() {
        override fun areItemsTheSame(old: HomeItem, new: HomeItem) = when {
            old is HomeItem.Header && new is HomeItem.Header -> old.date == new.date
            old is HomeItem.Row && new is HomeItem.Row -> old.transaction.id == new.transaction.id
            else -> false
        }

        override fun areContentsTheSame(old: HomeItem, new: HomeItem) = old == new
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ROW = 1
    }
}
