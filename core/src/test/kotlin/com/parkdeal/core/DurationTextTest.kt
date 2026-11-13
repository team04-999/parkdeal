package com.parkdeal.core

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationTextTest {
    @Test fun `1시간 미만은 분으로 쓰고 0분은 0시간이 아니다`() {
        assertEquals("0분", durationText(0))
        assertEquals("45분", durationText(45))
    }

    @Test fun `정시는 시간만 쓴다`() {
        assertEquals("2시간", durationText(120))
    }

    @Test fun `시간과 분을 함께 쓴다`() {
        assertEquals("1시간 30분", durationText(90))
    }
}
