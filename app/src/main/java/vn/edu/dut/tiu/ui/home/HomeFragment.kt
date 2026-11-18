package vn.edu.dut.tiu.ui.home

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.recyclerview.widget.RecyclerView
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentHomeBinding.bind(view)
        // Set setup default
        b.topBar.setup(this)
        
        b.btnSearchHome.setOnClickListener {
            findNavController().navigate(R.id.searchFragment)
        }

        val calendarAdapter = CalendarAdapter()
        b.recyclerCalendar.adapter = calendarAdapter

        b.btnPrevMonth.setOnClickListener { vm.previousMonth() }
        b.btnNextMonth.setOnClickListener { vm.nextMonth() }

        b.btnTabCalendar.setOnClickListener {
            b.btnTabCalendar.setBackgroundResource(R.drawable.bg_toggle_selected)
            b.btnTabCalendar.setTextColor(resources.getColor(R.color.tiu_ink, null))
            b.btnTabPhoto.setBackgroundResource(0)
            b.btnTabPhoto.setTextColor(resources.getColor(R.color.tiu_text_secondary, null))
            calendarAdapter.setPhotoMode(false)
        }

        b.btnTabPhoto.setOnClickListener {
            b.btnTabPhoto.setBackgroundResource(R.drawable.bg_toggle_selected)
            b.btnTabPhoto.setTextColor(resources.getColor(R.color.tiu_ink, null))
            b.btnTabCalendar.setBackgroundResource(0)
            b.btnTabCalendar.setTextColor(resources.getColor(R.color.tiu_text_secondary, null))
            calendarAdapter.setPhotoMode(true)
        }

        collectOnStarted(vm.state) { s ->
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
            calendarAdapter.submitData(calendarItems, s.expenseByDate, s.maxExpenseAmount)
        }
        collectOnStarted(vm.messages) { msg -> Snackbar.make(view, msg, Snackbar.LENGTH_SHORT).show() }
    }
}

class CalendarAdapter : RecyclerView.Adapter<CalendarAdapter.VH>() {
    private var items: List<LocalDate?> = emptyList()
    private var expenseByDate: Map<LocalDate, Long> = emptyMap()
    private var maxAmount: Long = 0L
    private var isPhotoMode: Boolean = false

    fun submitData(days: List<LocalDate?>, expenses: Map<LocalDate, Long>, max: Long) {
        items = days
        expenseByDate = expenses
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
        val date = items[position]
        if (date == null) {
            holder.b.tvDay.text = ""
            holder.b.dot.visibility = View.GONE
            holder.b.imgPhoto.visibility = View.GONE
            holder.b.dayContainer.setBackgroundColor(Color.TRANSPARENT)
        } else {
            val amount = expenseByDate[date] ?: 0L

            if (isPhotoMode) {
                if (amount > 0) {
                    holder.b.imgPhoto.visibility = View.VISIBLE
                    holder.b.tvDay.visibility = View.GONE
                    holder.b.dot.visibility = View.GONE
                    holder.b.dayContainer.setBackgroundResource(R.drawable.bg_doodle_circle)
                    holder.b.dayContainer.backgroundTintList = null
                } else {
                    holder.b.imgPhoto.visibility = View.GONE
                    holder.b.tvDay.visibility = View.VISIBLE
                    holder.b.tvDay.text = date.dayOfMonth.toString()
                    holder.b.dot.visibility = View.GONE
                    holder.b.dayContainer.setBackgroundResource(R.drawable.bg_doodle_circle)
                    holder.b.dayContainer.backgroundTintList = null
                }
            } else {
                holder.b.imgPhoto.visibility = View.GONE
                holder.b.tvDay.visibility = View.VISIBLE
                holder.b.tvDay.text = date.dayOfMonth.toString()

                if (amount > 0) {
                    holder.b.dot.visibility = View.VISIBLE
                    val intensity = amount.toFloat() / (if (maxAmount > 0) maxAmount else 1)
                    val color = when {
                        intensity > 0.6f -> Color.parseColor("#E85D45")
                        intensity > 0.3f -> Color.parseColor("#FFAD66")
                        else -> Color.parseColor("#FFD8B5")
                    }
                    holder.b.dayContainer.setBackgroundResource(R.drawable.bg_doodle_circle)
                    holder.b.dayContainer.backgroundTintList = ColorStateList.valueOf(color)
                } else {
                    holder.b.dot.visibility = View.GONE
                    holder.b.dayContainer.setBackgroundResource(R.drawable.bg_doodle_circle)
                    holder.b.dayContainer.backgroundTintList = null
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size

    class VH(val b: ItemDayCalendarBinding) : RecyclerView.ViewHolder(b.root)
}
