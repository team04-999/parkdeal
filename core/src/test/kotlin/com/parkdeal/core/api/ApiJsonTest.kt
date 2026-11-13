package com.parkdeal.core.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiJsonTest {
    private fun fixture(name: String) =
        requireNotNull(javaClass.getResource("/api/$name")) { name }.readText()

    @Test fun `목적지 응답에서 가게와 혜택과 연결 주차장 요금을 읽는다`() {
        val d = ApiJson.destination(fixture("destination-joy.json"))
        val store = requireNotNull(d.store)
        assertEquals("11482007", store.id)
        assertEquals("조이치과의원 봉천", store.name)
        assertEquals(60, store.stayMin)
        assertEquals("네이버 지도", store.source.name)

        val b = d.benefits.single()
        assertEquals("all_free_min", b.type)
        assertEquals(60, b.freeMin)
        val fee = requireNotNull(b.lot?.fee)
        assertEquals(60, fee.firstMin)
        assertEquals(0, fee.firstFee)
        assertEquals(30, fee.unitMin)
        assertEquals(2000, fee.unitFee)
    }

    @Test fun `맞는 가게가 없으면 store 는 null 이고 혜택은 비었다`() {
        val d = ApiJson.destination(fixture("destination-none.json"))
        assertNull(d.store)
        assertTrue(d.benefits.isEmpty())
    }

    @Test fun `주차장 목록은 요금이 없을 수 있다`() {
        val lots = ApiJson.lots(fixture("lots-joy.json")).lots
        assertEquals(3, lots.size)
        assertEquals(0, lots[0].distanceM)
        assertNull(lots[1].fee)
    }

    @Test fun `금액 조건 tier 와 유효 시각을 읽고 모르는 필드는 무시한다`() {
        val json = """
            {"store":null,"benefits":[{"id":1,"type":"conditional","condition":"meal","redeem":"receipt_stamp",
             "freeMin":null,"tiers":[{"minSpend":30000,"freeMin":120}],"validDays":"월-금","validFrom":"10:00",
             "validTo":"22:30","maxCars":null,"stackable":null,"needsReview":true,"lot":null,"newField":"미래"}],
             "extra":1}
        """.trimIndent()
        val b = ApiJson.destination(json).benefits.single()
        assertEquals(30000, b.tiers.single().minSpend)
        assertEquals(120, b.tiers.single().freeMin)
        assertEquals("10:00", b.validFrom)
        assertTrue(b.needsReview)
        assertNull(b.lot)
    }

    @Test(expected = ApiFormatException::class)
    fun `형식이 깨진 응답은 ApiFormatException`() {
        ApiJson.destination("<html>502 Bad Gateway</html>")
    }
}
