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

/** 기기에 저장하는 주차 상태. 둘 다 null 이면 대기 중. */
@Serializable
data class ParkingState(val session: ParkingSession? = null, val reminder: ExitReminder? = null)

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
class ParkingTracker(private val store: StateStore, private val clock: () -> Long) {
    var state: ParkingState = load()
        private set

    @Volatile var candidate: Candidate? = null
    /** 상태가 바뀐 뒤 불린다. 화면 갱신용. */
    @Volatile var onChange: ((ParkingState) -> Unit)? = null
    private var lastGear: Gear? = null

    @Synchronized
    fun onGear(gear: Gear): ParkingEvent? {
        val prev = lastGear
        lastGear = gear
        if (prev == gear) return null
        return when {
            gear == Gear.PARK && prev != null -> startNow()?.let { ParkingEvent.Started(it) }
            gear == Gear.DRIVE || gear == Gear.REVERSE -> end()?.let { ParkingEvent.Ended(it) }
            else -> null
        }
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
        )
        update(ParkingState(session = s))
        return s
    }

    @Synchronized
    fun dismissReminder() = update(state.copy(reminder = null))

    private fun end(): ExitReminder? {
        val s = state.session ?: return null
        val r = ExitReminder(s, clock())
        candidate = null
        update(ParkingState(reminder = r))
        return r
    }

    private fun update(next: ParkingState) {
        state = next
        store.save(json.encodeToString(ParkingState.serializer(), next))
        onChange?.invoke(next)
    }

    private fun load(): ParkingState =
        store.load()?.let { runCatching { json.decodeFromString(ParkingState.serializer(), it) }.getOrNull() } ?: ParkingState()

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
