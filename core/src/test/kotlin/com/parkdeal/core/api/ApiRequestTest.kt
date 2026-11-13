package com.parkdeal.core.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ApiRequestTest {
    @Test fun `목적지 요청은 좌표를 소수 넷째 자리로 맞추고 이름을 인코딩한다`() {
        val r = ApiRequest.Destination(37.4820519, 126.9429834, "조이 치과")
        assertEquals(
            "/v1/destination?lat=37.4821&lng=126.9430&name=%EC%A1%B0%EC%9D%B4+%EC%B9%98%EA%B3%BC",
            r.path,
        )
    }

    @Test fun `이름이 없으면 name 을 보내지 않는다`() {
        assertEquals("/v1/destination?lat=37.5000&lng=127.0000", ApiRequest.Destination(37.5, 127.0, null).path)
        assertEquals("/v1/destination?lat=37.5000&lng=127.0000", ApiRequest.Destination(37.5, 127.0, "  ").path)
    }

    @Test fun `주차장 요청은 반경을 함께 보낸다`() {
        assertEquals("/v1/lots?lat=37.5000&lng=127.0000&radius=800", ApiRequest.Lots(37.5, 127.0).path)
    }

    @Test fun `같은 목적지는 좌표가 조금 달라도 같은 캐시 키`() {
        val a = ApiRequest.Destination(37.48205, 126.94298, "조이치과")
        val b = ApiRequest.Destination(37.482052, 126.942983, "조이 치과")
        assertEquals(a.cacheKey, b.cacheKey)
        assertEquals(a.cacheFile, b.cacheFile)
    }

    @Test fun `목적지와 주차장 요청은 캐시 키가 겹치지 않는다`() {
        assertNotEquals(ApiRequest.Destination(37.5, 127.0, null).cacheKey, ApiRequest.Lots(37.5, 127.0).cacheKey)
    }

    @Test fun `캐시 파일 이름은 파일 시스템에 안전하다`() {
        val f = ApiRequest.Destination(37.5, 127.0, "가/게\\이름:*").cacheFile
        assertEquals(true, Regex("^[0-9a-f]{16}\\.json$").matches(f))
    }
}
