package com.parkdeal.core.navi

import com.parkdeal.core.FeeRule
import com.parkdeal.core.ParkingOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApproachTest {
    private fun opt(name: String, fee: Int?, walk: Int = 60) =
        ParkingOption(1, name, 37.5, 127.0, fee, walk, 0, "", FeeRule.Flat(1000))

    @Test fun `1km 안으로 들어오면 목적지마다 한 번만 알린다`() {
        val a = ApproachTrigger()
        assertFalse(a.onRemain("필앤필버거", 3_000))
        assertTrue(a.onRemain("필앤필버거", 950))
        assertFalse(a.onRemain("필앤필버거", 400))
        assertTrue(a.onRemain("조이치과", 800)) // 목적지가 바뀌면 다시
    }

    @Test fun `남은 거리 0 은 정보 없음으로 보고 알리지 않는다`() {
        assertFalse(ApproachTrigger().onRemain("필앤필버거", 0))
    }

    @Test fun `곧 도착 - 고른 곳과 낼 돈`() {
        assertEquals("곧 도착해요. 주차는 아크플레이스, 0원이에요.", NaviTalk.approaching(opt("아크플레이스", 0)))
        assertEquals("곧 도착해요. 주차는 테헤란로 146 주차장, 약 3,000원이에요.", NaviTalk.approaching(opt("테헤란로 146 주차장", 3000)))
    }

    @Test fun `경유지 도착 - 자리가 없을 때 다음 후보까지`() {
        assertEquals(
            "아크플레이스에 거의 왔어요. 자리가 없으면 다음은 GFC, 0원이에요.",
            NaviTalk.waypointArrived("아크플레이스", opt("GFC", 0)),
        )
        assertEquals(
            "아크플레이스에 거의 왔어요. 자리가 없으면 다음은 테헤란로 146 주차장, 약 3,000원이에요.",
            NaviTalk.waypointArrived("아크플레이스", opt("테헤란로 146 주차장", 3000)),
        )
        assertEquals("아크플레이스에 거의 왔어요.", NaviTalk.waypointArrived("아크플레이스", null))
        assertEquals("아크플레이스에 거의 왔어요. 자리가 없으면 다음은 B, 요금은 확인이 필요해요.",
            NaviTalk.waypointArrived("아크플레이스", opt("B", null)))
    }
}
