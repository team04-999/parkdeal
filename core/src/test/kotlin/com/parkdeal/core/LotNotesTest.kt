package com.parkdeal.core

import com.parkdeal.core.api.Benefit
import com.parkdeal.core.api.Lot
import com.parkdeal.core.api.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LotNotesTest {
    private fun lot(alerts: List<String> = emptyList(), valet: Boolean? = null, valetFee: Int? = null, heightMm: Int? = null) =
        Lot(id = 1, name = "A", kind = "building", alerts = alerts, valet = valet, valetFee = valetFee, heightMm = heightMm)

    @Test fun `경고 코드를 짧은 한국어 배지로 바꾼다`() {
        val n = LotNotes.of(lot(listOf("full_often", "mechanical", "narrow")), Vehicle.UNKNOWN)
        assertEquals(listOf("만차 잦음", "기계식", "협소"), n.badges)
        assertFalse(n.blocked)
    }

    @Test fun `전기차는 전기차 불가·소형만 주차장에 들어갈 수 없다`() {
        assertTrue(LotNotes.of(lot(listOf("ev")), Vehicle(ev = true)).blocked)
        assertEquals("전기차 불가", LotNotes.of(lot(listOf("ev")), Vehicle(ev = true)).blockReason)
        assertTrue(LotNotes.of(lot(listOf("small_only")), Vehicle(ev = true)).blocked)
        assertFalse(LotNotes.of(lot(listOf("ev")), Vehicle(ev = false)).blocked)
    }

    @Test fun `전기차가 기계식에 가면 확인이 필요하다고 알린다`() {
        assertEquals(listOf("기계식 · 전기차 확인"), LotNotes.of(lot(listOf("mechanical")), Vehicle(ev = true)).badges)
    }

    @Test fun `발렛과 높이 제한`() {
        assertEquals(listOf("발렛 3,000원", "높이 1.9m"), LotNotes.of(lot(valet = true, valetFee = 3000, heightMm = 1900), Vehicle.UNKNOWN).badges)
        assertEquals(listOf("무료 발렛"), LotNotes.of(lot(valet = true, valetFee = 0), Vehicle.UNKNOWN).badges)
        assertEquals(listOf("발렛"), LotNotes.of(lot(valet = true), Vehicle.UNKNOWN).badges)
    }

    private fun benefit(redeem: String = "unknown", maxCars: Int? = null, days: String? = null, from: String? = null, to: String? = null,
                        review: Boolean = false, tiers: List<Tier> = emptyList()) =
        Benefit(id = 1, type = "conditional", redeem = redeem, maxCars = maxCars, validDays = days, validFrom = from, validTo = to,
            needsReview = review, tiers = tiers)

    @Test fun `혜택 받는 법 - 등록 방법`() {
        assertEquals("카운터에 차량번호를 알려 주세요", BenefitHow.of(benefit("plate")).redeem)
        assertEquals("영수증에 도장을 받아 출차할 때 보여 주세요", BenefitHow.of(benefit("receipt_stamp")).redeem)
        assertEquals("매장에서 주차권을 받아 주세요", BenefitHow.of(benefit("ticket")).redeem)
        assertEquals("주차 앱으로 등록해요", BenefitHow.of(benefit("app")).redeem)
        assertEquals("매장 태블릿에 차량번호를 입력해요", BenefitHow.of(benefit("tablet")).redeem)
        assertNull(BenefitHow.of(benefit("unknown")).redeem)
    }

    @Test fun `혜택 조건 - 요일·시간·대수·확인 필요`() {
        assertEquals(listOf("평일만", "18:00 이후", "1대까지"),
            BenefitHow.of(benefit(maxCars = 1, days = "weekday", from = "18:00")).limits)
        assertEquals(listOf("주말만", "11:00–22:00"), BenefitHow.of(benefit(days = "weekend", from = "11:00", to = "22:00")).limits)
        assertTrue(BenefitHow.of(benefit(review = true)).needsReview)
    }
}
