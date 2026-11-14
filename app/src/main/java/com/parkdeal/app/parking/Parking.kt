package com.parkdeal.app.parking

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.parkdeal.core.parking.ParkingState
import com.parkdeal.core.parking.ParkingTracker
import com.parkdeal.core.parking.StateStore

/**
 * 앱 전체가 함께 쓰는 주차 상태. 서비스가 기어로 바꾸고, 화면은 읽고 구독한다.
 * 상태는 SharedPreferences 에 저장돼 앱을 전환하거나 프로세스가 다시 떠도 이어진다.
 */
object Parking {
    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<(ParkingState) -> Unit>()
    @Volatile private var instance: ParkingTracker? = null

    fun tracker(context: Context): ParkingTracker = instance ?: synchronized(this) {
        instance ?: ParkingTracker(PrefsStore(context.applicationContext)) { System.currentTimeMillis() }
            .also { it.onChange = ::changed; instance = it }
    }

    /** 리스너는 메인 스레드에서 불린다. */
    fun observe(l: (ParkingState) -> Unit) = synchronized(listeners) { listeners += l }
    fun unobserve(l: (ParkingState) -> Unit) = synchronized(listeners) { listeners -= l }

    private fun changed(state: ParkingState) {
        val ls = synchronized(listeners) { listeners.toList() }
        main.post { ls.forEach { it(state) } }
    }

    private class PrefsStore(context: Context) : StateStore {
        private val prefs = context.getSharedPreferences("parking", Context.MODE_PRIVATE)
        override fun load(): String? = prefs.getString(KEY, null)
        override fun save(json: String) { prefs.edit().putString(KEY, json).apply() }
        private companion object { const val KEY = "state" }
    }
}
