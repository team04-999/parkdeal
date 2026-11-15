package com.parkdeal.core.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCommandTest {
    private fun p(s: String) = VoiceCommand.parse(s)

    @Test fun `추천을 묻는 말`() {
        assertEquals(VoiceCommand.Recommend, p("근처 주차 어디가 좋아?"))
        assertEquals(VoiceCommand.Recommend, p("제일 싼 데 어디야"))
        assertEquals(VoiceCommand.Recommend, p("주차장 추천해 줘"))
    }

    @Test fun `번호로 고르기 - 숫자와 한글 서수`() {
        assertEquals(VoiceCommand.Pick(2), p("2번으로 가 줘"))
        assertEquals(VoiceCommand.Pick(3), p("세 번째 거"))
        assertEquals(VoiceCommand.Pick(1), p("첫 번째로 안내해"))
        assertEquals(VoiceCommand.Pick(2), p("이번으로"))
    }

    @Test fun `다른 후보`() {
        assertEquals(VoiceCommand.Next, p("다른 데는?"))
        assertEquals(VoiceCommand.Next, p("다음 후보"))
    }

    @Test fun `요금과 상태를 묻는 말`() {
        assertEquals(VoiceCommand.Status, p("지금 나가면 얼마야"))
        assertEquals(VoiceCommand.Status, p("주차 요금 얼마 나왔어"))
        assertEquals(VoiceCommand.Status, p("무료 시간 얼마나 남았어"))
    }

    @Test fun `등록 답하기`() {
        assertEquals(VoiceCommand.Registered(true), p("응 등록했어"))
        assertEquals(VoiceCommand.Registered(false), p("아니 못 했어"))
        assertEquals(VoiceCommand.Registered(false), p("등록 안 했어"))
    }

    @Test fun `모르는 말`() {
        assertEquals(VoiceCommand.Unknown, p("오늘 날씨 어때"))
        assertEquals(VoiceCommand.Unknown, p(""))
    }
}
