package com.parkdeal.app.navi

import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.parkdeal.core.navi.Place

/**
 * Pleos NaviHelper 로 내비 목적지를 받고, 고른 주차장으로 경로를 바꾼다.
 * NaviHelper 는 내비 탑재 차량에서만 완전히 동작하므로(docs/pleos-guidelines.md 4장) 초기화에 실패하면
 * [available] 이 false 로 남고 앱은 데모 목적지로 동작한다.
 */
class NaviSource(context: Context, private val onDestination: (Place, remainMeters: Int) -> Unit) {
    private val navi = NaviHelper(context.applicationContext)
    private val main = Handler(Looper.getMainLooper())
    @Volatile var available = false
        private set

    private val listener = object : NaviHelperEventListener {
        override fun onDestinationInfo(info: DestinationInfo) {
            main.post { onDestination(Place(info.name, info.latitude, info.longitude), info.remainDistance) }
        }
        override fun onDestinationChanged() { navi.getDestinationInfo() }
        override fun onRouteStarted(info: ai.pleos.playground.navi.data.RouteStartInfo) { navi.getDestinationInfo() }
    }

    fun start() {
        navi.initialize(
            {
                available = true
                navi.addListener(listener)
                navi.getDestinationInfo()
            },
            { Log.w(TAG, "내비 연결 실패: ${it.message}") },
        )
    }

    /** [place] 로 경로를 요청한다. 내비가 없으면 false. */
    fun route(place: Place): Boolean {
        if (!available) return false
        navi.requestRoute(RouteInfo(latitude = place.lat, longitude = place.lng, poiName = place.name, poiId = "", address = "", poiSubId = ""))
        return true
    }

    fun stop() {
        runCatching { navi.removeListener(listener) }
        runCatching { navi.release() }
    }

    private companion object { const val TAG = "ParkDeal" }
}
