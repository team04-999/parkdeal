package com.parkdeal.core

/** 주행 중 TTS 로 전할 한 문장. 주행 중에는 화면을 쓰지 않으므로 이 문장이 안내의 전부다. */
object Briefing {
    fun sentence(r: Recommendation, stayMin: Int): String {
        val top = r.options.firstOrNull() ?: return "근처에서 추천할 주차장을 찾지 못했어요."
        if (top.reason.isNotEmpty() && (top.freeMin > 0 || top.fee == 0)) {
            val free = if (top.freeMin > 0) "${durationText(top.freeMin)} 무료" else "무료"
            val lead = top.reason.substringBefore(" 시 ") + " 시"
            return "$lead ${top.name}${subject(top.name)} ${free}예요."
        }
        val cost = when (val fee = top.fee) {
            null -> "요금은 확인이 필요하고"
            0 -> "${durationText(stayMin)} 무료,"
            else -> "${durationText(stayMin)}에 약 %,d원,".format(fee)
        }
        return "${top.name}${subject(top.name)} 가장 저렴해요. $cost 걸어서 ${top.walkMinutes}분이에요."
    }

    /** 받침 유무로 이/가. 한글로 끝나지 않으면(숫자·영문) '이'. */
    private fun subject(word: String): String {
        val c = word.lastOrNull { !it.isWhitespace() } ?: return "이"
        if (c !in '가'..'힣') return "이"
        return if ((c - '가') % 28 == 0) "가" else "이"
    }
}
