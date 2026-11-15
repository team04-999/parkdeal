package com.parkdeal.core.navi

import com.parkdeal.core.ParkingOption

/** 목적지 1km 안으로 들어오면 목적지마다 한 번 "곧 도착" 안내를 하게 한다. */
class ApproachTrigger(private val withinMeters: Int = 1_000) {
    private var announcedFor: String? = null

    /** 남은 거리를 받을 때마다 부른다. 지금 알려야 하면 true. 0 은 정보 없음. */
    @Synchronized
    fun onRemain(destName: String, meters: Int): Boolean {
        if (meters !in 1..withinMeters) return false
        if (announcedFor == destName) return false
        announcedFor = destName
        return true
    }
}

/** 내비 이벤트에 맞춰 읽을 짧은 문장. 운전 중이라 한 문장에 하나만 말한다. */
object NaviTalk {
    fun approaching(top: ParkingOption): String = "곧 도착해요. 주차는 ${top.name}, ${fee(top.fee)}."

    /** 경유지(고른 주차장)에 다 왔을 때: 자리가 없을 때의 다음 후보까지. */
    fun waypointArrived(lotName: String, next: ParkingOption?): String {
        val head = "${lotName}에 거의 왔어요."
        next ?: return head
        return "$head 자리가 없으면 다음은 ${next.name}, ${fee(next.fee)}."
    }

    private fun fee(v: Int?) = when (v) {
        null -> "요금은 확인이 필요해요"
        0 -> "0원이에요"
        else -> "약 %,d원이에요".format(v)
    }
}
