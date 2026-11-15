package com.parkdeal.app.drive

import ai.pleos.playground.vehicle.Vehicle
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.parkdeal.core.Vehicle as Car

/**
 * 이 차가 전기차인지 Vehicle SDK 로 알아낸다. 배터리 잔량(SoC)을 읽을 수 있으면 전기차로 본다.
 * 읽지 못하면 모름(null)으로 두고 아무것도 거르지 않는다 (차종 이름으로 분기하지 않는다, 가이드 4장).
 */
object VehicleProfile {
    fun detect(context: Context, onResult: (Car) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        val v = Vehicle(context.applicationContext)
        runCatching {
            v.initialize()
            v.getEvBattery().getEvBatteryStateOfCharge(
                { main.post { onResult(Car(ev = true)); runCatching { v.release() } } },
                { main.post { onResult(Car.UNKNOWN); runCatching { v.release() } } },
            )
        }.onFailure { onResult(Car.UNKNOWN) }
    }
}
