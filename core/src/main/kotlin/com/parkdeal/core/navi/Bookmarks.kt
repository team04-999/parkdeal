package com.parkdeal.core.navi

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 내비 즐겨찾기 하나. 앱에서 따로 등록받지 않고 내비 것을 쓴다 (PRD S11). */
data class Bookmark(val name: String, val lat: Double, val lng: Double, val kind: Kind) {
    enum class Kind { HOME, OFFICE, OTHER }
}

class Bookmarks(private val list: List<Bookmark>) {
    /**
     * 목적지가 집·회사(150m 안)면 그 즐겨찾기. 평소 가는 곳이라 주차 안내를 먼저 꺼내지 않는다.
     * "할머니 집" 같은 다른 즐겨찾기는 주차가 필요할 수 있어 평소 목적지로 보지 않는다.
     */
    fun routine(dest: Place, withinMeters: Int = 150): Bookmark? =
        list.filter { it.kind != Bookmark.Kind.OTHER }
            .firstOrNull { meters(it.lat, it.lng, dest.lat, dest.lng) <= withinMeters }

    private fun meters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}
