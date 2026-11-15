package com.parkdeal.core.parking

import com.parkdeal.core.Geo
import com.parkdeal.core.ParkingOption
import kotlinx.serialization.Serializable

/** P 단에 넣었을 때 차가 고른 주차장에서 [distanceM] 떨어져 있었다. 차 위치는 [carLat],[carLng]. */
@Serializable
data class FarFrom(val distanceM: Int, val carLat: Double, val carLng: Double)

/**
 * 추천과 다른 곳에 세웠는지 확인한다. 지금은 P 단에 넣으면 고른 주차장에 세웠다고 보는데,
 * 다른 곳이면 요금·혜택 안내가 틀린다. 차 위치(FusedLocation)로 확인하고 다르면 운전자에게 묻는다.
 */
object ParkCheck {
    /** 이 거리 안이면 고른 주차장에 세운 것으로 본다 (주차장 좌표는 건물 중심점이라 여유를 둔다) */
    const val SAME_PLACE_M = 150
    /** 차 위치에서 이 거리 안의 후보만 "여기 세우셨나요?"로 제시한다 */
    private const val GUESS_WITHIN_M = 300

    fun distance(s: ParkingSession, carLat: Double, carLng: Double): Int? {
        val lat = s.lat ?: return null
        val lng = s.lng ?: return null
        return Geo.meters(lat, lng, carLat, carLng).toInt()
    }

    /** 차 위치에서 가까운 다른 후보(최대 3곳). 고른 주차장은 뺀다. */
    fun guesses(s: ParkingSession, options: List<ParkingOption>): List<ParkingOption> {
        val f = s.farFrom ?: return emptyList()
        return options.asSequence()
            .filter { it.lotId != s.lotId && it.lat != null && it.lng != null }
            .map { it to Geo.meters(f.carLat, f.carLng, it.lat!!, it.lng!!) }
            .filter { it.second <= GUESS_WITHIN_M }
            .sortedBy { it.second }
            .map { it.first }
            .take(3)
            .toList()
    }
}
