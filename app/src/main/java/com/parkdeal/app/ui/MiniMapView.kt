package com.parkdeal.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.parkdeal.core.ParkingOption
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max

/**
 * 목적지와 후보 주차장을 실제 좌표 비율대로 찍는 간이 지도. 도로는 그리지 않는다.
 * 1순위(선택)는 초록, 나머지는 회색 번호 핀.
 */
class MiniMapView(context: Context) : View(context) {
    var destLat = 0.0
    var destLng = 0.0
    var options: List<ParkingOption> = emptyList()
    var selected = 0

    private val d = context.resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        fill.color = Ui.CARD
        c.drawRoundRect(RectF(0f, 0f, w, h), 32 * d, 32 * d, fill)
        // 옅은 격자 (축척 감)
        fill.color = Ui.RAISED
        var x = 0f; while (x < w) { c.drawRect(x, 0f, x + 2 * d, h, fill); x += 120 * d }
        var y = 0f; while (y < h) { c.drawRect(0f, y, w, y + 2 * d, fill); y += 120 * d }
        if (options.isEmpty()) return

        // 목적지 기준 미터 좌표 → 화면 (가장 먼 핀이 테두리 안쪽 80dp 에 오도록)
        val k = 111_000.0; val kx = k * cos(Math.toRadians(destLat))
        // 좌표가 없는 주차장은 핀을 찍지 않는다 (번호는 목록 순서를 유지)
        val pts = options.map { o -> val la = o.lat; val ln = o.lng; if (la == null || ln == null) null else Pair((ln - destLng) * kx, (la - destLat) * k) }
        val known = pts.filterNotNull()
        if (known.isEmpty()) return
        val maxM = max(60.0, known.maxOf { max(abs(it.first), abs(it.second)) })
        val scale = (minOf(w, h) / 2 - 80 * d) / maxM
        fun sx(m: Double) = (w / 2 + m * scale).toFloat()
        fun sy(m: Double) = (h / 2 - m * scale).toFloat()

        // 목적지
        fill.color = BG; c.drawCircle(w / 2, h / 2, 22 * d, fill)
        fill.color = Ui.AMBER; c.drawCircle(w / 2, h / 2, 14 * d, fill)
        text.color = Ui.TEXT; text.textSize = 22 * d
        c.drawText("목적지", w / 2, h / 2 - 34 * d, text)

        // 핀: 선택된 것을 마지막에 그려 위로
        val order = pts.indices.filter { pts[it] != null }.sortedBy { if (it == selected) 1 else 0 }
        for (i in order) {
            val (mx, my) = pts[i]!!
            val r = (if (i == selected) 32 else 26) * d
            fill.color = BG; c.drawCircle(sx(mx), sy(my), r + 4 * d, fill)
            fill.color = if (i == selected) Ui.ACCENT else Ui.PIN; c.drawCircle(sx(mx), sy(my), r, fill)
            text.color = if (i == selected) Color.WHITE else Ui.TEXT; text.textSize = (if (i == selected) 30 else 26) * d
            c.drawText("${i + 1}", sx(mx), sy(my) + text.textSize / 3, text)
        }
        text.color = Ui.FAINT; text.textSize = 20 * d; text.textAlign = Paint.Align.LEFT; text.isFakeBoldText = false
        c.drawText("실제 위치 비율 · 위가 북쪽", 24 * d, h - 24 * d, text)
        text.textAlign = Paint.Align.CENTER; text.isFakeBoldText = true
    }

    private val BG get() = Ui.CARD
}
