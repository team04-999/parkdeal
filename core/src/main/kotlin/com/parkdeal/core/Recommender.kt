package com.parkdeal.core

import com.parkdeal.core.api.Benefit
import com.parkdeal.core.api.DestinationResponse
import com.parkdeal.core.api.Lot
import com.parkdeal.core.api.LotsResponse
import kotlin.math.ceil

/** 추천 선택지 하나. */
data class ParkingOption(
    val lotId: Int,
    val name: String,
    val lat: Double?,
    val lng: Double?,
    /** [stayMin] 분 머물 때 실제로 낼 돈(원). 요금 규칙을 모르면 null. */
    val fee: Int?,
    val walkMeters: Int,
    /** 이 주차장에 적용되는 가게 무료 시간(분). 0 이면 혜택 없음. */
    val freeMin: Int,
    /** 추천 이유 한 줄. 혜택이 없으면 빈 문자열. */
    val reason: String,
    val rule: FeeRule?,
) {
    /** 약 4km/h(분당 67m), 최소 1분. */
    val walkMinutes: Int get() = maxOf(1, ceil(walkMeters / 67.0).toInt())
}

data class Recommendation(val storeName: String?, val stayMin: Int, val options: List<ParkingOption>)

/**
 * 목적지 주변 주차 선택지를 **실제 낼 돈 + 걷는 거리** 기준으로 정렬한다.
 * 목적지 가게의 주차 혜택(이용 시 N시간 무료 등)이 연결된 주차장에는 그 무료 시간을 요금에 반영한다.
 */
object Recommender {
    /** 걷는 1m 를 몇 원의 불편으로 보는가. 100m 더 걷는 것 ≈ 500원. */
    private const val WON_PER_WALK_METER = 5
    private const val UNKNOWN_FEE = 1_000_000

    fun recommend(dest: DestinationResponse, lots: LotsResponse, stayMin: Int): Recommendation {
        val storeName = dest.store?.name
        val byLot = LinkedHashMap<Int, ParkingOption>()

        // 가게 혜택 주차장을 먼저 넣는다 — 같은 주차장이 주변 목록에도 있으면 혜택 쪽을 남긴다
        for (b in dest.benefits) {
            val lot = b.lot ?: continue
            val option = option(lot, stayMin, BenefitTerms.of(b, storeName))
            val prev = byLot[lot.id]
            if (prev == null || prev.freeMin < option.freeMin) byLot[lot.id] = option
        }
        for (lot in lots.lots) byLot.putIfAbsent(lot.id, option(lot, stayMin, BenefitTerms.NONE))

        val sorted = byLot.values.sortedBy { (it.fee ?: UNKNOWN_FEE) + it.walkMeters * WON_PER_WALK_METER }
        return Recommendation(storeName, stayMin, sorted)
    }

    private fun option(lot: Lot, stayMin: Int, terms: BenefitTerms): ParkingOption {
        val rule = FeeRule.from(lot.fee)
        val fee = when {
            terms.allFree -> 0
            rule == null -> null
            else -> (rule.feeFor(stayMin) - rule.feeFor(minOf(terms.freeMin, stayMin))).coerceAtLeast(0)
        }
        return ParkingOption(
            lotId = lot.id, name = lot.name, lat = lot.lat, lng = lot.lng, fee = fee,
            // 직선거리 × 1.2 로 도보 거리를 어림한다
            walkMeters = ((lot.distanceM ?: 0) * 1.2).toInt(),
            freeMin = terms.freeMin, reason = terms.reason, rule = rule,
        )
    }
}

/** 혜택 하나를 "몇 분 무료, 어떤 조건"으로 해석한 것. */
internal data class BenefitTerms(val freeMin: Int, val allFree: Boolean, val reason: String) {
    companion object {
        val NONE = BenefitTerms(0, false, "")

        fun of(b: Benefit, storeName: String?): BenefitTerms {
            val who = storeName ?: "가게"
            return when (b.type) {
                "free" -> BenefitTerms(0, true, "$who 이용 시 무료")
                "all_free_min", "conditional" -> {
                    val free = b.freeMin
                    val tier = b.tiers.firstOrNull { it.freeMin != null }
                    when {
                        free != null -> BenefitTerms(free, false, "$who ${action(b.condition)} 시 ${durationText(free)} 무료")
                        tier != null -> BenefitTerms(
                            tier.freeMin!!, false,
                            "$who ${won(tier.minSpend)} 이상 ${action(b.condition)} 시 ${durationText(tier.freeMin)} 무료",
                        )
                        else -> BenefitTerms(0, false, "$who 이용 시 주차 지원")
                    }
                }
                // available·guide·paid·paid_support: 무료 시간이 정해지지 않았거나 없다
                else -> BenefitTerms(0, false, "$who 안내 주차장")
            }
        }

        private fun action(condition: String?) = when (condition) {
            "purchase" -> "구매"
            "meal" -> "식사"
            "treatment" -> "진료"
            "first_visit" -> "첫 방문"
            "visit" -> "방문"
            else -> "이용"
        }

        private fun won(amount: Int) = if (amount % 10_000 == 0) "${amount / 10_000}만원" else "%,d원".format(amount)
    }
}
