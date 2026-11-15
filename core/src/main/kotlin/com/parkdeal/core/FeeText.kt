package com.parkdeal.core

import kotlin.math.ceil

/** 요금 규칙을 사람이 읽는 말로: 한 줄 규칙, 출차 계산식, 단계표, 다음에 오르는 시점. */
object FeeText {
    fun rule(r: FeeRule): String = when (r) {
        is FeeRule.Flat -> if (r.fee == 0) "무료" else "정액 ${won(r.fee)}"
        is FeeRule.Metered -> {
            val head = when {
                r.baseFee == 0 && r.baseMin > 0 -> "${durationText(r.baseMin)} 무료 · 이후 ${durationText(r.unitMin)}당 ${won(r.unitFee)}"
                r.baseMin == r.unitMin && r.baseFee == r.unitFee -> "${durationText(r.unitMin)}당 ${won(r.unitFee)}"
                else -> "최초 ${durationText(r.baseMin)} ${won(r.baseFee)} · 이후 ${durationText(r.unitMin)}당 ${won(r.unitFee)}"
            }
            head + (r.dailyMax?.let { " · 하루 최대 ${won(it)}" } ?: "")
        }
    }

    /** [minutes] 분 주차했을 때 혜택 없이 낼 돈의 계산식. 예: "6,000원 + 6,000원 × 2 = 18,000원" */
    fun formula(r: FeeRule, minutes: Int): String = when (r) {
        is FeeRule.Flat -> "정액 ${won(r.fee)}"
        is FeeRule.Metered -> {
            val m = minutes.coerceAtLeast(1)
            val (text, raw) = if (r.baseMin == r.unitMin && r.baseFee == r.unitFee) {
                val n = ceil(m / r.unitMin.toDouble()).toInt()
                val unit = if (r.unitMin == 60) "${n}시간(올림)" else "$n(${durationText(r.unitMin)} 단위 올림)"
                "${won(r.unitFee)} × $unit" to n * r.unitFee
            } else {
                val units = if (m > r.baseMin) ceil((m - r.baseMin) / r.unitMin.toDouble()).toInt() else 0
                val first = if (r.baseFee == 0) "${durationText(r.baseMin)} 무료" else won(r.baseFee)
                val t = if (units == 0) first else "$first + ${won(r.unitFee)} × $units"
                t to r.baseFee + units * r.unitFee
            }
            val cap = r.dailyMax?.takeIf { raw > it }
            "$text = ${won(raw)}" + (cap?.let { " → 하루 최대 ${won(it)}" } ?: "")
        }
    }

    data class Step(val label: String, val fee: Int, val now: Boolean)

    /** 요금 단계표(혜택 없이 낼 돈). 지금 구간을 포함해 4줄, 하루 최대가 있으면 마지막 줄. */
    fun ladder(r: FeeRule, elapsedMin: Int): List<Step> {
        val e = elapsedMin.coerceAtLeast(1)
        if (r is FeeRule.Flat) return listOf(Step("주차 1회", r.fee, true))
        r as FeeRule.Metered
        val bps = mutableListOf<Int>()
        var bp = r.baseMin.coerceAtLeast(1)
        while (bps.size < 300) {
            bps += bp
            val capped = r.dailyMax != null && r.feeFor(bp) >= r.dailyMax
            if (capped || (r.dailyMax == null && bp >= e + 3 * r.unitMin && bps.size >= 4)) break
            bp += r.unitMin
        }
        val rows = bps.map { Step("${durationText(it)}까지", r.feeFor(it), false) }.toMutableList()
        if (r.dailyMax != null) rows += Step("${durationText(bps.last())} 넘게 (하루 최대)", r.dailyMax, false)
        val nowIdx = bps.indexOfFirst { e <= it }.let { if (it < 0) rows.lastIndex else it }
        rows[nowIdx] = rows[nowIdx].copy(now = true)
        val start = (nowIdx - 1).coerceIn(0, (rows.size - 4).coerceAtLeast(0))
        return rows.drop(start).take(4)
    }

    data class Next(val atMin: Int, val fee: Int, val dailyMax: Boolean)

    /** 지금([elapsedMin])보다 낼 돈이 처음 오르는 시점. [freeMin] 은 등록한 혜택의 무료 시간. 더 오르지 않으면 null. */
    fun nextStep(r: FeeRule, elapsedMin: Int, freeMin: Int = 0): Next? {
        fun f(m: Int) = (r.feeFor(m) - r.feeFor(minOf(freeMin, m))).coerceAtLeast(0)
        val e = elapsedMin.coerceAtLeast(0)
        val nowFee = f(e)
        for (t in e..e + 24 * 60) {
            val next = f(t + 1)
            if (next > nowFee) return Next(t, next, (r as? FeeRule.Metered)?.dailyMax == r.feeFor(t + 1))
        }
        return null
    }

    fun won(v: Int) = "%,d원".format(v)
}
