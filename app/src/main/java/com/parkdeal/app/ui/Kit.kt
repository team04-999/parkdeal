package com.parkdeal.app.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.parkdeal.app.ui.Ui.MATCH
import com.parkdeal.app.ui.Ui.WRAP
import com.parkdeal.app.ui.Ui.dp
import com.parkdeal.app.ui.Ui.lp

/**
 * PRD 시안의 부품. 크기는 시안 px = dp (시안 앱 패널 1707×1210 과 우리 앱 영역 1682×1210 이 거의 같다).
 * 시안을 그대로 베끼지 않고, 넓은 창에서는 [twoCol] 로 두 열을 쓴다.
 */
class Kit(private val c: Context) {
    /** 맨 위 한 줄: 앱 이름 / 상태. [extra] 는 오른쪽 끝(말로 묻기 등) */
    fun status(right: String, extra: View? = null): View = Ui.hbox(c, 16).apply {
        addView(Ui.text(c, "빡딜", 27, Ui.TEXT2, 700))
        addView(Ui.grow(c))
        if (right.isNotEmpty()) addView(Ui.text(c, right, 27, Ui.TEXT2))
        extra?.let { addView(it) }
    }

    fun label(s: String, color: Int = Ui.ACCENT_TEXT) = Ui.text(c, s, 30, color, 700)
    fun title(s: String, size: Int = 48) = Ui.text(c, s, size, Ui.TEXT, 900).apply { setLineSpacing(0f, 1.2f) }
    fun small(s: String, size: Int = 27, color: Int = Ui.TEXT2) = Ui.text(c, s, size, color).apply { setLineSpacing(0f, 1.35f) }

    /** 금액 두 칸: (라벨, 금액, 파랑?) × 2 */
    fun split(a: Triple<String, String, Boolean>, b: Triple<String, String, Boolean>?, value: Int = 66): View = Ui.hbox(c, 18).apply {
        for (x in listOfNotNull(a, b)) addView(Ui.vbox(c, 6).apply {
            setPadding(c.dp(30), c.dp(24), c.dp(30), c.dp(24))
            background = Ui.rounded(Ui.CARD, 30, c)
            addView(Ui.text(c, x.first, 22, Ui.MUTED))
            addView(Ui.text(c, x.second, value, if (x.third) Ui.ACCENT_TEXT else Ui.TEXT, 900).apply { maxLines = 1 })
        }, lp(0, WRAP, 1f))
    }

    /** 표: (라벨, 값, 강조?) */
    fun kv(rows: List<Triple<String, String, Boolean>>): View = Ui.vbox(c, 12).apply {
        setPadding(c.dp(30), c.dp(24), c.dp(30), c.dp(24))
        background = Ui.rounded(Ui.CARD, 30, c)
        rows.forEach { (k, v, hi) ->
            addView(Ui.hbox(c, 24).apply {
                addView(Ui.text(c, k, 25, Ui.MUTED))
                addView(Ui.text(c, v, 25, if (hi) Ui.ACCENT_TEXT else Ui.TEXT, 700).apply { gravity = Gravity.END }, lp(0, WRAP, 1f))
            })
        }
    }

    fun big(s: String, primary: Boolean = true, onClick: () -> Unit): View =
        Ui.button(c, s, primary, if (primary) 126 else 102, if (primary) 39 else 30, onClick)

    /** 아래 버튼 모양의 두 칸 안내(왼쪽 글, 오른쪽 파랑) */
    fun wide(left: String, right: String, onClick: (() -> Unit)? = null): View = Ui.hbox(c).apply {
        setPadding(c.dp(36), 0, c.dp(36), 0); minimumHeight = c.dp(102)
        background = Ui.rounded(Ui.RAISED, 30, c)
        addView(Ui.text(c, left, 30, Ui.TEXT, 700))
        addView(Ui.grow(c))
        addView(Ui.text(c, right, 30, Ui.ACCENT_TEXT, 700))
        onClick?.let { f -> setOnClickListener { f() } }
    }

    /** 경고 띠 */
    fun stale(s: String): View = Ui.text(c, s, 25, Ui.STALE_FG, 700).apply {
        setPadding(c.dp(24), c.dp(18), c.dp(24), c.dp(18)); background = Ui.rounded(Ui.STALE_BG, 21, c)
    }

    fun chip(s: String): View = Ui.text(c, s, 27, Ui.TEXT, 700).apply {
        setPadding(c.dp(24), c.dp(12), c.dp(24), c.dp(12)); background = Ui.rounded(Ui.RAISED, 999, c)
    }

    fun num(n: Int, on: Boolean, size: Int = 54): View = Ui.text(c, "$n", size * 25 / 54, if (on) Ui.ON_ACCENT else Ui.TEXT, 900).apply {
        gravity = Gravity.CENTER; background = Ui.rounded(if (on) Ui.ACCENT else Ui.RAISED, 999, c); layoutParams = lp(c.dp(size), c.dp(size))
    }

    fun walkBadge(s: String, size: Int = 22): View = Ui.text(c, s, size, Ui.TEXT2, 700).apply {
        setPadding(c.dp(12), c.dp(4), c.dp(12), c.dp(4)); background = Ui.rounded(Ui.RAISED, 12, c); maxLines = 1
    }

    fun warnTag(s: String, size: Int = 20): View = Ui.text(c, s, size, Ui.STALE_FG, 700).apply {
        setPadding(c.dp(12), c.dp(4), c.dp(12), c.dp(4)); background = Ui.rounded(Ui.STALE_BG, 12, c); maxLines = 1
    }

    /** 후보 한 줄 (PRD .crow): 번호 / 이름·배지 / 한 줄 / 낼 돈·작은 글 */
    fun row(n: Int, name: String, badges: List<View>, line: String, fee: String, zero: Boolean, small: String?, on: Boolean = false, onClick: () -> Unit): View =
        Ui.hbox(c, 18).apply {
            setPadding(c.dp(24), c.dp(18), c.dp(24), c.dp(18)); minimumHeight = c.dp(108)
            background = Ui.rounded(Ui.CARD, 24, c, if (on) Ui.ACCENT else null)
            addView(num(n, on, 48))
            addView(Ui.vbox(c, 4).apply {
                addView(Ui.hbox(c, 12).apply {
                    addView(Ui.text(c, name, 28, Ui.TEXT, 700).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, lp(0, WRAP, 1f))
                    badges.forEach { addView(it) }
                })
                if (line.isNotEmpty()) addView(Ui.text(c, line, 21, Ui.TEXT2).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            }, lp(0, WRAP, 1f))
            addView(Ui.vbox(c).apply {
                gravity = Gravity.END
                addView(Ui.text(c, fee, 33, if (zero) Ui.ACCENT_TEXT else Ui.TEXT, 900))
                small?.let { addView(Ui.text(c, it, 19, Ui.MUTED)) }
            })
            setOnClickListener { onClick() }
        }

    /** 목록 머리: 왼쪽 제목 / 오른쪽 안내 */
    fun listHead(left: String, right: String): View = Ui.hbox(c).apply {
        setPadding(c.dp(6), 0, c.dp(6), 0)
        addView(Ui.text(c, left, 24, Ui.MUTED)); addView(Ui.grow(c)); addView(Ui.text(c, right, 24, Ui.MUTED))
    }

    /** 세로 스크롤 */
    fun scroll(child: View): View = ScrollView(c).apply { isVerticalScrollBarEnabled = false; addView(child) }

    /** 가로 칩 줄 (PRD .stay) */
    fun stay(options: List<Pair<String, Boolean>>, onPick: (Int) -> Unit): View = HorizontalScrollView(c).apply {
        isHorizontalScrollBarEnabled = false
        background = Ui.rounded(Ui.CARD, 24, c)
        setPadding(c.dp(9), c.dp(9), c.dp(9), c.dp(9))
        addView(Ui.hbox(c, 9).apply {
            options.forEachIndexed { i, (t, on) ->
                addView(Ui.text(c, t, 27, if (on) Ui.ON_ACCENT else Ui.TEXT2, 700).apply {
                    setPadding(c.dp(27), c.dp(18), c.dp(27), c.dp(18))
                    if (on) background = Ui.rounded(Ui.ACCENT, 18, c)
                    setOnClickListener { onPick(i) }
                })
            }
        })
    }

    /** 요금 단계표 (PRD .lad) */
    fun ladder(rows: List<Triple<String, String, Boolean>>): View = Ui.vbox(c, 6).apply {
        setPadding(c.dp(18), c.dp(18), c.dp(18), c.dp(18))
        background = Ui.rounded(Ui.CARD, 30, c)
        rows.forEach { (k, v, now) ->
            addView(Ui.hbox(c, 12).apply {
                setPadding(c.dp(18), c.dp(14), c.dp(18), c.dp(14))
                if (now) background = Ui.rounded(Ui.RAISED, 18, c)
                addView(Ui.text(c, k, 25, Ui.TEXT2, if (now) 700 else 400), lp(0, WRAP, 1f))
                if (now) addView(Ui.text(c, "지금", 19, Ui.ON_ACCENT, 700).apply {
                    setPadding(c.dp(10), c.dp(3), c.dp(10), c.dp(3)); background = Ui.rounded(Ui.ACCENT, 999, c) })
                addView(Ui.text(c, v, 27, Ui.TEXT, 700))
            })
        }
    }

    /** 혜택 가게 줄 (PRD .deal / .pick): [radio] 가 null 이면 고정 줄, true/false 면 고르는 줄 */
    fun deal(store: String, cond: String, fee: String, radio: Boolean?, onClick: (() -> Unit)? = null): View = Ui.hbox(c, 18).apply {
        setPadding(c.dp(24), c.dp(15), c.dp(24), c.dp(15))
        background = if (radio == null) Ui.rounded(Ui.RAISED, 21, c)
            else Ui.rounded(if (radio) Ui.ACCENT_BG else Ui.CARD, 21, c, if (radio) Ui.ACCENT else Ui.RAISED, 2)
        radio?.let { on ->
            addView(View(c).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setStroke(c.dp(3), if (on) Ui.ACCENT else Ui.MUTED); if (on) setColor(Ui.ACCENT) }
                layoutParams = lp(c.dp(28), c.dp(28))
            })
        }
        addView(Ui.vbox(c).apply {
            addView(Ui.text(c, store, 25, Ui.TEXT, 700).apply { maxLines = 1 })
            addView(Ui.text(c, cond, 22, Ui.TEXT2).apply { maxLines = 1 })
        }, lp(0, WRAP, 1f))
        addView(Ui.text(c, fee, if (radio == null) 36 else 30, Ui.ACCENT_TEXT, 900))
        onClick?.let { f -> setOnClickListener { f() } }
    }

    /** 넓은 창은 두 열, 좁은 창은 위아래로. 열 안쪽 간격은 [gap] */
    fun twoCol(compact: Boolean, left: View, right: View?, leftWeight: Float = 1f): View =
        if (compact || right == null) Ui.vbox(c, 21).apply {
            addView(left, lp(MATCH, if (right == null) MATCH else WRAP))
            right?.let { addView(it, lp(MATCH, 0, 1f)) }
        } else Ui.hbox(c, 48).apply {
            gravity = Gravity.TOP
            addView(left, lp(0, MATCH, leftWeight))
            addView(right, lp(0, MATCH, 1f))
        }

    fun col(gap: Int = 21, vararg views: View?): LinearLayout = Ui.vbox(c, gap).apply {
        views.filterNotNull().forEach { addView(it, if (it.tag == SPRING) lp(MATCH, 0, 1f) else lp(MATCH, WRAP)) }
    }

    /** 세로 칸 사이를 밀어 내는 빈칸 */
    fun spring(): View = View(c).apply { tag = SPRING; layoutParams = lp(MATCH, 0, 1f) }

    private companion object { const val SPRING = "spring" }

    fun pad(v: LinearLayout): LinearLayout = v.apply { setPadding(c.dp(48), c.dp(42), c.dp(48), c.dp(36)) }

    @Suppress("unused") fun tv(v: View) = v as TextView
}
