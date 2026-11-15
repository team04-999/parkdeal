package com.parkdeal.core

import com.parkdeal.core.api.ApiJson
import com.parkdeal.core.api.Benefit
import com.parkdeal.core.api.DestinationResponse
import com.parkdeal.core.api.Fee
import com.parkdeal.core.api.Lot
import com.parkdeal.core.api.LotsResponse
import com.parkdeal.core.api.Source
import com.parkdeal.core.api.Store
import com.parkdeal.core.api.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommenderTest {
    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/api/$name")).readText()

    private fun fee(first: Int, firstFee: Int, unit: Int, unitFee: Int) =
        Fee(first, firstFee, unit, unitFee, null, null, "store_fee", "2026-11-11T00:00:00Z")

    private fun lot(id: Int, name: String, distanceM: Int, fee: Fee?) =
        Lot(id = id, name = name, kind = "building", lat = 37.5, lng = 127.0, fee = fee, distanceM = distanceM)

    private val store = Store("1", "아마존바베큐", "고기", 37.5, 127.0, 90, Source("네이버 지도", "https://map.naver.com/p/entry/place/1"))

    @Test fun `실제 서버 응답 - 가게 혜택 주차장이 무료로 1순위`() {
        val r = Recommender.recommend(
            ApiJson.destination(fixture("destination-joy.json")),
            ApiJson.lots(fixture("lots-joy.json")),
            stayMin = 60,
        )
        val top = r.options.first()
        assertEquals("남부순환로 1738 주차장", top.name)
        assertEquals(0, top.fee)
        assertEquals(60, top.freeMin)
        assertEquals("조이치과의원 봉천 이용 시 1시간 무료", top.reason)
    }

    @Test fun `같은 주차장은 혜택 쪽 하나만 남긴다`() {
        val r = Recommender.recommend(
            ApiJson.destination(fixture("destination-joy.json")),
            ApiJson.lots(fixture("lots-joy.json")),
            stayMin = 60,
        )
        assertEquals(r.options.map { it.lotId }.distinct(), r.options.map { it.lotId })
    }

    @Test fun `무료 시간을 넘기면 넘긴 만큼만 낸다`() {
        val l = lot(1, "커먼그라운드", 100, fee(30, 2000, 30, 2000))
        val dest = DestinationResponse(store, listOf(Benefit(id = 1, type = "all_free_min", freeMin = 120, lot = l)))
        // 150분: 전체 10000 - 120분분 8000 = 2000
        assertEquals(2000, Recommender.recommend(dest, LotsResponse(listOf(l)), 150).options.single().fee)
    }

    @Test fun `혜택 없는 주차장은 요금과 거리로 정렬하고 요금 모르는 곳은 뒤로`() {
        val cheapFar = lot(1, "싸고 멂", 700, fee(60, 1000, 60, 1000))
        val priceyNear = lot(2, "비싸고 가까움", 50, fee(30, 3000, 30, 3000))
        val unknown = lot(3, "요금 모름", 10, null)
        val r = Recommender.recommend(DestinationResponse(), LotsResponse(listOf(unknown, priceyNear, cheapFar)), 60)
        // 싸고 멂: 1000 + 700*1.2*5=4200 → 5200 / 비싸고 가까움: 6000 + 300 = 6300
        assertEquals(listOf("싸고 멂", "비싸고 가까움", "요금 모름"), r.options.map { it.name })
        assertNull(r.options.last().fee)
    }

    @Test fun `조건부 혜택은 조건 없는 무료 시간이 없으면 가장 낮은 금액 조건을 쓴다`() {
        val l = lot(1, "타워", 0, fee(30, 2000, 30, 2000))
        val b = Benefit(id = 1, type = "conditional", condition = "meal", tiers = listOf(Tier(30000, 120), Tier(50000, 180)), lot = l)
        val top = Recommender.recommend(DestinationResponse(store, listOf(b)), LotsResponse(emptyList()), 90).options.single()
        assertEquals(120, top.freeMin)
        assertEquals("아마존바베큐 3만원 이상 식사 시 2시간 무료", top.reason)
        assertEquals(0, top.fee)
    }

    @Test fun `전액 무료 혜택은 요금 규칙이 없어도 0원`() {
        val l = lot(1, "가게 건물", 0, null)
        val top = Recommender.recommend(DestinationResponse(store, listOf(Benefit(id = 1, type = "free", lot = l))), LotsResponse(emptyList()), 90).options.single()
        assertEquals(0, top.fee)
        assertEquals("아마존바베큐 이용 시 무료", top.reason)
    }

    @Test fun `무료가 아닌 안내형 혜택은 가게 지정 주차장이라고만 표시한다`() {
        val l = lot(1, "지정 주차장", 0, fee(30, 2000, 30, 2000))
        val top = Recommender.recommend(DestinationResponse(store, listOf(Benefit(id = 1, type = "paid", lot = l))), LotsResponse(emptyList()), 60).options.single()
        assertEquals(0, top.freeMin)
        assertEquals(4000, top.fee)
        assertEquals("아마존바베큐 안내 주차장", top.reason)
    }

    @Test fun `도보는 직선거리의 1점2배를 분당 67m 로 어림한다`() {
        val top = Recommender.recommend(DestinationResponse(), LotsResponse(listOf(lot(1, "a", 500, fee(60, 0, 60, 0)))), 60).options.single()
        assertEquals(600, top.walkMeters)
        assertEquals(9, top.walkMinutes)
    }

    @Test fun `주차장이 하나도 없으면 빈 추천`() {
        assertTrue(Recommender.recommend(DestinationResponse(), LotsResponse(emptyList()), 60).options.isEmpty())
    }

    @Test fun `전기차가 못 들어가는 주차장은 더 싸도 맨 뒤로 보내고 이유를 단다`() {
        val cheapNoEv = Lot(id = 1, name = "기계식", kind = "building", lat = 37.5, lng = 127.0, fee = fee(30, 500, 30, 500), distanceM = 50, alerts = listOf("ev"))
        val normal = lot(2, "일반", 300, fee(30, 3000, 30, 3000))
        val dest = DestinationResponse(store, emptyList())
        val r = Recommender.recommend(dest, LotsResponse(listOf(cheapNoEv, normal)), 60, Vehicle(ev = true))
        assertEquals(listOf("일반", "기계식"), r.options.map { it.name })
        assertTrue(r.options.last().blocked)
        assertEquals("전기차 불가", r.options.last().notes.first())
    }

    @Test fun `혜택 주차장에는 받는 법이 붙는다`() {
        val b = Benefit(id = 9, type = "all_free_min", condition = "meal", redeem = "plate", freeMin = 120, maxCars = 1,
            lot = lot(3, "아크플레이스", 10, fee(30, 6000, 60, 6000)))
        val r = Recommender.recommend(DestinationResponse(store, listOf(b)), LotsResponse(emptyList()), 90)
        val how = r.options.first().how!!
        assertEquals("카운터에 차량번호를 알려 주세요", how.redeem)
        assertEquals(listOf("1대까지"), how.limits)
    }

    @Test fun `목적지 혜택이 없는 주차장에 다른 가게 혜택이 있으면 따로 붙인다 - 요금 순서는 일반 요금 기준`() {
        val l = lot(5, "자양호반써밋", 400, fee(60, 1000, 60, 1000)).copy(
            deals = listOf(com.parkdeal.core.api.Deal(storeId = "7", storeName = "은비스브레드", allFree = true, type = "free")),
        )
        val r = Recommender.recommend(DestinationResponse(store, emptyList()), LotsResponse(listOf(l)), 120)
        val o = r.options.single()
        assertEquals(2000, o.fee)
        assertEquals("은비스브레드 이용 시 무료", o.other!!.reason)
        assertEquals(0, o.other!!.fee)
    }

    @Test fun `다른 가게 혜택 중 이 체류 시간에 가장 싸지는 것을 고른다`() {
        val l = lot(6, "B", 100, fee(60, 1000, 60, 1000)).copy(
            deals = listOf(
                com.parkdeal.core.api.Deal(storeId = "1", storeName = "카페", type = "all_free_min", condition = "purchase", freeMin = 60),
                com.parkdeal.core.api.Deal(storeId = "2", storeName = "식당", type = "all_free_min", condition = "meal", freeMin = 120),
            ),
        )
        val o = Recommender.recommend(DestinationResponse(store, emptyList()), LotsResponse(listOf(l)), 180).options.single()
        assertEquals("식당 식사 시 2시간 무료", o.other!!.reason)
        assertEquals(1000, o.other!!.fee)
    }

    @Test fun `일반 요금을 몰라도 다른 가게 혜택으로 무료면 요금 모름보다 앞에 둔다`() {
        val unknown = lot(1, "요금모름", 50, null)
        val freeByDeal = lot(2, "혜택있음", 300, null).copy(
            deals = listOf(com.parkdeal.core.api.Deal(storeId = "3", storeName = "임팩트", allFree = true, type = "free")))
        val paid = lot(3, "유료", 100, fee(60, 5000, 60, 5000))
        val r = Recommender.recommend(DestinationResponse(store, emptyList()), LotsResponse(listOf(unknown, freeByDeal, paid)), 60)
        assertEquals(listOf("혜택있음", "유료", "요금모름"), r.options.map { it.name })
    }

    @Test fun `이름이 같은 주차장이 둘이면 더 나은 쪽 하나만 남긴다`() {
        val a = lot(1, "남부순환로181길 41 주차장", 30, null)
        val b = lot(2, "남부순환로181길 41 주차장", 150, fee(60, 2000, 60, 2000))
        val c = lot(3, "다른 주차장", 80, fee(60, 3000, 60, 3000))
        val r = Recommender.recommend(DestinationResponse(store, emptyList()), LotsResponse(listOf(a, b, c)), 60)
        assertEquals(listOf("남부순환로181길 41 주차장", "다른 주차장"), r.options.map { it.name })
        assertEquals(2, r.options.first().lotId) // 요금을 아는 쪽이 더 낫다
    }

    @Test fun `후보마다 일반 요금과 혜택 가게 전체 목록(목적지 가게 먼저)`() {
        val l = lot(5, "자양호반써밋", 400, fee(60, 1000, 60, 1000)).copy(deals = listOf(
            com.parkdeal.core.api.Deal(storeId = "7", storeName = "은비스브레드", allFree = true, type = "free"),
            com.parkdeal.core.api.Deal(storeId = "8", storeName = "커스텀커피", type = "all_free_min", condition = "purchase", freeMin = 60),
        ))
        val o = Recommender.recommend(DestinationResponse(store, emptyList()), LotsResponse(listOf(l)), 120).options.single()
        assertEquals(2000, o.normalFee)
        assertEquals(listOf("은비스브레드", "커스텀커피"), o.deals.map { it.storeName })
        assertEquals(listOf(0, 1000), o.deals.map { it.fee })
    }
}
