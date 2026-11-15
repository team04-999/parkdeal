package com.parkdeal.core.parking

import com.parkdeal.core.FeeRule
import com.parkdeal.core.ParkingOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkCheckTest {
    private val t0 = 1_700_000_000_000L
    private var now = t0
    private val store = object : StateStore { var v: String? = null; override fun load() = v; override fun save(json: String) { v = json } }

    private fun opt(id: Int, name: String, lat: Double, lng: Double, freeMin: Int = 0) =
        ParkingOption(id, name, lat, lng, 0, 50, freeMin, if (freeMin > 0) "가게 이용 시 무료" else "", FeeRule.Metered(30, 1000, 30, 1000, null))

    private val ark = opt(1, "아크플레이스", 37.49980, 127.03500, freeMin = 120)
    private val gfc = opt(2, "GFC", 37.50050, 127.03700)
    private val far = opt(3, "멀리", 37.51000, 127.05000)

    private fun parkedAtArk() = ParkingTracker(store, settleMs = 0, minSessionMs = 0) { now }.apply {
        candidate = Candidate(ark, "필앤필버거", "필앤필버거"); startNow()
    }

    @Test fun `차가 고른 주차장 150m 안이면 그대로`() {
        val t = parkedAtArk()
        assertNull(t.checkPlace(37.49990, 127.03510))
        assertNull(t.state.session!!.farFrom)
    }

    @Test fun `150m 넘게 떨어져 있으면 거리와 차 위치를 남기고 가까운 후보를 고른다`() {
        val t = parkedAtArk()
        val far1 = t.checkPlace(37.50052, 127.03702)!!
        assertTrue(far1.distanceM in 150..300)
        assertEquals(37.50052, t.state.session!!.farFrom!!.carLat, 1e-9)
        val guesses = ParkCheck.guesses(t.state.session!!, listOf(ark, gfc, far))
        assertEquals("GFC", guesses.first().name)
        assertTrue(guesses.none { it.name == "아크플레이스" })
    }

    @Test fun `다른 주차장으로 고치면 주차 시각은 그대로, 혜택은 그 주차장 기준`() {
        val t = parkedAtArk()
        now += 60_000
        t.checkPlace(37.50052, 127.03702)
        t.relocate(Candidate(gfc, "필앤필버거", "필앤필버거"))
        val s = t.state.session!!
        assertEquals("GFC", s.lotName)
        assertEquals(t0, s.startedAt)
        assertEquals(0, s.freeMin)
        assertNull(s.storeName)
        assertNull(s.farFrom)
    }

    @Test fun `여기 맞아요를 누르면 표시만 지운다`() {
        val t = parkedAtArk()
        t.checkPlace(37.50052, 127.03702)
        t.confirmPlace()
        assertEquals("아크플레이스", t.state.session!!.lotName)
        assertNull(t.state.session!!.farFrom)
    }
}
