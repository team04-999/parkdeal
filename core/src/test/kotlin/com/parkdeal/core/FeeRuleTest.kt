package com.parkdeal.core

import com.parkdeal.core.api.Fee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeeRuleTest {
    private fun fee(first: Int?, firstFee: Int?, unit: Int?, unitFee: Int?, max: Int? = null, flat: Int? = null) =
        Fee(first, firstFee, unit, unitFee, max, flat, "store_fee", "2026-11-11T00:00:00Z")

    @Test fun `공영주차장 5분당 250원은 2시간에 6000원`() {
        assertEquals(6000, FeeRule.Metered(5, 250, 5, 250, null).feeFor(120))
    }

    @Test fun `기본 시간 이내면 기본 요금만`() {
        assertEquals(3000, FeeRule.Metered(60, 3000, 60, 3000, null).feeFor(45))
    }

    @Test fun `추가 시간은 단위 시간 올림으로 계산`() {
        assertEquals(11000, FeeRule.Metered(10, 0, 10, 1000, 30000).feeFor(120))
        assertEquals(6000, FeeRule.Metered(60, 3000, 60, 3000, null).feeFor(61))
    }

    @Test fun `일 최대 요금을 넘지 않는다`() {
        assertEquals(15000, FeeRule.Metered(30, 0, 10, 1100, 15000).feeFor(600))
    }

    @Test fun `0분이면 0원`() {
        assertEquals(0, FeeRule.Metered(5, 250, 5, 250, null).feeFor(0))
        assertEquals(0, FeeRule.Flat(10000).feeFor(0))
    }

    @Test fun `정액은 시간과 관계없이 같은 요금`() {
        assertEquals(10000, FeeRule.Flat(10000).feeFor(30))
        assertEquals(10000, FeeRule.Flat(10000).feeFor(600))
    }

    @Test fun `서버 요금을 규칙으로 바꾼다`() {
        assertEquals(FeeRule.Metered(60, 0, 30, 2000, null), FeeRule.from(fee(60, 0, 30, 2000)))
        assertEquals(FeeRule.Flat(5000), FeeRule.from(fee(null, null, null, null, flat = 5000)))
        // 최초 요금이 비면 0원으로 본다 (서버 스키마: first_min 과 unit_min 이 있으면 유효한 규칙)
        assertEquals(FeeRule.Metered(30, 0, 10, 1000, null), FeeRule.from(fee(30, null, 10, 1000)))
    }

    @Test fun `규칙을 만들 수 없으면 null`() {
        assertNull(FeeRule.from(null))
        assertNull(FeeRule.from(fee(30, 1000, null, null)))
        assertNull(FeeRule.from(fee(30, 1000, 0, 1000)))
    }
}

class ExitFeeTest {
    @Test fun `2시간10분, 2시간 무료면 미등록 13000 등록 1000`() {
        val f = ExitFee.of(FeeRule.Metered(10, 1000, 10, 1000, 30000), 130, 120)
        assertEquals(13000, f.withoutBenefit)
        assertEquals(1000, f.withBenefit)
        assertEquals(12000, f.saved)
    }

    @Test fun `무료 시간 안이면 등록 시 0원`() {
        assertEquals(0, ExitFee.of(FeeRule.Metered(60, 0, 30, 2000, null), 50, 60).withBenefit)
    }
}
