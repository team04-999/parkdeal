package com.parkdeal.core

import com.parkdeal.core.api.Fee

/** 주차 요금 규칙. 서버의 대표 요금([Fee])에서 만든다. */
sealed interface FeeRule {
    /** [minutes] 분 주차했을 때 요금(원). 0분이면 0원. */
    fun feeFor(minutes: Int): Int

    /** 최초 [baseMin] 분 [baseFee] 원, 이후 [unitMin] 분마다 [unitFee] 원(올림), 일 최대 [dailyMax] 원. */
    data class Metered(val baseMin: Int, val baseFee: Int, val unitMin: Int, val unitFee: Int, val dailyMax: Int?) : FeeRule {
        override fun feeFor(minutes: Int): Int {
            if (minutes <= 0) return 0
            val extra = (minutes - baseMin).coerceAtLeast(0)
            val units = (extra + unitMin - 1) / unitMin
            val fee = baseFee + units * unitFee
            return dailyMax?.let { minOf(fee, it) } ?: fee
        }
    }

    /** 시간과 관계없는 정액. */
    data class Flat(val fee: Int) : FeeRule {
        override fun feeFor(minutes: Int): Int = if (minutes <= 0) 0 else fee
    }

    companion object {
        /** 서버 요금을 규칙으로. 서버 스키마상 정액이 있거나, 최초 시간과 단위 시간(> 0)이 있어야 유효하다. */
        fun from(fee: Fee?): FeeRule? {
            fee ?: return null
            fee.flatFee?.let { return Flat(it) }
            val base = fee.firstMin ?: return null
            val unit = fee.unitMin?.takeIf { it > 0 } ?: return null
            return Metered(base, fee.firstFee ?: 0, unit, fee.unitFee ?: 0, fee.dailyMax)
        }
    }
}

/** 출차 시 요금: 혜택 미등록 / 등록 기준. */
data class ExitFee(val minutes: Int, val withoutBenefit: Int, val withBenefit: Int) {
    val saved: Int get() = withoutBenefit - withBenefit

    companion object {
        fun of(rule: FeeRule, minutes: Int, freeMin: Int) = ExitFee(
            minutes,
            rule.feeFor(minutes),
            (rule.feeFor(minutes) - rule.feeFor(minOf(freeMin, minutes))).coerceAtLeast(0),
        )
    }
}
