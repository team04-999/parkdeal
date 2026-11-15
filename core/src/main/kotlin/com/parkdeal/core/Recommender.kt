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
    /** 주의 배지(만차 잦음·기계식·발렛 등). 이 차가 못 들어가면 첫 배지가 그 이유. */
    val notes: List<String> = emptyList(),
    /** 이 차(전기차 등)는 들어갈 수 없다. 목록 맨 뒤로 간다. */
    val blocked: Boolean = false,
    /** 가게 혜택을 실제로 받는 법. 혜택이 없으면 null. */
    val how: BenefitHow? = null,
    /** 목적지 가게가 아닌 다른 가게 혜택으로 더 싸지는 경우 (PRD "혜택 가게 바꾸기"). */
    val other: OtherDeal? = null,
    /** 실시간으로 지금 만차. 순위를 뒤로 미룬다. */
    val fullNow: Boolean = false,
    /** 혜택 없이 [stayMin] 분 머물 때 낼 돈. 요금 규칙을 모르면 null. */
    val normalFee: Int? = null,
    /** 이 주차장에서 쓸 수 있는 혜택 가게 전부: 목적지 가게 혜택이 있으면 그것이 먼저, 그다음 싼 순 (PRD "다른 혜택 가게로 바꾸기") */
    val deals: List<OtherDeal> = emptyList(),
) {
    /** 약 4km/h(분당 67m), 최소 1분. */
    val walkMinutes: Int get() = maxOf(1, ceil(walkMeters / 67.0).toInt())
}

/** 다른 가게 혜택을 쓰면: [reason] 한 줄과 그때 낼 돈. */
data class OtherDeal(val storeName: String, val reason: String, val fee: Int, val how: BenefitHow, val freeMin: Int) {
    /** 이 혜택을 쓰기로 하고 주차할 때의 선택지: 무료 시간·이유·받는 법을 이 가게 것으로 바꾼다. */
    fun applyTo(o: ParkingOption) = o.copy(fee = fee, freeMin = freeMin, reason = reason, how = how, other = null)
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
    /** 다른 가게를 따로 이용해야 받는 혜택: 그만큼의 부담을 더해 비교한다. */
    private const val OTHER_STORE_COST = 2_000
    /** 지금 만차인 곳: 자리를 찾아 돌 시간을 돈으로 쳐서 뒤로 보낸다 */
    private const val FULL_PENALTY = 10_000

    /** 정렬에 쓰는 낼 돈. 일반 요금을 모르면 다른 가게 혜택을 쓸 때의 돈(+부담), 그것도 없으면 맨 뒤. */
    private fun costOf(o: ParkingOption): Int = o.fee ?: o.other?.let { it.fee + OTHER_STORE_COST } ?: UNKNOWN_FEE

    fun recommend(dest: DestinationResponse, lots: LotsResponse, stayMin: Int, vehicle: Vehicle = Vehicle.UNKNOWN): Recommendation {
        val storeName = dest.store?.name
        val byLot = LinkedHashMap<Int, ParkingOption>()

        // 가게 혜택 주차장을 먼저 넣는다 — 같은 주차장이 주변 목록에도 있으면 혜택 쪽을 남긴다
        for (b in dest.benefits) {
            val lot = b.lot ?: continue
            val option = option(lot, stayMin, BenefitTerms.of(b, storeName), vehicle).copy(how = BenefitHow.of(b))
            val prev = byLot[lot.id]
            if (prev == null || prev.freeMin < option.freeMin) byLot[lot.id] = option
        }
        for (lot in lots.lots) byLot.putIfAbsent(lot.id, option(lot, stayMin, BenefitTerms.NONE, vehicle).copy(other = otherDeal(lot, stayMin)))
        // 혜택 가게 전체 목록: 목적지 가게(있으면) + 같은 주차장 다른 가게
        val lotById = (lots.lots + dest.benefits.mapNotNull { it.lot }).associateBy { it.id }
        for ((id, o) in byLot.toMap()) {
            val own = if (o.freeMin > 0 || (o.reason.isNotEmpty() && o.fee == 0)) storeName?.let { OtherDeal(it, o.reason, o.fee ?: 0, o.how ?: BenefitHow(null, emptyList(), false), o.freeMin) } else null
            val others = lotById[id]?.let { allDeals(it, stayMin) }.orEmpty().filter { it.storeName != storeName }
            byLot[id] = o.copy(deals = listOfNotNull(own) + others)
        }

        // 이 차가 못 들어가는 곳은 아무리 싸도 맨 뒤
        val sorted = byLot.values.sortedWith(compareBy({ it.blocked }, { costOf(it) + it.walkMeters * WON_PER_WALK_METER + if (it.fullNow) FULL_PENALTY else 0 }))
            // 수집 데이터에 같은 이름으로 다른 주차장이 들어 있는 경우가 있다. 운전자에게는 같은 곳으로 보이니 더 나은 하나만 남긴다
            .distinctBy { it.name.replace(" ", "") }
        return Recommendation(storeName, stayMin, sorted)
    }

    /** 이 주차장의 다른 가게 혜택 중 [stayMin] 머물 때 가장 싸지는 것. 일반 요금보다 싸지지 않으면 null. */
    private fun otherDeal(lot: Lot, stayMin: Int): OtherDeal? {
        val normal = FeeRule.from(lot.fee)?.feeFor(stayMin)
        return allDeals(lot, stayMin).filter { normal == null || it.fee < normal }.minByOrNull { it.fee }
    }

    /** 이 주차장의 다른 가게 혜택 전부, 싼 순. */
    private fun allDeals(lot: Lot, stayMin: Int): List<OtherDeal> {
        val rule = FeeRule.from(lot.fee)
        return lot.deals.mapNotNull { d ->
            val terms = BenefitTerms.of(d.asBenefit(), d.storeName)
            val fee = when {
                terms.allFree -> 0
                rule == null || terms.freeMin == 0 -> return@mapNotNull null
                else -> (rule.feeFor(stayMin) - rule.feeFor(minOf(terms.freeMin, stayMin))).coerceAtLeast(0)
            }
            // 전부 무료는 하루로 본다 (출차 요금 계산이 0원이 되게)
            OtherDeal(d.storeName, terms.reason, fee, BenefitHow.of(d.asBenefit()), if (terms.allFree) 24 * 60 else terms.freeMin)
        }.sortedBy { it.fee }
    }

    private fun option(lot: Lot, stayMin: Int, terms: BenefitTerms, vehicle: Vehicle): ParkingOption {
        val notes = LotNotes.of(lot, vehicle)
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
            notes = listOfNotNull(notes.blockReason) + notes.badges.filter { it != notes.blockReason },
            blocked = notes.blocked,
            normalFee = rule?.feeFor(stayMin),
            fullNow = notes.fullNow,
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
