package com.parkdeal.app.drive

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 주행 제한 상태. 화면을 줄일지는 기어가 아니라 이 값으로 정한다 (Pleos 가이드 1장).
 * 차량 서비스에 연결하지 못하면 제한 없음으로 본다.
 */
class DrivingRestriction(context: Context, private val onChange: (restricted: Boolean) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null
    @Volatile var restricted = false
        private set

    private val listener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { r ->
        main.post { update(r.isRequiresDistractionOptimization) }
    }

    init {
        runCatching {
            val c = Car.createCar(context.applicationContext)
            car = c
            val m = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            manager = m
            m.registerListener(listener)
            m.currentCarUxRestrictions?.let { restricted = it.isRequiresDistractionOptimization }
        }.onFailure { Log.w(TAG, "주행 제한 상태를 읽을 수 없음: ${it.message}") }
    }

    private fun update(value: Boolean) {
        if (value == restricted) return
        restricted = value
        Log.d(TAG, "주행 제한 $value")
        onChange(value)
    }

    fun close() {
        runCatching { manager?.unregisterListener() }
        runCatching { car?.disconnect() }
    }

    private companion object { const val TAG = "ParkDeal" }
}
