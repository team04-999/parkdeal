package com.parkdeal.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 화면의 색·글자·기본 부품. 크기는 앱 영역 1680×1210 기준 dp.
 *
 * 디자인 원칙 (가격표 보드):
 *  - 색은 먹(글자·주요 버튼)과 회색 면뿐이고, **노란 가격표 하나만 색을 쓴다.** 노랑 = 가게 혜택으로 내는 돈.
 *    진한 노랑은 목적지 가게 혜택(그냥 받는 돈), 옅은 노랑은 다른 가게를 이용해야 받는 혜택.
 *    낮·밤 모두 같은 "노랑 바탕에 검은 숫자"라 1~2초 안에 눈이 먼저 간다 (docs/pleos-guidelines.md 2장).
 *  - 경고(만차·정차 금지·요금 인상)만 주홍. 그 밖의 강조는 굵기와 크기로 한다.
 *  - 글자 크기는 [CAP]·[BODY]·[LEAD]·[TITLE]·[DISPLAY] 다섯 단계와 화면마다 하나뿐인 큰 숫자.
 *  - 면은 세 겹: 바닥([GROUND]) · 떠 있는 판([SURFACE]) · 눌린 홈([SUNKEN]). 카드 안에 카드를 넣지 않는다.
 */
object Ui {
    private class Palette(
        val ground: String, val surface: String, val sunken: String, val line: String,
        val ink: String, val ink2: String, val ink3: String,
        val tag: String, val warn: String, val warnBg: String,
        val tagSoft: String, val tagSoftInk: String,
    )
    // 대비: ink3 도 바닥 위에서 4.5:1 이상
    private val DAY = Palette("#EDEEF1", "#FFFFFF", "#E1E3E8", "#D3D6DC", "#111318", "#434854", "#5F6573", "#FFD21F", "#B82E06", "#FBE4DB", "#FFEFA3", "#111318")
    private val NIGHT = Palette("#0C0D10", "#1A1C21", "#272A31", "#32363E", "#F4F5F7", "#B6BBC6", "#8D93A0", "#F5C400", "#FF8558", "#3A1E13", "#3D340B", "#F5C400")
    private var p = DAY
    var night = false
        private set

    /** 화면을 그리기 전에 부른다. */
    fun theme(night: Boolean) { this.night = night; p = if (night) NIGHT else DAY }

    private fun c(hex: String) = Color.parseColor(hex)
    val GROUND get() = c(p.ground)
    val SURFACE get() = c(p.surface)
    val SUNKEN get() = c(p.sunken)
    /** 홈 안에서 고른 칸(머물 시간). 밤에는 홈보다 밝아야 떠 보인다 */
    val KNOB get() = c(if (night) "#444955" else "#FFFFFF")
    val LINE get() = c(p.line)
    val INK get() = c(p.ink)
    val INK2 get() = c(p.ink2)
    val INK3 get() = c(p.ink3)
    /** 주요 버튼(먹 바탕) 위의 글자 */
    val ON_INK get() = c(if (night) "#111318" else "#FFFFFF")
    /** 노란 가격표와 그 위의 숫자. 낮·밤 같은 조합 */
    val TAG get() = c(p.tag)
    val TAG_INK = Color.parseColor("#111318")
    /** 옅은 가격표: 목적지가 아닌 다른 가게를 이용해야 받는 혜택가 */
    val TAG_SOFT get() = c(p.tagSoft)
    val TAG_SOFT_INK get() = c(p.tagSoftInk)
    val WARN get() = c(p.warn)
    val WARN_BG get() = c(p.warnBg)

    // 글자 단계
    const val CAP = 22
    const val BODY = 26
    const val LEAD = 32
    const val TITLE = 44
    const val DISPLAY = 64

    fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    fun text(ctx: Context, s: CharSequence, size: Int, color: Int = INK, weight: Int = 400): TextView =
        TextView(ctx).apply {
            text = s
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, size.toFloat())
            setTextColor(color)
            typeface = Typeface.create(Typeface.DEFAULT, weight, false)
            includeFontPadding = false
            // 금액·시간이 세로로 줄 맞게 고정 폭 숫자
            fontFeatureSettings = "tnum"
            if (size >= 40) letterSpacing = -0.02f
            // 한글을 글자 중간이 아니라 어절에서 끊는다 ("하루 최/대" 방지)
            if (android.os.Build.VERSION.SDK_INT >= 33) lineBreakWordStyle = android.graphics.text.LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE
        }

    fun rounded(color: Int, radius: Int, ctx: Context, stroke: Int? = null, strokeWidth: Int = 2) = GradientDrawable().apply {
        cornerRadius = ctx.dp(radius).toFloat()
        setColor(color)
        stroke?.let { setStroke(ctx.dp(strokeWidth), it) }
    }

    /** 누르면 바로 번지는 표시(0.25초 안 피드백)와 함께 누를 수 있게 한다. */
    fun tap(v: View, radius: Int, onClick: () -> Unit) {
        v.isClickable = true
        v.foreground = RippleDrawable(
            ColorStateList.valueOf(if (night) 0x33FFFFFF else 0x24000000), null, rounded(Color.WHITE, radius, v.context))
        v.setOnClickListener { onClick() }
    }

    /** 큰 버튼. 주요 버튼은 먹 바탕, 보조는 눌린 홈 색. [enabled] 가 false 면 흐리고 눌리지 않는다. */
    fun button(ctx: Context, s: String, primary: Boolean, height: Int, size: Int, enabled: Boolean = true, onClick: () -> Unit): TextView =
        text(ctx, s, size, if (primary) ON_INK else INK, 700).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(ctx.dp(32), 0, ctx.dp(32), 0)
            minimumHeight = ctx.dp(height); minHeight = ctx.dp(height)
            background = rounded(if (primary) INK else SUNKEN, 24, ctx)
            if (enabled) tap(this, 24, onClick) else alpha = 0.35f
        }

    fun vbox(ctx: Context, gap: Int = 0): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        if (gap > 0) { showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE; dividerDrawable = spacer(ctx, gap) }
    }

    fun hbox(ctx: Context, gap: Int = 0): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        if (gap > 0) { showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE; dividerDrawable = spacer(ctx, gap) }
    }

    private fun spacer(ctx: Context, gap: Int) = GradientDrawable().apply { setSize(ctx.dp(gap), ctx.dp(gap)); setColor(Color.TRANSPARENT) }

    fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
    const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

    fun grow(ctx: Context): View = View(ctx).apply { layoutParams = lp(1, 0, 1f) }

    fun won(v: Int?): String = v?.let { "%,d원".format(it) } ?: "요금 확인 필요"

    /**
     * 보드 한 줄의 바탕: 고른 줄은 떠 있는 판 + 왼쪽 먹 막대, 나머지는 아래 가는 줄.
     * 줄마다 카드를 만들지 않아 금액 열이 한 줄로 읽힌다.
     */
    class RowBg(ctx: Context, private val selected: Boolean, private val line: Boolean) : Drawable() {
        private val d = ctx.resources.displayMetrics.density
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val surface = SURFACE; private val ink = INK; private val lineColor = LINE
        override fun draw(c: Canvas) {
            val b = bounds
            if (selected) {
                paint.color = surface
                c.drawRoundRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), 22 * d, 22 * d, paint)
                paint.color = ink
                c.drawRoundRect(b.left.toFloat(), b.top + 22 * d, b.left + 6 * d, b.bottom - 22 * d, 3 * d, 3 * d, paint)
            } else if (line) {
                paint.color = lineColor
                c.drawRect(b.left + 24 * d, b.bottom - 2 * d, b.right - 24 * d, b.bottom.toFloat(), paint)
            }
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(cf: ColorFilter?) {}
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
