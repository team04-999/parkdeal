package com.parkdeal.core

import com.parkdeal.core.api.ApiJson
import org.junit.Assert.assertEquals
import org.junit.Test

class BriefingTest {
    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/api/$name")).readText()

    @Test fun `가게 혜택이 1순위면 혜택을 말한다`() {
        val r = Recommender.recommend(ApiJson.destination(fixture("destination-joy.json")), ApiJson.lots(fixture("lots-joy.json")), 60)
        assertEquals("조이치과의원 봉천 이용 시 남부순환로 1738 주차장이 1시간 무료예요.", Briefing.sentence(r, 60))
    }

    @Test fun `혜택이 없으면 가장 저렴한 곳과 요금과 도보를 말한다`() {
        val r = Recommender.recommend(ApiJson.destination(fixture("destination-none.json")), ApiJson.lots(fixture("lots-joy.json")), 60)
        assertEquals("남부순환로 1738 주차장이 가장 저렴해요. 1시간 무료, 걸어서 1분이에요.", Briefing.sentence(r, 60))
    }

    @Test fun `추천할 곳이 없으면 그렇게 말한다`() {
        val r = Recommender.recommend(ApiJson.destination(fixture("destination-none.json")), ApiJson.lots("""{"lots":[]}"""), 60)
        assertEquals("근처에서 추천할 주차장을 찾지 못했어요.", Briefing.sentence(r, 60))
    }
}
