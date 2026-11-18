package vn.edu.dut.tiu.ui.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import vn.edu.dut.tiu.R

class DonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    data class Item(val label: String, val amount: Long, val color: Int)

    private var items: List<Item> = emptyList()
    private var centerAmountText: String = "0 ₫"
    private var centerSubText: String = "Tổng chi"

    private val inkColor = ContextCompat.getColor(context, R.color.tiu_ink)

    private val slicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val strokeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = inkColor
        strokeWidth = 3f
    }

    private val textAmountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = inkColor
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val textSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.tiu_text_secondary)
        textAlign = Paint.Align.CENTER
    }

    private val rect = RectF()

    fun setData(newItems: List<Item>, totalText: String) {
        items = newItems
        centerAmountText = totalText
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = width.coerceAtMost(height).toFloat()
        if (size <= 0) return

        val strokeW = size * 0.16f
        slicePaint.strokeWidth = strokeW

        val pad = strokeW / 2f + 8f
        rect.set(pad, pad, size - pad, size - pad)

        val total = items.sumOf { it.amount }
        if (total <= 0L || items.isEmpty()) {
            slicePaint.color = Color.parseColor("#EAE0D5")
            canvas.drawArc(rect, 0f, 360f, false, slicePaint)
            canvas.drawArc(rect, 0f, 360f, false, strokeBorderPaint)
        } else {
            var startAngle = -90f
            for (item in items) {
                val sweep = (item.amount.toFloat() / total.toFloat()) * 360f
                if (sweep > 0.5f) {
                    slicePaint.color = item.color
                    canvas.drawArc(rect, startAngle, sweep, false, slicePaint)
                    startAngle += sweep
                }
            }
            // Viền ngoài và trong kiểu doodle
            canvas.drawCircle(rect.centerX(), rect.centerY(), rect.width() / 2f + strokeW / 2f, strokeBorderPaint)
            canvas.drawCircle(rect.centerX(), rect.centerY(), rect.width() / 2f - strokeW / 2f, strokeBorderPaint)
        }

        // Chữ ở tâm
        textAmountPaint.textSize = size * 0.11f
        textSubPaint.textSize = size * 0.065f

        val centerY = rect.centerY()
        canvas.drawText(centerAmountText, rect.centerX(), centerY + (textAmountPaint.textSize * 0.1f), textAmountPaint)
        canvas.drawText(centerSubText, rect.centerX(), centerY + (textAmountPaint.textSize * 0.9f), textSubPaint)
    }
}
