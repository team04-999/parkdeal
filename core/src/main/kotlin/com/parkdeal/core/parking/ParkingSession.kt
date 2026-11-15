package com.parkdeal.core.parking

import com.parkdeal.core.ExitFee
import com.parkdeal.core.FeeRule
import com.parkdeal.core.durationText
import kotlinx.serialization.Serializable

/**
 * P단에서 시작해 출차(D/R)로 끝나는 주차 1건.
 * [freeMin] 은 [storeName] 가게 혜택으로 이 주차장에서 무료인 시간(분). 혜택이 없으면 0.
 */
@Serializable
data class ParkingSession(
    val startedAt: Long,
    val lotId: Int,
    val lotName: String,
    val lat: Double?,
    val lng: Double?,
    val rule: FeeRule?,
    val freeMin: Int,
    val storeName: String?,
    /** 추천 이유 한 줄 (예: "조이치과의원 봉천 이용 시 1시간 무료"). */
    val reason: String,
    val destName: String,
    /** 주차장에서 목적지까지 걷는 거리(m). 예전에 저장한 세션에는 없어 0. */
    val walkMeters: Int = 0,
    /** 혜택 등록 방법 한 줄 (예: "카운터에 차량번호를 알려 주세요"). 모르면 null. */
    val redeemHint: String? = null,
    /** 고른 주차장과 차 위치가 떨어져 있으면 그 정보. 운전자가 확인하면 지운다. */
    val farFrom: FarFrom? = null,
) {
    /** 약 4km/h(분당 67m), 최소 1분. */
    val walkMinutes: Int get() = maxOf(1, (walkMeters + 66) / 67)

    val hasBenefit: Boolean get() = storeName != null && freeMin > 0

    fun elapsedMin(now: Long): Int = ((now - startedAt) / 60_000).toInt().coerceAtLeast(0)

    /** 지금 나가면 낼 요금 (혜택 미등록 / 등록 기준). */
    fun fee(now: Long): ExitFee? = rule?.let { ExitFee.of(it, elapsedMin(now), freeMin) }

    /** 출차 시 요금. 주차장은 1분만 있어도 기본 요금을 받는다. */
    fun exitFee(now: Long): ExitFee? = rule?.let { ExitFee.of(it, elapsedMin(now).coerceAtLeast(1), freeMin) }

    fun freeLeftMin(now: Long): Int? = if (hasBenefit) (freeMin - elapsedMin(now)).coerceAtLeast(0) else null

    /** 무료 시간이 [beforeMin]분 남는 시각. 무료 시간이 그보다 짧으면 알리지 않는다. */
    fun freeWarnAt(beforeMin: Int = WARN_BEFORE_MIN): Long? =
        if (hasBenefit && freeMin > beforeMin) startedAt + (freeMin - beforeMin) * 60_000L else null

    companion object {
        const val WARN_BEFORE_MIN = 15
    }
}

/** 출차 직후 남기는 리마인드. 운전자가 닫을 때까지 화면에 남는다. */
@Serializable
data class ExitReminder(val session: ParkingSession, val endedAt: Long) {
    val fee: ExitFee? get() = session.exitFee(endedAt)
}

/** 주차 중·출차 때 음성과 알림으로 들려줄 문장. */
object ParkingTalk {
    /** P단에 세운 직후: 어디 세웠는지, 혜택을 받으려면 뭘 해야 하는지, 얼마나 걷는지. */
    fun parked(s: ParkingSession): String {
        val deal = when {
            !s.hasBenefit -> ""
            s.redeemHint != null -> " ${s.storeName} 이용하고 ${s.redeemHint}."
            else -> " ${s.storeName} 이용하고 주차 등록하세요."
        }
        return "${s.lotName}에 세웠어요.$deal 목적지까지 걸어서 ${s.walkMinutes}분이에요."
    }

    fun status(s: ParkingSession, now: Long): String {
        val head = "${s.lotName}에 주차한 지 ${durationText(s.elapsedMin(now))} 됐어요."
        val fee = s.fee(now) ?: return "$head 이 주차장은 요금 정보가 없어요."
        val left = s.freeLeftMin(now)
        return when {
            left != null && left > 0 ->
                "$head ${s.storeName} 무료 시간이 ${durationText(left)} 남아서 지금 나가면 ${won(fee.withBenefit)}이에요."
            left != null -> "$head 무료 시간이 지나서 혜택을 등록해도 약 ${won(fee.withBenefit)}이에요."
            else -> "$head 지금 나가면 약 ${won(fee.withoutBenefit)}이에요."
        }
    }

    fun freeWarning(s: ParkingSession, now: Long): String {
        val left = durationText(s.freeLeftMin(now) ?: 0)
        val after = when (val r = s.rule) {
            is FeeRule.Metered -> "그 뒤로는 ${durationText(r.unitMin)}마다 ${won(r.unitFee)}이 붙어요."
            else -> "그 뒤로는 요금이 붙어요."
        }
        return "${s.storeName} 무료 주차가 $left 남았어요. $after"
    }

    /** 출차 직후: 혜택 등록을 잊지 않게. */
    fun exitReminder(r: ExitReminder): String {
        val s = r.session
        val fee = r.fee
        return when {
            s.hasBenefit && fee != null && fee.saved > 0 ->
                "${s.storeName}에서 무료 주차 등록하셨나요? 등록하지 않으면 약 ${won(fee.withoutBenefit)}이 나와요."
            s.hasBenefit -> "${s.storeName}에서 무료 주차 등록하셨나요?"
            fee != null -> "${s.lotName} 주차 요금은 약 ${won(fee.withoutBenefit)}이에요."
            else -> "${s.lotName} 주차 요금을 확인하세요."
        }
    }

    private fun won(v: Int) = "%,d원".format(v)
}
