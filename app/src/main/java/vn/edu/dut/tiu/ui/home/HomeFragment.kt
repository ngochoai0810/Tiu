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
import androidx.core.content.ContextCompat
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
            calendarAdapter.submitData(calendarItems, s.expenseByDate, s.photoByDate, s.maxExpenseAmount)
        }
        collectOnStarted(vm.messages) { msg -> Snackbar.make(view, msg, Snackbar.LENGTH_SHORT).show() }
    }
}

class CalendarAdapter : RecyclerView.Adapter<CalendarAdapter.VH>() {
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
        val date = items[position]
        if (date == null) {
            Glide.with(holder.itemView.context).clear(holder.b.imgPhoto)
            holder.b.tvDay.text = ""
            holder.b.dot.visibility = View.GONE
            holder.b.imgPhoto.visibility = View.GONE
            holder.b.dayContainer.setBackgroundColor(Color.TRANSPARENT)
        } else {
            val amount = expenseByDate[date] ?: 0L
            val photoUrl = photoByDate[date]

            if (isPhotoMode) {
                if (photoUrl != null) {
                    holder.b.tvDay.visibility = View.GONE
                    holder.b.dot.visibility = View.GONE
                    holder.b.imgPhoto.visibility = View.VISIBLE
                    holder.b.dayContainer.setBackgroundResource(R.drawable.bg_doodle_circle)
                    holder.b.dayContainer.backgroundTintList = null

                    val context = holder.itemView.context
                    val density = context.resources.displayMetrics.density
                    val cornerPx = (12 * density).toInt()

                    holder.b.imgPhoto.imageTintList = null
                    holder.b.imgPhoto.setPadding(0, 0, 0, 0)
                    holder.b.imgPhoto.scaleType = ImageView.ScaleType.CENTER_CROP

                    if (photoUrl.startsWith("data:image")) {
                        try {
                            val base64Data = photoUrl.substringAfter(",")
                            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                            Glide.with(context)
                                .load(bytes)
                                .transform(CenterCrop(), RoundedCorners(cornerPx))
                                .into(holder.b.imgPhoto)
                        } catch (e: Exception) {
                            holder.b.imgPhoto.setImageResource(R.drawable.ic_camera)
                        }
                    } else {
                        Glide.with(context)
                            .load(Uri.parse(photoUrl))
                            .transform(CenterCrop(), RoundedCorners(cornerPx))
                            .placeholder(R.drawable.ic_camera)
                            .error(R.drawable.ic_camera)
                            .into(holder.b.imgPhoto)
                    }
                } else if (amount > 0) {
                    Glide.with(holder.itemView.context).clear(holder.b.imgPhoto)
                    val context = holder.itemView.context
                    val pad = (6 * context.resources.displayMetrics.density).toInt()
                    holder.b.imgPhoto.setPadding(pad, pad, pad, pad)
                    holder.b.imgPhoto.setImageResource(R.drawable.ic_camera)
                    holder.b.imgPhoto.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.tiu_ink))
                    holder.b.imgPhoto.visibility = View.VISIBLE
                    holder.b.tvDay.visibility = View.GONE
                    holder.b.dot.visibility = View.GONE
                    holder.b.dayContainer.setBackgroundResource(R.drawable.bg_doodle_circle)
                    holder.b.dayContainer.backgroundTintList = null
                } else {
                    Glide.with(holder.itemView.context).clear(holder.b.imgPhoto)
                    holder.b.imgPhoto.visibility = View.GONE
                    holder.b.tvDay.visibility = View.VISIBLE
                    holder.b.tvDay.text = date.dayOfMonth.toString()
                    holder.b.dot.visibility = View.GONE
                    holder.b.dayContainer.setBackgroundResource(R.drawable.bg_doodle_circle)
                    holder.b.dayContainer.backgroundTintList = null
                }
            } else {
                Glide.with(holder.itemView.context).clear(holder.b.imgPhoto)
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
