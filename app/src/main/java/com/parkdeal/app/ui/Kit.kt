package com.parkdeal.app.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.parkdeal.app.ui.Ui.MATCH
import com.parkdeal.app.ui.Ui.WRAP
import com.parkdeal.app.ui.Ui.dp
import com.parkdeal.app.ui.Ui.lp

/**
 * 화면 부품. 원칙은 [Ui] 머리말 참고.
 * 넓은 창은 [twoCol] 로 두 열, 내비 옆 반쪽 창은 한 열로 쌓는다.
 */
class Kit(private val c: Context) {
    /** 맨 위 한 줄: 앱 이름 · 지금 상태 / 오른쪽 끝 [extra] (말로 묻기) */
    fun topBar(state: String, extra: View? = null): View = Ui.hbox(c, 18).apply {
        minimumHeight = c.dp(72)
        addView(Ui.text(c, "빡딜", Ui.BODY, Ui.INK, 800))
        if (state.isNotEmpty()) {
            addView(View(c).apply { setBackgroundColor(Ui.LINE) }, lp(c.dp(2), c.dp(24)))
            addView(Ui.text(c, state, Ui.BODY, Ui.INK2, 600).apply { maxLines = 1 })
        }
        addView(Ui.grow(c))
        extra?.let { addView(it) }
    }

    /** 제목 위의 한 줄: 지금 무슨 장면인지 */
    fun label(s: String, color: Int = Ui.INK2) = Ui.text(c, s, 30, color, 700)
    fun title(s: String, size: Int = Ui.DISPLAY) = Ui.text(c, s, size, Ui.INK, 800).apply { setLineSpacing(0f, 1.16f) }
    fun body(s: String, size: Int = Ui.BODY, color: Int = Ui.INK2, weight: Int = 400) =
        Ui.text(c, s, size, color, weight).apply { setLineSpacing(0f, 1.35f) }

    /**
     * 금액. [benefit] 이면 노란 가격표(가게 혜택으로 내는 돈), 아니면 그냥 먹 숫자.
     * 이 앱에서 색이 들어간 곳은 이것 하나다.
     */
    fun price(s: String, size: Int, benefit: Boolean, color: Int = Ui.INK, soft: Boolean = false): TextView =
        Ui.text(c, s, size, if (!benefit) color else if (soft) Ui.TAG_SOFT_INK else Ui.TAG_INK, 800).apply {
            maxLines = 1
            if (benefit) {
                val h = (size * 0.26f).toInt().coerceAtLeast(8); val v = (size * 0.08f).toInt().coerceAtLeast(2)
                setPadding(c.dp(h), c.dp(v), c.dp(h), c.dp(v))
                background = Ui.rounded(if (soft) Ui.TAG_SOFT else Ui.TAG, (size * 0.2f).toInt().coerceIn(6, 28), c)
            }
        }

    /** 금액 두 칸을 한 판에: (라벨, 금액, 혜택가?) × 2. 가운데 세로줄로 나눈다 */
    fun duo(a: Triple<String, String, Boolean>, b: Triple<String, String, Boolean>?, value: Int = 60): View = Ui.hbox(c).apply {
        background = Ui.rounded(Ui.SURFACE, 28, c)
        listOfNotNull(a, b).forEachIndexed { i, x ->
            if (i > 0) addView(View(c).apply { setBackgroundColor(Ui.LINE) }, lp(c.dp(2), MATCH).apply { topMargin = c.dp(24); bottomMargin = c.dp(24) })
            addView(Ui.vbox(c, 10).apply {
                setPadding(c.dp(32), c.dp(26), c.dp(32), c.dp(28))
                addView(Ui.text(c, x.first, 24, Ui.INK3, 600).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
                addView(price(x.second, value, x.third), lp(WRAP, WRAP))
            }, lp(0, WRAP, 1f))
        }
    }

    /** 표: (라벨, 값). 카드 없이 가는 줄로만 나눈다 */
    fun table(rows: List<Pair<String, String>>): View = Ui.vbox(c).apply {
        rows.forEachIndexed { i, (k, v) ->
            if (i > 0) addView(View(c).apply { setBackgroundColor(Ui.LINE) }, lp(MATCH, c.dp(2)))
            addView(Ui.hbox(c, 24).apply {
                gravity = Gravity.TOP
                setPadding(c.dp(4), c.dp(20), c.dp(4), c.dp(20))
                addView(Ui.text(c, k, 24, Ui.INK3, 600), lp(c.dp(170), WRAP))
                addView(Ui.text(c, v, Ui.BODY, Ui.INK, 600).apply { gravity = Gravity.END; setLineSpacing(0f, 1.3f) }, lp(0, WRAP, 1f))
            })
        }
    }

    fun big(s: String, primary: Boolean = true, enabled: Boolean = true, onClick: () -> Unit): View =
        Ui.button(c, s, primary, if (primary) 120 else 100, if (primary) 36 else 30, enabled, onClick)

    /** 가로로 긴 안내 한 줄 (왼쪽 무엇, 오른쪽 값) */
    fun wide(left: String, right: String): View = Ui.hbox(c, 16).apply {
        setPadding(c.dp(32), 0, c.dp(32), 0); minimumHeight = c.dp(96)
        background = Ui.rounded(Ui.SUNKEN, 24, c)
        addView(Ui.text(c, left, 28, Ui.INK2, 600).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, lp(0, WRAP, 1f))
        addView(Ui.text(c, right, 28, Ui.INK, 800).apply { maxLines = 1 })
    }

    /** 경고 한 줄: 주홍 막대 + 주홍 글자 */
    fun warn(s: String): View = Ui.hbox(c, 18).apply {
        setPadding(c.dp(20), c.dp(16), c.dp(24), c.dp(16))
        background = Ui.rounded(Ui.WARN_BG, 18, c)
        addView(View(c).apply { background = Ui.rounded(Ui.WARN, 3, c) }, lp(c.dp(6), MATCH))
        addView(Ui.text(c, s, 25, Ui.WARN, 700).apply { setLineSpacing(0f, 1.3f) }, lp(0, WRAP, 1f))
    }

    /** 조용한 알림 한 줄 (다시 찾는 중 등) */
    fun notice(s: String): View = Ui.text(c, s, 25, Ui.INK, 700).apply {
        setPadding(c.dp(24), c.dp(16), c.dp(24), c.dp(16)); background = Ui.rounded(Ui.SUNKEN, 18, c)
    }

    fun chip(s: String): View = Ui.text(c, s, Ui.BODY, Ui.INK, 700).apply {
        maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        setPadding(c.dp(22), c.dp(12), c.dp(22), c.dp(12)); background = Ui.rounded(Ui.SUNKEN, 14, c)
    }

    /** 작은 딱지. [warn] 이면 주의할 것(만차 잦음·기계식·협소·차종 제한) */
    fun tag(s: String, warn: Boolean = false, size: Int = 20): View =
        Ui.text(c, s, size, if (warn) Ui.WARN else Ui.INK2, 700).apply {
            setPadding(c.dp(10), c.dp(4), c.dp(10), c.dp(4)); maxLines = 1
            background = Ui.rounded(if (warn) Ui.WARN_BG else Ui.SUNKEN, 8, c)
        }

    /** 순위 번호. 안내 중인 곳만 먹 동그라미로 채운다 */
    fun rank(n: Int, filled: Boolean, strong: Boolean): View =
        Ui.text(c, "$n", 28, if (filled) Ui.ON_INK else if (strong) Ui.INK else Ui.INK3, 800).apply {
            gravity = Gravity.CENTER
            if (filled) background = Ui.rounded(Ui.INK, 999, c)
            layoutParams = lp(c.dp(52), c.dp(52))
        }

    /**
     * 보드 한 줄: 번호 / 이름·딱지 + 아래 줄들 / 걷는 시간 열 / 금액 열.
     * 걷는 시간과 금액은 줄마다 같은 자리에 있어 세로로 훑어 비교한다. [below] 는 고른 줄 아래 펼치는 내용.
     */
    fun row(
        n: Int, selected: Boolean, guided: Boolean, name: String, tags: List<View>, lines: List<View>,
        walk: String?, price: View, note: String?, dim: Boolean = false, below: View? = null, minH: Int = 112, onClick: (() -> Unit)? = null,
    ): LinearLayout = Ui.vbox(c, 16).apply {
        setPadding(c.dp(24), c.dp(20), c.dp(24), c.dp(22))
        background = Ui.RowBg(c, selected, true)
        if (dim) alpha = 0.5f
        addView(Ui.hbox(c, 18).apply {
            minimumHeight = c.dp(minH - 42)
            addView(rank(n, guided, selected))
            addView(Ui.vbox(c, 8).apply {
                addView(Ui.hbox(c, 10).apply {
                    // 이름 바로 뒤에 딱지. 이름이 길면 줄이고 딱지는 남긴다
                    // 이름은 남는 폭만 쓰고 줄인다. 딱지는 잘리지 않고 걷는 시간 열 앞에 선다
                    addView(Ui.text(c, name, 30, Ui.INK, 700).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, lp(0, WRAP, 1f))
                    tags.forEach { addView(it) }
                })
                lines.forEach { addView(it) }
            }, lp(0, WRAP, 1f))
            walk?.let { addView(Ui.text(c, it, 24, Ui.INK2, 600).apply { gravity = Gravity.END; maxLines = 1 }, lp(c.dp(124), WRAP)) }
            addView(Ui.vbox(c, 6).apply {
                gravity = Gravity.END
                minimumWidth = c.dp(150)
                addView(price, lp(WRAP, WRAP))
                note?.let { addView(Ui.text(c, it, 20, Ui.INK3, 600).apply { maxLines = 1 }, lp(WRAP, WRAP)) }
            })
        })
        below?.let { addView(it, lp(MATCH, WRAP)) }
        onClick?.let { Ui.tap(this, 22, it) }
    }

    /** 줄 아래 한 줄: (있으면) 작은 노란 가격표 + 글. 가격표가 앞에 있어 글이 길어도 잘리지 않는다 */
    fun line(s: String, miniTag: String? = null, color: Int = Ui.INK2): View = Ui.hbox(c, 10).apply {
        miniTag?.let { addView(price(it, 20, true, soft = true)) }
        addView(Ui.text(c, s, Ui.CAP, color).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, lp(0, WRAP, 1f))
    }

    /** 목록 머리: 왼쪽 제목 / 오른쪽 안내 */
    fun listHead(left: String, right: String): View = Ui.hbox(c, 16).apply {
        setPadding(c.dp(24), 0, c.dp(24), c.dp(6))
        addView(Ui.text(c, left, 24, Ui.INK, 700).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, lp(0, WRAP, 1f))
        addView(Ui.text(c, right, Ui.CAP, Ui.INK3))
    }

    /** 세로 스크롤. [key] 가 있으면 다시 그려도 스크롤 위치를 되돌린다 */
    fun scroll(child: View, key: String? = null): ScrollView = ScrollView(c).apply {
        isVerticalScrollBarEnabled = false; isFillViewport = true
        // 아래로 더 있으면 끝을 흐린다
        isVerticalFadingEdgeEnabled = true; setFadingEdgeLength(c.dp(40))
        key?.let { tag = SCROLL + it }
        addView(child)
    }

    /** 머물 시간 고르기: 홈 안에서 고른 칸만 떠 있는 판. 오른쪽 끝은 흐려서 더 있다는 걸 알린다 */
    fun segmented(options: List<Pair<String, Boolean>>, onPick: (Int) -> Unit): View = HorizontalScrollView(c).apply {
        isHorizontalScrollBarEnabled = false
        isHorizontalFadingEdgeEnabled = true; setFadingEdgeLength(c.dp(72))
        tag = SCROLL + "stay"
        background = Ui.rounded(Ui.SUNKEN, 24, c)
        setPadding(c.dp(8), c.dp(8), c.dp(8), c.dp(8)); clipToPadding = true
        addView(Ui.hbox(c, 4).apply {
            options.forEachIndexed { i, (t, on) ->
                addView(Ui.text(c, t, Ui.BODY, if (on) Ui.INK else Ui.INK2, if (on) 800 else 600).apply {
                    gravity = Gravity.CENTER; minimumWidth = c.dp(112); minimumHeight = c.dp(72)
                    setPadding(c.dp(26), 0, c.dp(26), 0)
                    if (on) { background = Ui.rounded(Ui.KNOB, 18, c); elevation = c.dp(2).toFloat() }
                    Ui.tap(this, 18) { onPick(i) }
                })
            }
        })
    }

    /** 요금 계단: 지금 구간만 떠 있는 판 + "지금" */
    fun ladder(rows: List<Triple<String, String, Boolean>>): View = Ui.vbox(c, 4).apply {
        rows.forEach { (k, v, now) ->
            addView(Ui.hbox(c, 14).apply {
                setPadding(c.dp(24), 0, c.dp(24), 0); minimumHeight = c.dp(if (now) 88 else 68)
                if (now) background = Ui.rounded(Ui.SUNKEN, 20, c)
                addView(Ui.text(c, k, if (now) 28 else 25, if (now) Ui.INK else Ui.INK2, if (now) 800 else 400).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, lp(0, WRAP, 1f))
                if (now) addView(Ui.text(c, "지금", 20, Ui.ON_INK, 700).apply {
                    setPadding(c.dp(12), c.dp(4), c.dp(12), c.dp(4)); background = Ui.rounded(Ui.INK, 999, c) })
                addView(Ui.text(c, v, if (now) 32 else 26, if (now) Ui.INK else Ui.INK2, if (now) 800 else 600))
            })
        }
    }

    /**
     * 혜택 가게 한 줄. [radio] 가 null 이면 고정 줄, true/false 면 고르는 줄(고른 줄은 먹 테두리).
     * 혜택으로 내는 돈이라 금액은 노란 가격표.
     */
    fun deal(store: String, cond: String, fee: String, radio: Boolean?, tagged: Boolean = true, soft: Boolean = false, onClick: (() -> Unit)? = null): View = Ui.hbox(c, 18).apply {
        setPadding(c.dp(22), c.dp(14), c.dp(22), c.dp(14)); minimumHeight = c.dp(88)
        background = if (radio == true) Ui.rounded(Ui.SURFACE, 20, c, Ui.INK, 3) else Ui.rounded(Ui.SUNKEN, 20, c)
        radio?.let { on ->
            addView(View(c).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; if (on) { setColor(Ui.SURFACE); setStroke(c.dp(9), Ui.INK) } else setStroke(c.dp(3), Ui.INK3) }
                layoutParams = lp(c.dp(30), c.dp(30))
            })
        }
        addView(Ui.vbox(c, 4).apply {
            addView(Ui.text(c, store, 25, Ui.INK, 700).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
            if (cond.isNotEmpty()) addView(Ui.text(c, cond, Ui.CAP, Ui.INK2).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        }, lp(0, WRAP, 1f))
        addView(price(fee, 28, tagged, soft = soft))
        onClick?.let { Ui.tap(this, 20, it) }
    }

    /** 떠 있는 판 하나 (화면에 하나만 쓴다) */
    fun sheet(v: LinearLayout): LinearLayout = v.apply {
        setPadding(c.dp(36), c.dp(34), c.dp(36), c.dp(36)); background = Ui.rounded(Ui.SURFACE, 32, c)
    }

    /** 세로 칸이 폭을 다 채우지 않게 왼쪽에 붙인다 (가격표·칩·버튼) */
    fun left(v: View): View = FrameLayout(c).apply { addView(v, FrameLayout.LayoutParams(WRAP, WRAP)) }

    fun hairline(): View = View(c).apply { setBackgroundColor(Ui.LINE); layoutParams = lp(MATCH, c.dp(2)) }

    /** 넓은 창은 두 열, 좁은 창은 위아래로 */
    fun twoCol(compact: Boolean, left: View, right: View?, leftWeight: Float = 1f): View =
        if (compact || right == null) Ui.vbox(c, 24).apply {
            addView(left, lp(MATCH, if (right == null) MATCH else WRAP))
            right?.let { addView(it, lp(MATCH, 0, 1f)) }
        } else Ui.hbox(c, 56).apply {
            gravity = Gravity.TOP
            addView(left, lp(0, MATCH, leftWeight))
            addView(right, lp(0, MATCH, 1f))
        }

    fun col(gap: Int = 20, vararg views: View?): LinearLayout = Ui.vbox(c, gap).apply {
        views.filterNotNull().forEach { addView(it, if (it.tag == SPRING) lp(MATCH, 0, 1f) else lp(MATCH, WRAP)) }
    }

    /** 세로 칸 사이를 밀어 내는 빈칸 */
    fun spring(): View = View(c).apply { tag = SPRING; layoutParams = lp(MATCH, 0, 1f) }

    fun pad(v: LinearLayout, compact: Boolean = false): LinearLayout =
        v.apply { val h = c.dp(if (compact) 36 else 56); setPadding(h, c.dp(28), h, c.dp(36)) }

    /** 정차 5분 막대: 1분 = 막대 하나, 남은 만큼 아래에서부터 찬다 */
    fun bars(fills: List<Double>, heightDp: Int, color: Int): View = Ui.hbox(c, 14).apply {
        gravity = Gravity.BOTTOM
        fills.forEach { f ->
            addView(FrameLayout(c).apply {
                background = Ui.rounded(Ui.SUNKEN, 22, c); clipToOutline = true
                if (f > 0) addView(View(c).apply { background = Ui.rounded(color, 22, c) },
                    FrameLayout.LayoutParams(MATCH, (c.dp(heightDp) * f).toInt().coerceAtLeast(c.dp(8)), Gravity.BOTTOM))
            }, lp(0, c.dp(heightDp), 1f))
        }
    }

    companion object {
        private const val SPRING = "spring"
        /** 스크롤 위치를 기억할 뷰의 tag 머리말 */
        const val SCROLL = "scroll:"
    }
}
