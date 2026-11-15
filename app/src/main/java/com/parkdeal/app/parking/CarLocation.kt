package com.parkdeal.app.parking

import ai.pleos.playground.location.fused.FusedLocation
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 차 위치를 한 번 읽는다 (Pleos FusedLocation). 차종마다 지원이 달라 isSupported 를 먼저 본다(가이드 4장).
 * 위치 업데이트를 구독해 첫 값을 받으면 바로 끊는다. 5초 안에 못 받으면 포기한다 —
 * 위치 확인은 덤이라 주차 기록을 막지 않는다.
 */
object CarLocation {
    private const val TAG = "ParkDeal"
    private const val TIMEOUT_MS = 5_000L

    fun once(context: Context, onResult: (lat: Double, lng: Double) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        val loc = FusedLocation(context.applicationContext)
        var done = false
        lateinit var bridge: FusedLocationBridge
        fun finish() {
            if (done) return
            done = true
            runCatching { loc.unregisterFusedLocationCallback(bridge) }
            runCatching { loc.release() }
        }
        bridge = FusedLocationBridge(object : FusedLocationBridge.Callback {
            override fun onLocation(lat: Double, lng: Double) {
                main.post {
                    if (done) return@post
                    Log.d(TAG, "차 위치 $lat,$lng")
                    finish()
                    onResult(lat, lng)
                }
            }
            override fun onFailure(e: Exception) {
                Log.w(TAG, "차 위치를 읽지 못함: ${e.message}")
            }
        })
        runCatching {
            loc.initialize()
            val supported = loc.isSupported()
            Log.d(TAG, "FusedLocation 지원 $supported")
            if (!supported) { finish(); return }
            loc.registerFusedLocationCallback(bridge)
            main.postDelayed({ if (!done) { Log.w(TAG, "차 위치 시간 초과"); finish() } }, TIMEOUT_MS)
        }.onFailure { Log.w(TAG, "FusedLocation 사용 불가: ${it.message}"); finish() }
    }
}
