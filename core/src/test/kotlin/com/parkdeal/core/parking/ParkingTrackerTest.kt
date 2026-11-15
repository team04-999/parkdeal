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

    /** 기존 시나리오 테스트는 시간 흐름을 보지 않으므로 흔들림 거르기를 끈다. */
    private fun tracker() = ParkingTracker(store, settleMs = 0, minSessionMs = 0) { now }
    private fun realTracker() = ParkingTracker(store) { now }

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
        assertEquals(60, e.session.walkMeters)
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
        assertNull(tracker().state.reminder)
    }

    @Test fun `상태가 바뀔 때마다 알린다`() {
        val seen = mutableListOf<ParkingState>()
        val t = tracker().apply { this.candidate = this@ParkingTrackerTest.candidate; onChange = { seen += it } }
        t.onGear(Gear.DRIVE); t.onGear(Gear.PARK); t.onGear(Gear.DRIVE)
        assertEquals(2, seen.size)
        assertEquals(t.state, seen.last())
    }

    @Test fun `시연용 시간 건너뛰기는 주차 시작 시각만 앞당긴다`() {
        val t = tracker().apply { this.candidate = this@ParkingTrackerTest.candidate }
        assertNull(t.backdate(90)) // 주차 중이 아니면 아무것도 안 한다
        t.startNow()
        assertEquals(t0 - 90 * min, t.backdate(90)!!.startedAt)
        assertEquals(90, t.state.session!!.elapsedMin(now))
    }

    @Test fun `시간 건너뛰기는 1분에서 하루 사이만 받는다`() {
        val t = tracker().apply { this.candidate = this@ParkingTrackerTest.candidate }
        t.startNow()
        assertNull(t.backdate(0))
        assertNull(t.backdate(-30))
        assertNull(t.backdate(24 * 60 + 1))
        assertEquals(t0, t.state.session!!.startedAt)
    }

    @Test fun `시작 직후 2초 안에 몰려 온 기어 값은 기준으로만 쓴다`() {
        val t = realTracker().apply { this.candidate = this@ParkingTrackerTest.candidate }
        // 실제 에뮬레이터: 리스너 등록 직후 D, P(이전 값), D 가 0.1초 안에 들어왔다
        assertNull(t.onGear(Gear.DRIVE)); now += 15
        assertNull(t.onGear(Gear.PARK)); now += 60
        assertNull(t.onGear(Gear.DRIVE))
        assertNull(t.state.session)
        assertNull(t.state.reminder)
        // 2초가 지나면 정상으로 받는다. 기준은 마지막 값 D
        now += 3_000
        assertTrue(t.onGear(Gear.PARK) is ParkingEvent.Started)
    }

    @Test fun `10초 안에 끝난 주차는 잘못 감지한 것으로 보고 리마인드 없이 버린다`() {
        val t = realTracker().apply { this.candidate = this@ParkingTrackerTest.candidate }
        t.onGear(Gear.DRIVE); now += 3_000
        t.onGear(Gear.PARK); now += 2_000
        assertNull(t.onGear(Gear.DRIVE))
        assertNull(t.state.session)
        assertNull(t.state.reminder)
        assertEquals(this.candidate, t.candidate) // 후보는 그대로 둔다
    }

    @Test fun `주차 중에 앱이 다시 떴는데 이미 D 면 기준을 잡은 뒤 출차로 본다`() {
        tracker().apply { this.candidate = this@ParkingTrackerTest.candidate; onGear(Gear.DRIVE); onGear(Gear.PARK) }
        now += 30 * min
        val t = realTracker()
        assertNull(t.onGear(Gear.DRIVE)) // 기준 잡는 중
        now += 2_500
        assertTrue(t.settle() is ParkingEvent.Ended)
    }

    @Test fun `주차 중 R 로 바꿨다가 다시 P 면 자리를 고친 것 - 출차가 아니다`() {
        val t = tracker().apply { this.candidate = this@ParkingTrackerTest.candidate }
        t.onGear(Gear.DRIVE); t.onGear(Gear.PARK)
        now += 40 * min
        assertNull(t.onGear(Gear.REVERSE))
        assertNull(t.onGear(Gear.PARK))
        assertEquals(t0, t.state.session!!.startedAt) // 주차 시각 그대로
        assertNull(t.state.reminder)
    }

    @Test fun `주차 중 R 로 빼서 D 로 가면 출차`() {
        val t = tracker().apply { this.candidate = this@ParkingTrackerTest.candidate }
        t.onGear(Gear.DRIVE); t.onGear(Gear.PARK)
        now += 40 * min
        assertNull(t.onGear(Gear.REVERSE))
        assertTrue(t.onGear(Gear.DRIVE) is ParkingEvent.Ended)
    }

    @Test fun `머물 예정 시간을 기록하고 혜택 가게를 바꿀 수 있다`() {
        val t = tracker().apply { this.candidate = this@ParkingTrackerTest.candidate.copy(plannedMin = 120) }
        t.startNow()
        assertEquals(120, t.state.session!!.plannedMin)
        t.switchDeal(com.parkdeal.core.OtherDeal("커스텀커피", "커스텀커피 구매 시 1시간 무료", 1000,
            com.parkdeal.core.BenefitHow("카운터에 차량번호를 알려 주세요", emptyList(), false), 60))
        val s = t.state.session!!
        assertEquals("커스텀커피", s.storeName)
        assertEquals(60, s.freeMin)
        assertEquals("카운터에 차량번호를 알려 주세요", s.redeemHint)
    }

    @Test fun `정차는 D 로 출발하면 출차 확인 없이 끝난다`() {
        val t = tracker().apply { this.candidate = this@ParkingTrackerTest.candidate.copy(stop = true) }
        t.onGear(Gear.DRIVE); t.onGear(Gear.PARK)
        assertTrue(t.state.session!!.stop)
        now += 3 * min
        assertNull(t.onGear(Gear.DRIVE))
        assertNull(t.state.session)
        assertNull(t.state.reminder)
    }

    private class MemoryStore : StateStore {
        var value: String? = null
        override fun load() = value
        override fun save(json: String) { value = json }
    }
}
