package com.parkdeal.core.parking

import com.parkdeal.core.ParkingOption
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 앱이 쓰는 기어 값. 차량 API 의 값을 여기로 옮긴다. */
enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, OTHER }

/**
 * 기어 입력. 지금은 Pleos Vehicle SDK 로 구현하고, 양산 탑재 때 `CarPropertyManager` 로 바꾼다
 * (docs/architecture.md). 리스너로만 받는다. 폴링하지 않는다.
 */
interface GearSource {
    /** 기어가 바뀔 때마다 [onGear]. 이 차에서 기어를 읽을 수 없으면 [onUnavailable]. */
    fun start(onGear: (Gear) -> Unit, onUnavailable: (Throwable) -> Unit)
    fun stop()
}

/** 지금 P단에 넣으면 주차할 곳으로 보는 주차장: 추천에서 고른 것. */
data class Candidate(val option: ParkingOption, val storeName: String?, val destName: String)

/** 기기에 저장하는 주차 상태. session·reminder 가 둘 다 null 이면 대기 중. [history] 는 최근 것이 뒤. */
@Serializable
data class ParkingState(
    val session: ParkingSession? = null,
    val reminder: ExitReminder? = null,
    val history: List<LogEntry> = emptyList(),
)

/** 출차 1건의 기록. [paid] 는 요금 규칙을 모르면 null. */
@Serializable
data class LogEntry(val endedAt: Long, val lotName: String, val minutes: Int, val paid: Int?, val saved: Int)

/** 한 달 주차 합계: 낸 돈(추정)과 가게 혜택으로 아낀 돈. */
data class MonthSummary(val count: Int, val paid: Int, val saved: Int) {
    companion object {
        fun of(history: List<LogEntry>, year: Int, month: Int, zone: java.time.ZoneId): MonthSummary {
            val inMonth = history.filter {
                val d = java.time.Instant.ofEpochMilli(it.endedAt).atZone(zone)
                d.year == year && d.monthValue == month
            }
            return MonthSummary(inMonth.size, inMonth.sumOf { it.paid ?: 0 }, inMonth.sumOf { it.saved })
        }
    }
}

/** 저장소. 앱에서는 SharedPreferences, 테스트에서는 메모리. */
interface StateStore {
    fun load(): String?
    fun save(json: String)
}

sealed interface ParkingEvent {
    data class Started(val session: ParkingSession) : ParkingEvent
    data class Ended(val reminder: ExitReminder) : ParkingEvent
}

/**
 * 기어 전환으로 주차를 시작하고 끝낸다.
 *  - 처음 받은 기어는 기준으로만 쓴다. 앱을 켰을 때 이미 P 였던 것을 주차 시작으로 보지 않는다
 *  - P 로 바뀌면 [candidate] 주차장으로 주차 시작
 *  - 주차 중 D·R 로 바뀌면 출차 (P → N → D 도 출차)
 * 상태는 바뀔 때마다 [store] 에 저장해 프로세스가 다시 만들어져도 이어진다.
 */
class ParkingTracker(
    private val store: StateStore,
    /** 첫 기어 값을 받은 뒤 이 시간 동안 들어온 값은 기준으로만 쓴다 (리스너 등록 직후 이전 값이 섞여 온다). */
    private val settleMs: Long = SETTLE_MS,
    /** 이보다 짧게 끝난 주차는 잘못 감지한 것으로 보고 리마인드 없이 버린다. */
    private val minSessionMs: Long = MIN_SESSION_MS,
    private val clock: () -> Long,
) {
    var state: ParkingState = load()
        private set

    @Volatile var candidate: Candidate? = null
    /** 상태가 바뀐 뒤 불린다. 화면 갱신용. */
    @Volatile var onChange: ((ParkingState) -> Unit)? = null
    private var lastGear: Gear? = null
    private var firstGearAt: Long? = null

    @Synchronized
    fun onGear(gear: Gear): ParkingEvent? {
        val prev = lastGear
        lastGear = gear
        val first = firstGearAt ?: clock().also { firstGearAt = it }
        if (clock() - first < settleMs) return null // 아직 기준을 잡는 중
        if (prev == gear) return null
        return when {
            gear == Gear.PARK && prev != null -> startNow()?.let { ParkingEvent.Started(it) }
            gear == Gear.DRIVE || gear == Gear.REVERSE -> end()?.let { ParkingEvent.Ended(it) }
            else -> null
        }
    }

    /**
     * 기준 잡기가 끝난 뒤 한 번 부른다. 주차 중으로 저장돼 있는데 기준 기어가 D·R 이면
     * 앱이 꺼진 사이 출발한 것이므로 출차로 처리한다.
     */
    @Synchronized
    fun settle(): ParkingEvent? {
        val g = lastGear ?: return null
        if (g != Gear.DRIVE && g != Gear.REVERSE) return null
        return end()?.let { ParkingEvent.Ended(it) }
    }

    /** P 단 없이 직접 시작 (기어를 읽을 수 없는 차, 또는 화면의 "주차 시작" 버튼). */
    @Synchronized
    fun startNow(): ParkingSession? {
        if (state.session != null) return null
        val c = candidate ?: return null
        val o = c.option
        val s = ParkingSession(
            startedAt = clock(), lotId = o.lotId, lotName = o.name, lat = o.lat, lng = o.lng, rule = o.rule,
            freeMin = o.freeMin, storeName = c.storeName.takeIf { o.freeMin > 0 }, reason = o.reason, destName = c.destName,
            walkMeters = o.walkMeters,
        )
        update(state.copy(session = s, reminder = null))
        return s
    }

    /** 시연용: 진행 중 주차의 시작 시각을 [minutes]분 앞당긴다. 영상에서 긴 주차를 몇 초 안에 보여주려고 쓴다. */
    @Synchronized
    fun backdate(minutes: Int): ParkingSession? {
        if (minutes !in 1..MAX_BACKDATE_MIN) return null
        val s = state.session ?: return null
        val moved = s.copy(startedAt = s.startedAt - minutes * 60_000L)
        update(state.copy(session = moved))
        return moved
    }

    /** 출차 리마인드를 닫고 기록을 남긴다. [registered] 는 운전자가 "등록했어요"를 눌렀는지. */
    @Synchronized
    fun dismissReminder(registered: Boolean = false) {
        val r = state.reminder ?: return
        val fee = r.fee
        val entry = LogEntry(
            endedAt = r.endedAt,
            lotName = r.session.lotName,
            minutes = fee?.minutes ?: r.session.elapsedMin(r.endedAt),
            paid = fee?.let { if (registered) it.withBenefit else it.withoutBenefit },
            saved = if (registered && fee != null) fee.saved else 0,
        )
        update(state.copy(reminder = null, history = (state.history + entry).takeLast(MAX_HISTORY)))
    }

    private fun end(): ExitReminder? {
        val s = state.session ?: return null
        if (clock() - s.startedAt < minSessionMs) {
            // 기어가 잠깐 흔들린 것. 주차 기록도 리마인드도 남기지 않는다
            update(state.copy(session = null))
            return null
        }
        val r = ExitReminder(s, clock())
        candidate = null
        update(state.copy(session = null, reminder = r))
        return r
    }

    private fun update(next: ParkingState) {
        state = next
        store.save(json.encodeToString(ParkingState.serializer(), next))
        onChange?.invoke(next)
    }

    private fun load(): ParkingState =
        store.load()?.let { runCatching { json.decodeFromString(ParkingState.serializer(), it) }.getOrNull() } ?: ParkingState()

    companion object {
        /** 시연용 건너뛰기 상한: 하루. */
        const val MAX_BACKDATE_MIN = 24 * 60
        const val MAX_HISTORY = 100
        const val SETTLE_MS = 2_000L
        const val MIN_SESSION_MS = 10_000L
        private val json = Json { ignoreUnknownKeys = true }
    }
}
