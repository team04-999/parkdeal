package com.parkdeal.app

import com.parkdeal.core.api.ApiRequest

/**
 * 시연용 목적지. 내비 목적지 연동 전까지 화면이 이 목적지로 데이터를 불러오고,
 * 서버·캐시가 모두 없을 때를 위해 같은 목적지의 응답을 assets/api-snapshot 에 넣어 둔다
 * (scripts/snapshot_api.py 로 만든다). 데이터 범위는 서울 지하철 2호선 역 주변이다.
 */
data class DemoPlace(val name: String, val lat: Double, val lng: Double) {
    val destinationRequest get() = ApiRequest.Destination(lat, lng, name)
    val lotsRequest get() = ApiRequest.Lots(lat, lng)
}

object DemoPlaces {
    val all = listOf(
        // 강남역. 아크플레이스 주차장(30분 6,000원) 2시간 무료 — 혜택으로 아끼는 돈이 크게 보인다
        DemoPlace("필앤필버거", 37.499734, 127.034964),
        DemoPlace("조이치과", 37.482052, 126.942983),
        // 신림·봉천: 서울시 실시간 빈자리가 나오는 봉천복개3 공영주차장이 가깝다
        DemoPlace("영원순두부", 37.486085, 126.930807),
        // 광진 뚝섬로: 구청이 정한 정차 허용 구간(노룬산골목시장 앞)이 같은 도로에 있다 — 정차 시연
        DemoPlace("진한국밥", 37.532855, 127.077288),
    )
}
