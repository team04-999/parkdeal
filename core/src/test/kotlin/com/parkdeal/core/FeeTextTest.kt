package com.parkdeal.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeeTextTest {
    private val hourly = FeeRule.Metered(60, 1000, 60, 1000, 3000)      // 1시간당 1,000원 · 하루 최대 3,000원
    private val ark = FeeRule.Metered(30, 6000, 60, 6000, 60000)        // 최초 30분 6,000원 · 이후 1시간당 6,000원
    private val firstFree = FeeRule.Metered(60, 0, 10, 1000, 10000)     // 1시간 무료 · 이후 10분당 1,000원

    @Test fun `요금 규칙 한 줄`() {
        assertEquals("1시간당 1,000원 · 하루 최대 3,000원", FeeText.rule(hourly))
        assertEquals("최초 30분 6,000원 · 이후 1시간당 6,000원 · 하루 최대 60,000원", FeeText.rule(ark))
        assertEquals("1시간 무료 · 이후 10분당 1,000원 · 하루 최대 10,000원", FeeText.rule(firstFree))
        assertEquals("무료", FeeText.rule(FeeRule.Flat(0)))
        assertEquals("정액 5,000원", FeeText.rule(FeeRule.Flat(5000)))
    }

    @Test fun `출차 계산식 - 같은 단위면 곱셈 하나`() {
        assertEquals("1,000원 × 3시간(올림) = 3,000원", FeeText.formula(hourly, 130))
    }

    @Test fun `출차 계산식 - 최초와 이후가 다르면 더하기`() {
        assertEquals("6,000원 + 6,000원 × 2 = 18,000원", FeeText.formula(ark, 100))
        assertEquals("6,000원 = 6,000원", FeeText.formula(ark, 20))
    }

    @Test fun `출차 계산식 - 하루 최대에 걸리면 그렇게 말한다`() {
        assertEquals("1,000원 × 5시간(올림) = 5,000원 → 하루 최대 3,000원", FeeText.formula(hourly, 290))
    }

    @Test fun `요금 단계표 - 지금 구간을 표시하고 하루 최대를 끝에`() {
        val rows = FeeText.ladder(hourly, elapsedMin = 80)
        assertEquals(listOf("1시간까지", "2시간까지", "3시간까지", "3시간 넘게 (하루 최대)"), rows.map { it.label })
        assertEquals(listOf(1000, 2000, 3000, 3000), rows.map { it.fee })
        assertEquals(1, rows.indexOfFirst { it.now })
    }

    @Test fun `요금 단계표 - 지금 구간을 중심으로 4줄`() {
        val rows = FeeText.ladder(firstFree, elapsedMin = 95)
        assertEquals("1시간 40분까지", rows.first { it.now }.label)
        assertEquals(4, rows.size)
    }

    @Test fun `다음에 요금이 오르는 시점`() {
        val n = FeeText.nextStep(hourly, elapsedMin = 80)!!
        assertEquals(120, n.atMin)
        assertEquals(3000, n.fee)
        assertEquals(true, n.dailyMax)
        assertNull(FeeText.nextStep(hourly, elapsedMin = 200))
    }
}
