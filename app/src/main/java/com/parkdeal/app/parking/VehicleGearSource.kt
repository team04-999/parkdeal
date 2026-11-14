package com.parkdeal.app.parking

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.parkdeal.core.parking.Gear
import com.parkdeal.core.parking.GearSource

/** Pleos Vehicle SDK 의 기어 리스너. 콜백은 메인 스레드로 옮겨 준다. */
class VehicleGearSource(context: Context) : GearSource {
    private val vehicle = Vehicle(context.applicationContext)
    private val main = Handler(Looper.getMainLooper())
    private var listener: GearSelectionListener? = null

    override fun start(onGear: (Gear) -> Unit, onUnavailable: (Throwable) -> Unit) {
        stop()
        vehicle.initialize()
        val l = object : GearSelectionListener {
            override fun onSelected(gear: VehicleGear?) { main.post { onGear(map(gear)) } }
            override fun onFailed(e: Exception) { main.post { onUnavailable(e) } }
        }
        listener = l
        val g = vehicle.getGear()
        // 시작 시점 기어를 먼저 받아 기준으로 삼는다 (ParkingTracker 는 첫 값을 주차 시작으로 보지 않는다)
        g.getCurrentGearSelectionState({ main.post { onGear(map(it)) } }, { main.post { onUnavailable(it) } })
        g.registerGearSelection(l)
    }

    override fun stop() {
        val l = listener ?: return
        listener = null
        runCatching { vehicle.getGear().unregisterGearSelection(l) }
        runCatching { vehicle.release() }
    }

    private fun map(g: VehicleGear?): Gear = when (g) {
        VehicleGear.GEAR_PARK -> Gear.PARK
        VehicleGear.GEAR_REVERSE -> Gear.REVERSE
        VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
        VehicleGear.GEAR_DRIVE, VehicleGear.GEAR_1, VehicleGear.GEAR_2, VehicleGear.GEAR_3, VehicleGear.GEAR_4,
        VehicleGear.GEAR_5, VehicleGear.GEAR_6, VehicleGear.GEAR_7, VehicleGear.GEAR_8, VehicleGear.GEAR_9 -> Gear.DRIVE
        else -> Gear.OTHER
    }
}
