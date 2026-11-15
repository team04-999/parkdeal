package com.parkdeal.app.navi

import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.parkdeal.core.navi.Bookmark
import com.parkdeal.core.navi.Place

/**
 * Pleos NaviHelper 로 내비 목적지를 받고, 고른 주차장으로 경로를 바꾼다.
 * NaviHelper 는 내비 탑재 차량에서만 완전히 동작하므로(docs/pleos-guidelines.md 4장) 초기화에 실패하면
 * [available] 이 false 로 남고 앱은 데모 목적지로 동작한다.
 */
class NaviSource(
    context: Context,
    private val onDestination: (Place, remainMeters: Int) -> Unit,
    /** 주행 중 목적지까지 남은 거리(m). 내비가 보내는 주행 정보 이벤트에서 받는다 (폴링하지 않는다). */
    private val onRemain: (destName: String, meters: Int) -> Unit = { _, _ -> },
    /** 경유지(고른 주차장)에 다 왔다. */
    private val onWaypointArrived: (name: String) -> Unit = {},
    /** 내비 즐겨찾기(집·회사·기타). 시작할 때와 바뀔 때 받는다. */
    private val onBookmarks: (List<Bookmark>) -> Unit = {},
) {
    private val navi = NaviHelper(context.applicationContext)
    private val main = Handler(Looper.getMainLooper())
    @Volatile var available = false
        private set
    /** 연결 전에 들어온 경로 요청. 연결되면 보낸다. */
    private var pending: Place? = null

    private val listener = object : NaviHelperEventListener {
        override fun onDestinationInfo(info: DestinationInfo) {
            main.post { onDestination(Place(info.name, info.latitude, info.longitude), info.remainDistance) }
        }
        override fun onDestinationChanged() { navi.getDestinationInfo() }
        override fun onDrivingInfo(info: ai.pleos.playground.navi.data.DrivingInfo) {
            val d = info.destination ?: return
            main.post { onRemain(d.name, d.distance) }
        }
        override fun onBookmarkInfo(list: List<ai.pleos.playground.navi.data.BookmarkInfo>) {
            val bs = list.map {
                val kind = when (it.category) {
                    ai.pleos.playground.navi.constants.BookmarkType.HOME -> Bookmark.Kind.HOME
                    ai.pleos.playground.navi.constants.BookmarkType.OFFICE -> Bookmark.Kind.OFFICE
                    else -> Bookmark.Kind.OTHER
                }
                Bookmark(it.name, it.latitude, it.longitude, kind)
            }
            main.post { onBookmarks(bs) }
        }
        override fun onBookMarkChanged() { navi.getBookmarkInfo() }
        override fun onWayPointArrived(info: ai.pleos.playground.navi.data.WaypointArrivedInfo) {
            main.post { onWaypointArrived(info.name) }
        }
        override fun onRouteStarted(info: ai.pleos.playground.navi.data.RouteStartInfo) { navi.getDestinationInfo() }
    }

    fun start() {
        navi.initialize(
            {
                available = true
                navi.addListener(listener)
                navi.getDestinationInfo()
                navi.getBookmarkInfo()
                main.post { pending?.let { pending = null; route(it) } }
            },
            { Log.w(TAG, "내비 연결 실패: ${it.message}") },
        )
    }

    /** [place] 로 경로를 요청한다. 아직 연결 전이면 연결된 뒤 보낸다. 지금 보냈으면 true. */
    fun route(place: Place): Boolean {
        if (!available) { pending = place; return false }
        navi.requestRoute(RouteInfo(latitude = place.lat, longitude = place.lng, poiName = place.name, poiId = "", address = "", poiSubId = ""))
        return true
    }

    /**
     * 목적지(가게)는 두고 [lot] 을 첫 경유지로 넣는다. 이미 넣은 경유지는 바꾼다.
     * 안내 중인 경로가 없으면(내비 목적지가 없을 때) [lot] 으로 경로를 건다.
     */
    fun via(lot: Place, hasRoute: Boolean): Boolean {
        if (!available) return false
        if (!hasRoute) return route(lot)
        runCatching { navi.removeWaypoint(WaypointIndex.ALL) }
        navi.addWaypoint(RequestWaypointInfo(lot.lat, lot.lng, lot.name, WaypointIndex.FIRST, "", "", "", ""))
        return true
    }

    /** Pleos 내비 화면을 앞으로 띄운다 (목적지는 내비에서 찾는다. 우리 앱엔 키보드 입력이 없다). */
    fun openNavigation(): Boolean {
        if (!available) return false
        runCatching { navi.requestNavigationScreen(ai.pleos.playground.navi.data.NavigationScreenRequest.Foreground) }
            .onFailure { Log.w(TAG, "내비 화면 열기 실패: ${it.message}"); return false }
        return true
    }

    fun stop() {
        runCatching { navi.removeListener(listener) }
        runCatching { navi.release() }
    }

    private companion object { const val TAG = "ParkDeal" }
}
