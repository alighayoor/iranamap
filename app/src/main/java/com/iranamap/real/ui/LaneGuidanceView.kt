package com.iranamap.real.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.iranamap.real.core.LanePlan
import com.iranamap.real.core.LanePlanner

/** نمایش گرافیکی راهنمای لاین (لاین‌های پیشنهادی با رنگ متمایز). */
class LaneGuidanceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var plan: LanePlan? = null

    private val lanePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#33FFFFFF")
    }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 20f * resources.displayMetrics.scaledDensity
    }

    fun setPlan(lanePlan: LanePlan?) {
        plan = lanePlan
        visibility = if (lanePlan == null || lanePlan.lanes.isEmpty()) View.GONE else View.VISIBLE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val current = plan ?: return
        if (current.lanes.isEmpty()) return

        val count = current.lanes.size
        val padding = 6f * resources.displayMetrics.density
        val gap = 8f * resources.displayMetrics.density
        val availableWidth = width - padding * 2
        val laneWidth = ((availableWidth - gap * (count - 1)) / count).coerceAtMost(46f * resources.displayMetrics.density)
        val totalWidth = laneWidth * count + gap * (count - 1)
        var left = (width - totalWidth) / 2f
        val top = padding
        val bottom = height - padding

        for (lane in current.lanes) {
            val rect = RectF(left, top, left + laneWidth, bottom)
            lanePaint.color = if (lane.active) Color.parseColor("#0B6E4F") else Color.parseColor("#22FFFFFF")
            canvas.drawRoundRect(rect, 10f, 10f, lanePaint)
            canvas.drawRoundRect(rect, 10f, 10f, borderPaint)

            val glyph = lane.directions.firstOrNull()?.let { LanePlanner.directionGlyph(it) } ?: "⬆"
            glyphPaint.color = if (lane.active) Color.WHITE else Color.parseColor("#9FAEC6")
            val textY = rect.centerY() - (glyphPaint.descent() + glyphPaint.ascent()) / 2f
            canvas.drawText(glyph, rect.centerX(), textY, glyphPaint)
            left += laneWidth + gap
        }
    }
}
