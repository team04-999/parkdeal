package com.parkdeal.core.parking

import com.parkdeal.core.FeeRule
import com.parkdeal.core.ParkingOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkingTrackerTest {
    private val min = 60_000L
    private val t0 = 1_700_000_000_000L
    private var now = t0
    private val store = MemoryStore()

    private val option = ParkingOption(
        lotId = 6624, name = "남부순환로 1738 주차장", lat = 37.48, lng = 126.94, fee = 0, walkMeters = 60,
        freeMin = 60, reason = "조이치과의원 봉천 이용 시 1시간 무료", rule = FeeRule.Metered(30, 2000, 10, 1000, null),
    )
    private val candidate = Candidate(option, storeName = "조이치과의원 봉천", destName = "조이치과의원 봉천")

    private fun tracker() = ParkingTracker(store) { now }

    @Test fun `첫 기어 값은 기준으로만 쓴다 - 원래 P 였던 것을 주차 시작으로 보지 않는다`() {
        val t = tracker().apply { this.candidate = this@ParkingTrackerTest.candidate }
        assertNull(t.onGear(Gear.PARK))
        assertNull(t.state.session)
    }

    @Test fun `D 에서 P 로 바꾸면 후보 주차장으로 주차를 시작한다`() {
        val t = tracker().apply { this.candidate = this@ParkingTrackerTest.candidate }
        t.onGear(Gear.DRIVE)
        val e = t.onGear(Gear.PARK) as ParkingEvent.Started
        assertEquals(6624, e.session.lotId)
        assertEquals(60, e.session.freeMin)
        assertEquals("조이치과의원 봉천", e.session.storeName)
        assertEquals(t0, e.session.startedAt)
    }

    @Test fun `후보가 없으면 P 로 바꿔도 주차를 시작하지 않는다`() {
        val t = tracker()
        t.onGear(Gear.DRIVE)
        assertNull(t.onGear(Gear.PARK))
    }

    @Test fun `주차 중 D 로 바꾸면 출차 리마인드를 남긴다`() {
        val t = tracker().apply { this.candidate = this@ParkingTrackerTest.candidate }
        t.onGear(Gear.DRIVE); t.onGear(Gear.PARK)
        now = t0 + 90 * min
        val e = t.onGear(Gear.DRIVE) as ParkingEvent.Ended
        assertEquals(90, e.reminder.fee!!.minutes)
        assertNull(t.state.session)
        assertEquals(e.reminder, t.state.reminder)
    }

    @Test fun `P 에서 N 을 거쳐 D 로 가도 출차로 본다`() {
        val t = tracker().apply { this.candidate = this@ParkingTrackerTest.candidate }
        t.onGear(Gear.DRIVE); t.onGear(Gear.PARK)
        assertNull(t.onGear(Gear.NEUTRAL))
        assertTrue(t.onGear(Gear.DRIVE) is ParkingEvent.Ended)
    }

    @Test fun `출차 뒤에는 후보를 비워 다음 P 에서 같은 주차장으로 다시 시작하지 않는다`() {
        val t = tracker().apply { this.candidate = this@ParkingTrackerTest.candidate }
        t.onGear(Gear.DRIVE); t.onGear(Gear.PARK); t.onGear(Gear.DRIVE)
        assertNull(t.candidate)
        assertNull(t.onGear(Gear.PARK))
    }

    @Test fun `같은 기어가 반복해서 와도 한 번만 처리한다`() {
        val t = tracker().apply { this.candidate = this@ParkingTrackerTest.candidate }
        t.onGear(Gear.DRIVE)
        val first = t.onGear(Gear.PARK)
        assertTrue(first is ParkingEvent.Started)
        assertNull(t.onGear(Gear.PARK))
    }

    @Test fun `주차 상태는 저장소에 남아 새 트래커가 이어받는다`() {
        val t = tracker().apply { this.candidate = this@ParkingTrackerTest.candidate }
        t.onGear(Gear.DRIVE); t.onGear(Gear.PARK)
        val again = tracker()
        assertEquals(t.state.session, again.state.session)
        // 이어받은 뒤 첫 기어가 D 면 출차다 (프로세스가 죽은 사이 출발한 경우)
        now = t0 + 30 * min
        assertTrue(again.onGear(Gear.DRIVE) is ParkingEvent.Ended)
    }

    @Test fun `깨진 저장값은 버리고 빈 상태로 시작한다`() {
        store.value = "{not json"
        assertEquals(ParkingState(), tracker().state)
    }

    @Test fun `직접 시작하면 기어와 관계없이 후보로 주차를 시작한다`() {
        val t = tracker().apply { this.candidate = this@ParkingTrackerTest.candidate }
        val s = t.startNow()!!
        assertEquals(6624, s.lotId)
        assertNull(t.startNow()) // 이미 주차 중
    }

    @Test fun `리마인드를 닫으면 저장소에서도 지운다`() {
        val t = tracker().apply { this.candidate = this@ParkingTrackerTest.candidate }
        t.onGear(Gear.DRIVE); t.onGear(Gear.PARK); t.onGear(Gear.DRIVE)
        t.dismissReminder()
        assertEquals(ParkingState(), tracker().state)
    }

    @Test fun `상태가 바뀔 때마다 알린다`() {
        val seen = mutableListOf<ParkingState>()
        val t = tracker().apply { this.candidate = this@ParkingTrackerTest.candidate; onChange = { seen += it } }
        t.onGear(Gear.DRIVE); t.onGear(Gear.PARK); t.onGear(Gear.DRIVE)
        assertEquals(2, seen.size)
        assertEquals(t.state, seen.last())
    }

    private class MemoryStore : StateStore {
        var value: String? = null
        override fun load() = value
        override fun save(json: String) { value = json }
    }
}
