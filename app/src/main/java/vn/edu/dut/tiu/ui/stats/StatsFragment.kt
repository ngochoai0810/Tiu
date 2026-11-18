package vn.edu.dut.tiu.ui.stats

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import vn.edu.dut.tiu.R
import vn.edu.dut.tiu.databinding.FragmentStatsBinding
import vn.edu.dut.tiu.ui.common.appContainer
import vn.edu.dut.tiu.ui.common.collectOnStarted
import vn.edu.dut.tiu.ui.common.setup
import vn.edu.dut.tiu.util.DateUtils
import vn.edu.dut.tiu.util.MoneyFormat
import java.time.LocalDate
import java.time.YearMonth

class StatsFragment : Fragment(R.layout.fragment_stats) {

    private val vm: StatsViewModel by viewModels {
        viewModelFactory {
            initializer { StatsViewModel(appContainer.transactionRepository) }
        }
    }

    private val chartColors = listOf(
        Color.parseColor("#E96F62"), // Cam đỏ
        Color.parseColor("#FFB780"), // Cam pastel
        Color.parseColor("#83C69B"), // Xanh mint
        Color.parseColor("#6CA0DC"), // Xanh biển nhạt
        Color.parseColor("#DF8038"), // Cam đất
        Color.parseColor("#A882DD")  // Tím pastel
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentStatsBinding.bind(view)
        b.topBar.setup(this)

        // Filter tabs
        b.tabWeek.setOnClickListener { vm.setFilter(StatsFilter.WEEK) }
        b.tabMonth.setOnClickListener { vm.setFilter(StatsFilter.MONTH) }
        b.tabYear.setOnClickListener { vm.setFilter(StatsFilter.YEAR) }

        collectOnStarted(vm.state) { s ->
            b.textTitle.text = s.title
            
            // Switch tabs
            val tabs = listOf(b.tabWeek, b.tabMonth, b.tabYear)
            tabs.forEachIndexed { i, tab ->
                val isSelected = i == s.filter.ordinal
                if (isSelected) {
                    tab.setBackgroundResource(R.drawable.bg_toggle_selected)
                    tab.setTextColor(requireContext().getColor(R.color.tiu_ink))
                } else {
                    tab.setBackgroundResource(0)
                    tab.setTextColor(requireContext().getColor(R.color.tiu_text_secondary))
                }
            }

            b.tvTotalExpense.text = "-${MoneyFormat.format(s.totalExpense)}"
            b.tvTotalIncome.text = "+${MoneyFormat.format(s.totalIncome)}"

            val chartItems = s.expensesByCategory.mapIndexed { index, (catName, amount) ->
                val color = chartColors[index % chartColors.size]
                DonutChartView.Item(label = catName, amount = amount, color = color)
            }

            val totalText = if (s.totalExpense >= 1_000_000L) {
                String.format(java.util.Locale.US, "%.2ftr", s.totalExpense / 1_000_000.0)
            } else {
                MoneyFormat.format(s.totalExpense)
            }
            b.donutChart.setData(chartItems, totalText)

            // Render legend
            b.layoutLegend.removeAllViews()
            if (s.expensesByCategory.isEmpty()) {
                val emptyTv = TextView(context).apply {
                    text = "Chưa có chi tiêu"
                    setTextAppearance(R.style.TextAppearance_Tiu_Caption)
                }
                b.layoutLegend.addView(emptyTv)
            } else {
                s.expensesByCategory.forEachIndexed { index, (catName, amount) ->
                    val percent = if (s.totalExpense > 0) ((amount.toDouble() / s.totalExpense) * 100).toInt() else 0
                    val color = chartColors[index % chartColors.size]
                    b.layoutLegend.addView(createLegendRow(catName, percent, color))
                }
            }

            // Gợi ý thông minh
            b.tvSmartInsightTitle.text = s.insightTitle
            b.tvSmartInsightBody.text = s.insightBody
        }
    }

    private fun createLegendRow(name: String, percent: Int, color: Int): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 6, 0, 6)
        }

        val dot = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(18, 18).apply { marginEnd = 12 }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
        }
        row.addView(dot)

        val nameTv = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = name
            maxLines = 1
            setTextAppearance(R.style.TextAppearance_Tiu_BodySmall)
        }
        row.addView(nameTv)

        val percentTv = TextView(context).apply {
            text = "$percent%"
            setTextAppearance(R.style.TextAppearance_Tiu_Label)
        }
        row.addView(percentTv)

        return row
    }
}
