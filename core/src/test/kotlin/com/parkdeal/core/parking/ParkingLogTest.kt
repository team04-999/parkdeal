package com.parkdeal.core.parking

import com.parkdeal.core.FeeRule
import com.parkdeal.core.ParkingOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZonedDateTime
import java.time.ZoneId

class ParkingLogTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private val min = 60_000L
    private val t0 = ZonedDateTime.of(2026, 11, 15, 12, 0, 0, 0, seoul).toInstant().toEpochMilli()
    private var now = t0
    private val store = object : StateStore {
        var v: String? = null
        override fun load() = v
        override fun save(json: String) { v = json }
    }
    /** 최초 30분 6,000원, 이후 60분당 6,000원, 2시간 무료 */
    private val option = ParkingOption(1, "아크플레이스", 37.5, 127.03, 0, 6, 120, "필앤필버거 강남본점 이용 시 2시간 무료",
        FeeRule.Metered(30, 6000, 60, 6000, 60000))

    private fun parkFor(minutes: Int, t: ParkingTracker = ParkingTracker(store) { now }): ParkingTracker {
        t.candidate = Candidate(option, "필앤필버거 강남본점", "필앤필버거")
        t.onGear(Gear.DRIVE)
        t.onGear(Gear.PARK) // 주차 시작
        now += minutes * min
        t.onGear(Gear.DRIVE) // 출차
        return t
    }

    @Test fun `등록했다고 닫으면 혜택 적용 요금과 아낀 금액을 기록한다`() {
        val t = parkFor(100)
        t.dismissReminder(registered = true)
        val e = t.state.history.single()
        assertEquals("아크플레이스", e.lotName)
        assertEquals(100, e.minutes)
        assertEquals(0, e.paid)
        assertEquals(18000, e.saved)
        assertNull(t.state.reminder)
    }

    @Test fun `등록하지 못했으면 전체 요금을 내고 아낀 금액은 0`() {
        val t = parkFor(100)
        t.dismissReminder(registered = false)
        assertEquals(18000, t.state.history.single().paid)
        assertEquals(0, t.state.history.single().saved)
    }

    @Test fun `요금 규칙을 모르는 주차는 금액 없이 기록한다`() {
        val t = ParkingTracker(store) { now }
        t.candidate = Candidate(option.copy(rule = null, freeMin = 0), null, "어딘가")
        t.startNow(); now += 30 * min; t.onGear(Gear.DRIVE)
        t.dismissReminder(registered = false)
        assertNull(t.state.history.single().paid)
    }

    @Test fun `이번 달 합계는 같은 달 기록만 더한다`() {
        val t = parkFor(100)
        t.dismissReminder(registered = true)
        now = ZonedDateTime.of(2026, 12, 1, 9, 0, 0, 0, seoul).toInstant().toEpochMilli()
        parkFor(45, t).dismissReminder(registered = false)
        val nov = MonthSummary.of(t.state.history, 2026, 11, seoul)
        assertEquals(1, nov.count)
        assertEquals(0, nov.paid)
        assertEquals(18000, nov.saved)
        val dec = MonthSummary.of(t.state.history, 2026, 12, seoul)
        assertEquals(12000, dec.paid)
    }

    @Test fun `기록은 최근 100건까지만 남긴다`() {
        val t = ParkingTracker(store) { now }
        repeat(105) { parkFor(10, t).dismissReminder(registered = true) }
        assertEquals(100, t.state.history.size)
    }
}
