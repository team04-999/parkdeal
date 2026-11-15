package com.parkdeal.core

import com.parkdeal.core.api.Benefit
import com.parkdeal.core.api.Lot

/** 이 차의 특성. 주차장 제한(전기차 불가 등)을 거르는 데 쓴다. 지금은 Vehicle SDK 로 전기차 여부만 안다. */
data class Vehicle(val ev: Boolean?) {
    companion object { val UNKNOWN = Vehicle(ev = null) }
}

/** 주차장 한 곳의 주의 배지와, 이 차가 들어갈 수 없는지. */
/** [fullNow]: 실시간으로 지금 남은 자리가 없다. */
data class LotNotes(val badges: List<String>, val blocked: Boolean, val blockReason: String?, val fullNow: Boolean = false) {
    companion object {
        private val LABEL = mapOf(
            "full_often" to "만차 잦음", "mechanical" to "기계식", "narrow" to "협소",
            "suv" to "SUV 제한", "suv_large" to "대형 SUV 불가", "midsize_up" to "중형 이상 불가",
            "small_only" to "소형만", "import" to "수입차 제한", "ev" to "전기차 불가",
        )
        /** 전기차가 들어갈 수 없는 경고. 수집 원문에서 "전기차 불가", "SUV·전기차 주차불가(소형만)" 로 나온다. */
        private val EV_BLOCK = listOf("ev", "small_only")

        fun of(lot: Lot, vehicle: Vehicle): LotNotes {
            val ev = vehicle.ev == true
            val block = if (ev) lot.alerts.firstOrNull { it in EV_BLOCK } else null
            val badges = lot.alerts.mapNotNull { a ->
                when {
                    a == "mechanical" && ev -> "기계식 · 전기차 확인"
                    else -> LABEL[a]
                }
            }.toMutableList()
            // 실시간 빈자리를 맨 앞에: 지금 들어갈 수 있는지가 가장 중요하다
            lot.live?.let { lv -> badges.add(0, if (lv.available > 0) "빈자리 ${lv.available}/${lv.total}" else "지금 만차") }
            if (lot.valet == true) badges += when (val f = lot.valetFee) {
                null -> "발렛"
                0 -> "무료 발렛"
                else -> "발렛 %,d원".format(f)
            }
            lot.heightMm?.let { badges += "높이 %.1fm".format(it / 1000.0) }
            return LotNotes(badges, blocked = block != null, blockReason = block?.let { LABEL[it] }, fullNow = lot.live?.available == 0)
        }
    }
}

/** 혜택을 실제로 받는 법: 어디서 어떻게 등록하는지, 언제·몇 대까지인지. */
data class BenefitHow(val redeem: String?, val limits: List<String>, val needsReview: Boolean) {
    companion object {
        fun of(b: Benefit): BenefitHow {
            val redeem = when (b.redeem) {
                "plate" -> "카운터에 차량번호를 알려 주세요"
                "receipt_stamp" -> "영수증에 도장을 받아 출차할 때 보여 주세요"
                "ticket" -> "매장에서 주차권을 받아 주세요"
                "app" -> "주차 앱으로 등록해요"
                "tablet" -> "매장 태블릿에 차량번호를 입력해요"
                else -> null
            }
            val limits = buildList {
                when (b.validDays) { "weekday" -> add("평일만"); "weekend" -> add("주말만") }
                val from = b.validFrom; val to = b.validTo
                when {
                    from != null && to != null -> add("$from–$to")
                    from != null -> add("$from 이후")
                    to != null -> add("$to 까지")
                }
                b.maxCars?.let { add("${it}대까지") }
            }
            return BenefitHow(redeem, limits, b.needsReview)
        }
    }
}
