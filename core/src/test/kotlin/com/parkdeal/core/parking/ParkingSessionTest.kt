package com.parkdeal.core.parking

import com.parkdeal.core.FeeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParkingSessionTest {
    private val min = 60_000L
    private val t0 = 1_700_000_000_000L
    /** 최초 30분 2000원, 30분당 2000원 */
    private val rule = FeeRule.Metered(30, 2000, 30, 2000, null)

    private fun session(freeMin: Int = 120, store: String? = "아마존바베큐", rule: FeeRule? = this.rule) =
        ParkingSession(t0, 1, "커먼그라운드", 37.54, 127.07, rule, freeMin, store, "", "아마존바베큐")

    @Test fun `경과 시간은 분 단위 내림`() {
        assertEquals(70, session().elapsedMin(t0 + 70 * min + 59_000))
    }

    @Test fun `무료 시간 안이면 지금 요금은 0원`() {
        assertEquals(0, session().fee(t0 + 100 * min)!!.withBenefit)
    }

    @Test fun `무료 시간을 넘기면 넘긴 만큼만 낸다`() {
        // 150분: 10000원 - 120분분 8000원 = 2000원
        assertEquals(2000, session().fee(t0 + 150 * min)!!.withBenefit)
    }

    @Test fun `요금 규칙을 모르면 요금은 null`() {
        assertNull(session(rule = null).fee(t0 + 10 * min))
    }

    @Test fun `남은 무료 시간은 0 밑으로 내려가지 않고 혜택이 없으면 null`() {
        assertEquals(50, session().freeLeftMin(t0 + 70 * min))
        assertEquals(0, session().freeLeftMin(t0 + 200 * min))
        assertNull(session(freeMin = 0, store = null).freeLeftMin(t0))
    }

    @Test fun `무료 종료 15분 전에 알린다`() {
        assertEquals(t0 + 105 * min, session().freeWarnAt())
    }

    @Test fun `무료 시간이 알림 간격 이하거나 없으면 알리지 않는다`() {
        assertNull(session(freeMin = 15).freeWarnAt())
        assertNull(session(freeMin = 0, store = null).freeWarnAt())
    }

    @Test fun `출차 요금은 최소 1분으로 계산한다`() {
        val fee = session(freeMin = 0, store = null).exitFee(t0)!!
        assertEquals(1, fee.minutes)
        assertEquals(2000, fee.withoutBenefit)
    }

    @Test fun `상태 문장 - 무료 시간이 남은 경우`() {
        assertEquals(
            "커먼그라운드에 주차한 지 1시간 10분 됐어요. 아마존바베큐 무료 시간이 50분 남아서 지금 나가면 0원이에요.",
            ParkingTalk.status(session(), t0 + 70 * min),
        )
    }

    @Test fun `상태 문장 - 혜택 없는 주차`() {
        assertEquals(
            "커먼그라운드에 주차한 지 45분 됐어요. 지금 나가면 약 4,000원이에요.",
            ParkingTalk.status(session(freeMin = 0, store = null), t0 + 45 * min),
        )
    }

    @Test fun `무료 종료 임박 경고는 이후 요금 단위를 알려준다`() {
        assertEquals(
            "아마존바베큐 무료 주차가 15분 남았어요. 그 뒤로는 30분마다 2,000원이 붙어요.",
            ParkingTalk.freeWarning(session(), t0 + 105 * min),
        )
    }

    @Test fun `주차 직후 - 혜택 가게 이용과 등록을 안내한다`() {
        val s = session().copy(walkMeters = 120)
        assertEquals("커먼그라운드에 세웠어요. 아마존바베큐 이용하고 주차 등록하세요. 목적지까지 걸어서 2분이에요.", ParkingTalk.parked(s))
    }

    @Test fun `주차 직후 - 등록 방법을 알면 그대로 안내한다`() {
        val s = session().copy(walkMeters = 60, redeemHint = "카운터에 차량번호를 알려 주세요")
        assertEquals("커먼그라운드에 세웠어요. 아마존바베큐 이용하고 카운터에 차량번호를 알려 주세요. 목적지까지 걸어서 1분이에요.", ParkingTalk.parked(s))
    }

    @Test fun `주차 직후 - 혜택이 없으면 위치와 걷는 시간만`() {
        val s = session(freeMin = 0, store = null).copy(walkMeters = 0)
        assertEquals("커먼그라운드에 세웠어요. 목적지까지 걸어서 1분이에요.", ParkingTalk.parked(s))
    }

    @Test fun `출차 리마인드 - 혜택을 등록하지 않으면 낼 돈을 알려준다`() {
        val s = session()
        assertEquals(
            "아마존바베큐에서 무료 주차 등록하셨나요? 등록하지 않으면 약 6,000원이 나와요.",
            ParkingTalk.exitReminder(ExitReminder(s, t0 + 90 * min)),
        )
    }

    @Test fun `출차 리마인드 - 혜택 없는 주차는 요금만`() {
        assertEquals(
            "커먼그라운드 주차 요금은 약 4,000원이에요.",
            ParkingTalk.exitReminder(ExitReminder(session(freeMin = 0, store = null), t0 + 45 * min)),
        )
    }

    @Test fun `출차 리마인드 - 요금 정보가 없으면 확인하라고 한다`() {
        assertEquals(
            "커먼그라운드 주차 요금을 확인하세요.",
            ParkingTalk.exitReminder(ExitReminder(session(freeMin = 0, store = null, rule = null), t0 + 45 * min)),
        )
    }
}
