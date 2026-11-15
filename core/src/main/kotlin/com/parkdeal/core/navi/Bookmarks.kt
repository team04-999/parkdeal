package com.parkdeal.core.navi


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
            .firstOrNull { com.parkdeal.core.Geo.meters(it.lat, it.lng, dest.lat, dest.lng) <= withinMeters }
}
