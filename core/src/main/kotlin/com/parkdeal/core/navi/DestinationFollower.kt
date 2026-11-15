package com.parkdeal.core.navi

/** 내비 목적지 또는 데모 목적지. */
data class Place(val name: String, val lat: Double, val lng: Double)

/**
 * 내비 목적지를 따라간다. 추천을 다시 만들 "새 목적지"만 돌려준다.
 *  - 같은 이름이 다시 오면(남은 거리 갱신 등) 무시
 *  - 앱이 "여기로 안내"로 주차장으로 경로를 바꾼 경우는 원래 목적지(가게)를 유지
 */
class DestinationFollower {
    var current: Place? = null
        private set
    var guiding: String? = null
        private set

    @Synchronized
    fun onDestination(p: Place): Place? {
        if (p.name.isBlank()) return null
        if (p.name == current?.name) return null
        if (p.name == guiding) return null
        current = p
        guiding = null
        return p
    }

    /** 앱이 [lotName] 으로 경로를 바꿨다. 이 이름으로 오는 목적지는 새 목적지로 보지 않는다. */
    @Synchronized
    fun guidingTo(lotName: String) { guiding = lotName }
}
