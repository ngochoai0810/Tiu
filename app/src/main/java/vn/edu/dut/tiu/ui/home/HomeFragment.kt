package vn.edu.dut.tiu.ui.home

import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import vn.edu.dut.tiu.R
import vn.edu.dut.tiu.data.model.Transaction
import vn.edu.dut.tiu.databinding.FragmentHomeBinding
import vn.edu.dut.tiu.databinding.ItemDayCalendarBinding
import vn.edu.dut.tiu.ui.common.appContainer
import vn.edu.dut.tiu.ui.common.collectOnStarted
import vn.edu.dut.tiu.ui.common.setup
import vn.edu.dut.tiu.util.DateUtils
import vn.edu.dut.tiu.util.MoneyFormat
import java.time.LocalDate

/** Nhật ký: xem (Read) giao dịch trong tháng; chế độ Lịch chi tiêu & Nhật ký ảnh. */
class HomeFragment : Fragment(R.layout.fragment_home) {

    private val vm: HomeViewModel by viewModels {
        viewModelFactory {
            initializer { HomeViewModel(appContainer.transactionRepository, appContainer.categoryRepository) }
        }
    }

    private var photoDialog: AlertDialog? = null
    private var viewingTransactionId: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentHomeBinding.bind(view)
        // Set setup default
        b.topBar.setup(this)
        
        b.btnSearchHome.setOnClickListener {
            findNavController().navigate(R.id.searchFragment)
        }

        val calendarAdapter = CalendarAdapter(::openDay)
        b.recyclerCalendar.adapter = calendarAdapter

        b.btnPrevMonth.setOnClickListener { vm.previousMonth() }
        b.btnNextMonth.setOnClickListener { vm.nextMonth() }

        b.btnTabCalendar.setOnClickListener {
            b.btnTabCalendar.setBackgroundResource(R.drawable.bg_toggle_selected)
            b.btnTabCalendar.setTextColor(resources.getColor(R.color.tiu_ink, null))
            b.btnTabPhoto.setBackgroundResource(0)
            b.btnTabPhoto.setTextColor(resources.getColor(R.color.tiu_text_secondary, null))
            calendarAdapter.setPhotoMode(false)
            b.tvEyebrow.text = "LỊCH CHI TIÊU"
            b.calendarSummary.visibility = View.VISIBLE
            b.expenseLegend.visibility = View.VISIBLE
            b.summaryDivider.visibility = View.VISIBLE
        }

        b.btnTabPhoto.setOnClickListener {
            b.btnTabPhoto.setBackgroundResource(R.drawable.bg_toggle_selected)
            b.btnTabPhoto.setTextColor(resources.getColor(R.color.tiu_ink, null))
            b.btnTabCalendar.setBackgroundResource(0)
            b.btnTabCalendar.setTextColor(resources.getColor(R.color.tiu_text_secondary, null))
            calendarAdapter.setPhotoMode(true)
            b.tvEyebrow.text = "NHẬT KÝ ẢNH"
            b.calendarSummary.visibility = View.GONE
            b.expenseLegend.visibility = View.GONE
            b.summaryDivider.visibility = View.GONE
        }

        collectOnStarted(vm.state) { s ->
            val selectedId = viewingTransactionId
            if (selectedId != null && !s.loading && s.items.filterIsInstance<HomeItem.Row>().none { it.transaction.id == selectedId }) {
                photoDialog?.dismiss()
            }
            b.textMonth.text = s.monthLabel
            b.btnNextMonth.alpha = if (s.canGoNext) 1f else 0.3f

            b.tvTitle.text = s.monthLabel.replace(", ${LocalDate.now(DateUtils.zone).year}", "")
            val count = s.items.count { it is HomeItem.Row }
            b.tvSubtitle.text = "$count khoản chi · ${MoneyFormat.format(s.expense)}"

            if (s.maxExpenseDate != null) {
                b.tvMaxExpense.text = "${s.maxExpenseDate.dayOfMonth}/${s.maxExpenseDate.monthValue} · ${MoneyFormat.format(s.maxExpenseAmount)}"
            } else {
                b.tvMaxExpense.text = "–"
            }
            b.tvAvgExpense.text = MoneyFormat.format(s.averageExpensePerDay)

            // Tính số ô trống trước ngày mùng 1
            val firstDayOfWeek = if (s.daysInMonth.isNotEmpty()) s.daysInMonth.first().dayOfWeek.value else 1
            val blanks = firstDayOfWeek - 1
            val calendarItems = List(blanks) { null } + s.daysInMonth
            calendarAdapter.submitData(calendarItems, s.expenseByDate, s.photoByDate, s.maxExpenseAmount)
        }
        collectOnStarted(vm.messages) { msg -> Snackbar.make(view, msg, Snackbar.LENGTH_SHORT).show() }
    }
    private fun openDay(date: LocalDate, photoMode: Boolean) {
        val transactions = vm.state.value.items.filterIsInstance<HomeItem.Row>()
            .map { it.transaction }
            .filter { DateUtils.toLocalDate(it.date) == date && (!photoMode || !it.imageUrl.isNullOrBlank()) }
        if (transactions.isEmpty()) {
            view?.let { Snackbar.make(it, if (photoMode) "Ngày này chưa có ảnh" else "Ngày này chưa có giao dịch", Snackbar.LENGTH_SHORT).show() }
            return
        }
        fun open(transaction: Transaction) {
            if (photoMode) {
                showPhoto(transaction)
            } else {
                findNavController().navigate(R.id.transactionEditorFragment, Bundle().apply {
                    putString("transactionId", transaction.id)
                })
            }
        }
        if (transactions.size == 1) {
            open(transactions.single())
            return
        }
        val labels = transactions.map {
            "${it.categoryName} · ${MoneyFormat.format(it.amount)}${it.note.takeIf(String::isNotBlank)?.let { note -> " · $note" }.orEmpty()}"
        }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("${date.dayOfMonth}/${date.monthValue}/${date.year}")
            .setItems(labels) { _, index -> open(transactions[index]) }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private fun showPhoto(transaction: Transaction) {
        val imageUrl = transaction.imageUrl ?: return
        photoDialog?.dismiss()
        val image = ImageView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.55f).toInt())
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Ảnh ${transaction.categoryName} · ${MoneyFormat.format(transaction.amount)}"
        }
        val source: Any = if (imageUrl.startsWith("data:image")) {
            try {
                Base64.decode(imageUrl.substringAfter(","), Base64.DEFAULT)
            } catch (_: IllegalArgumentException) {
                view?.let { Snackbar.make(it, "Không đọc được ảnh giao dịch", Snackbar.LENGTH_SHORT).show() }
                return
            }
        } else Uri.parse(imageUrl)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("${transaction.categoryName} · ${MoneyFormat.format(transaction.amount)}")
            .setView(image)
            .setNegativeButton("Đóng", null)
            .create()
        dialog.setOnDismissListener {
            Glide.with(image).clear(image)
            if (photoDialog === dialog) {
                photoDialog = null
                viewingTransactionId = null
            }
        }
        viewingTransactionId = transaction.id
        photoDialog = dialog
        dialog.show()
        Glide.with(image).load(source).error(R.drawable.bg_photo_empty).into(image)
    }

    override fun onDestroyView() {
        photoDialog?.dismiss()
        photoDialog = null
        viewingTransactionId = null
        super.onDestroyView()
    }

}

class CalendarAdapter(private val onDayClick: (LocalDate, Boolean) -> Unit) : RecyclerView.Adapter<CalendarAdapter.VH>() {
    private var items: List<LocalDate?> = emptyList()
    private var expenseByDate: Map<LocalDate, Long> = emptyMap()
    private var photoByDate: Map<LocalDate, String> = emptyMap()
    private var maxAmount: Long = 0L
    private var isPhotoMode: Boolean = false

    fun submitData(days: List<LocalDate?>, expenses: Map<LocalDate, Long>, photos: Map<LocalDate, String>, max: Long) {
        items = days
        expenseByDate = expenses
        photoByDate = photos
        maxAmount = max
        notifyDataSetChanged()
    }

    fun setPhotoMode(photoMode: Boolean) {
        if (isPhotoMode != photoMode) {
            isPhotoMode = photoMode
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemDayCalendarBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val b = holder.b
        val context = holder.itemView.context
        Glide.with(context).clear(b.imgPhoto)
        b.imgPhoto.setImageDrawable(null)
        b.imgPhoto.visibility = View.GONE
        b.tvDay.visibility = View.GONE
        b.dot.visibility = View.GONE
        b.dayContainer.backgroundTintList = null
        b.dayContainer.setBackgroundColor(Color.TRANSPARENT)

        holder.itemView.setOnClickListener(null)
        val date = items[position] ?: return
        holder.itemView.setOnClickListener { onDayClick(date, isPhotoMode) }
        val amount = expenseByDate[date] ?: 0L
        if (isPhotoMode) {
            b.dayContainer.setBackgroundResource(R.drawable.bg_photo_empty)
            b.dayContainer.contentDescription = "Ngày ${date.dayOfMonth}, chưa có ảnh"
            val photoUrl = photoByDate[date]?.takeIf { it.isNotBlank() } ?: return
            b.imgPhoto.visibility = View.VISIBLE
            b.imgPhoto.imageTintList = null
            b.imgPhoto.setPadding(0, 0, 0, 0)
            b.imgPhoto.scaleType = ImageView.ScaleType.CENTER_CROP
            val imageSource: Any = if (photoUrl.startsWith("data:image")) {
                try {
                    Base64.decode(photoUrl.substringAfter(","), Base64.DEFAULT)
                } catch (_: IllegalArgumentException) {
                    b.imgPhoto.visibility = View.GONE
                    return
                }
            } else {
                Uri.parse(photoUrl)
            }
            b.dayContainer.contentDescription = "Ảnh chi tiêu ngày ${date.dayOfMonth}"
            val cornerPx = (10 * context.resources.displayMetrics.density).toInt()
            Glide.with(context)
                .load(imageSource)
                .transform(CenterCrop(), RoundedCorners(cornerPx))
                .placeholder(R.drawable.bg_photo_empty)
                .error(R.drawable.bg_photo_empty)
                .into(b.imgPhoto)
            return
        }

        b.dayContainer.contentDescription = "Ngày ${date.dayOfMonth}, ${MoneyFormat.format(amount)}"
        b.tvDay.visibility = View.VISIBLE
        b.tvDay.text = date.dayOfMonth.toString()
        b.dayContainer.setBackgroundResource(R.drawable.bg_calendar_day)
        val intensity = amount.toFloat() / maxAmount.coerceAtLeast(1L)
        b.tvDay.setTextColor(Color.parseColor(if (amount > 0 && intensity > 0.6f) "#FFFFFF" else "#4A3427"))
        if (amount > 0) {
            b.dot.visibility = View.VISIBLE
            val color = when {
                intensity > 0.6f -> "#E85D45"
                intensity > 0.3f -> "#FFAD66"
                else -> "#FFD8B5"
            }
            b.dayContainer.backgroundTintList = ColorStateList.valueOf(Color.parseColor(color))
        }
    }

    override fun getItemCount(): Int = items.size

    class VH(val b: ItemDayCalendarBinding) : RecyclerView.ViewHolder(b.root)
}
