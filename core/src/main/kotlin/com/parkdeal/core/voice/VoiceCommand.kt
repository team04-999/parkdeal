package com.parkdeal.core.voice

/**
 * 운전자가 말한 문장(SpeechToText 결과)을 앱이 할 일로 바꾼다.
 * 대화형 AI 가 아니라 정해진 몇 가지 말만 알아듣는다. 모르면 [Unknown] 으로 돌려 예시를 안내한다.
 */
sealed interface VoiceCommand {
    /** "어디 세우면 돼?" — 1순위 브리핑 */
    data object Recommend : VoiceCommand
    /** "2번으로" — [rank]번(1부터) 주차장을 경유지로 */
    data class Pick(val rank: Int) : VoiceCommand
    /** "다른 데는?" — 다음 후보 */
    data object Next : VoiceCommand
    /** "지금 얼마야?" — 주차 중이면 경과·요금, 아니면 고른 곳 요금 */
    data object Status : VoiceCommand
    /** 출차 확인에 대한 대답 */
    data class Registered(val yes: Boolean) : VoiceCommand
    data object Unknown : VoiceCommand

    companion object {
        private val ORDINAL = listOf("첫" to 1, "한" to 1, "두" to 2, "둘" to 2, "세" to 3, "셋" to 3, "네" to 4, "넷" to 4, "다섯" to 5)
        // STT 가 숫자를 한글로 적을 때: 일번·이번·삼번…
        private val SINO = listOf("일번" to 1, "이번" to 2, "삼번" to 3, "사번" to 4, "오번" to 5)

        fun parse(text: String): VoiceCommand {
            val t = text.replace(" ", "")
            if (t.isEmpty()) return Unknown
            if (Regex("못했|안했|아직|아니").containsMatchIn(t) && Regex("등록|했").containsMatchIn(t)) return Registered(false)
            if (t.contains("등록했") || t == "응" || t.startsWith("네했")) return Registered(true)
            Regex("([1-9])번").find(t)?.let { return Pick(it.groupValues[1].toInt()) }
            SINO.firstOrNull { t.contains(it.first) }?.let { return Pick(it.second) }
            ORDINAL.firstOrNull { t.contains(it.first + "번째") }?.let { return Pick(it.second) }
            if (Regex("다른|다음|말고").containsMatchIn(t)) return Next
            if (Regex("얼마|요금|남았|몇분|몇시간").containsMatchIn(t)) return Status
            if (Regex("주차|어디|싼|추천|세우").containsMatchIn(t)) return Recommend
            return Unknown
        }
    }
}
