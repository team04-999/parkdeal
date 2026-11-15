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
    val BG = Color.parseColor("#0E1013")
    val CARD = Color.parseColor("#1B1F24")
    val RAISED = Color.parseColor("#262B32")
    val BUTTON = Color.parseColor("#2C323A")
    val PIN = Color.parseColor("#3A424C")
    val TEXT = Color.parseColor("#F2F4F6")
    val TEXT2 = Color.parseColor("#DCE1E6")
    val MUTED = Color.parseColor("#A7B0BA")
    val FAINT = Color.parseColor("#8C96A1")
    val ACCENT = Color.parseColor("#3D8BFF")
    val ACCENT_TEXT = Color.parseColor("#7DB2FF")
    val ACCENT_BG = Color.parseColor("#16273F")
    val GREEN = Color.parseColor("#5BE49B")
    val GREEN_BG = Color.parseColor("#123524")
    val AMBER = Color.parseColor("#FFB020")
    val GLEO = Color.parseColor("#B9A8FF")
    val GLEO_BG = Color.parseColor("#1E1A33")

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
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.DEFAULT, 700, false)
            background = rounded(if (primary) ACCENT else BUTTON, 32, ctx)
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
