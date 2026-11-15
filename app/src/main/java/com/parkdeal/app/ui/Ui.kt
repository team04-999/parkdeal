package com.parkdeal.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** 화면 색·글자·버튼. 크기는 앱 영역 1680×1210 기준 dp. 어두운 배경에 대비 4.5:1 이상 (docs/pleos-guidelines.md 2장). */
object Ui {
    /** PRD 디자인 체계: Pleos 시스템 화면과 같이 낮에는 밝게, 밤에는 어둡게. 강조색 #5A46FA. */
    private class Palette(
        val win: String, val card: String, val card2: String, val ink: String, val ink2: String, val ink3: String,
        val blue: String, val blueBg: String, val hot: String, val hotBg: String,
    )
    private val DAY = Palette("#ECECF0", "#FFFFFF", "#F1F1F5", "#0D0D0D", "#32353F", "#5B606C", "#5A46FA", "#ECE9FF", "#C26A00", "#FFF1DD")
    private val NIGHT = Palette("#0D0D0D", "#171717", "#32353F", "#FAFAFA", "#AAAAAA", "#8F95A1", "#8D84FB", "#221E3D", "#FF9900", "#33220A")
    private var p = DAY
    var night = false
        private set

    /** 화면을 그리기 전에 부른다. */
    fun theme(night: Boolean) { this.night = night; p = if (night) NIGHT else DAY }

    private fun c(hex: String) = Color.parseColor(hex)
    val BG get() = c(p.win)
    val CARD get() = c(p.card)
    val RAISED get() = c(p.card2)
    val BUTTON get() = c(p.card2)
    val PIN get() = c(p.card2)
    val TEXT get() = c(p.ink)
    val TEXT2 get() = c(p.ink2)
    val MUTED get() = c(p.ink3)
    val FAINT get() = c(p.ink3)
    val ACCENT get() = c("#5A46FA")
    val ACCENT_TEXT get() = c(p.blue)
    val ACCENT_BG get() = c(p.blueBg)
    /** 0원·혜택: PRD 는 강조색으로 쓴다 */
    val GREEN get() = c(p.blue)
    val GREEN_BG get() = c(p.blueBg)
    val AMBER get() = c(p.hot)
    val AMBER_BG get() = c(p.hotBg)
    val GLEO get() = c(p.blue)
    val GLEO_BG get() = c(p.blueBg)
    val ON_ACCENT = Color.WHITE

    fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    fun text(ctx: Context, s: CharSequence, size: Int, color: Int = TEXT, weight: Int = 400): TextView =
        TextView(ctx).apply {
            text = s
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, size.toFloat())
            setTextColor(color)
            typeface = Typeface.create(Typeface.DEFAULT, weight, false)
            includeFontPadding = false
        }

    fun rounded(color: Int, radius: Int, ctx: Context, stroke: Int? = null, strokeWidth: Int = 3) = GradientDrawable().apply {
        cornerRadius = ctx.dp(radius).toFloat()
        setColor(color)
        stroke?.let { setStroke(ctx.dp(strokeWidth), it) }
    }

    fun button(ctx: Context, s: String, primary: Boolean, height: Int, size: Int, onClick: () -> Unit): Button =
        Button(ctx).apply {
            text = s
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, size.toFloat())
            setTextColor(if (primary) ON_ACCENT else TEXT)
            typeface = Typeface.create(Typeface.DEFAULT, 700, false)
            background = rounded(if (primary) ACCENT else BUTTON, 30, ctx)
            stateListAnimator = null
            gravity = Gravity.CENTER
            minHeight = ctx.dp(height)
            setOnClickListener { onClick() }
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
}
