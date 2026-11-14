package com.parkdeal.core.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class ParkingRepositoryTest {
    private val joy = requireNotNull(javaClass.getResource("/api/destination-joy.json")).readText()
    private val req = ApiRequest.Destination(37.482052, 126.942983, "조이치과")

    private class MapStore(val map: MutableMap<String, String> = mutableMapOf()) : ResponseCache {
        override fun read(key: String) = map[key]
        override fun write(key: String, body: String) { map[key] = body }
    }

    private class Transport(val body: String? = null, val error: Exception? = null) : HttpTransport {
        var calls = 0
        override fun get(path: String): String {
            calls++
            error?.let { throw it }
            return requireNotNull(body)
        }
    }

    @Test fun `서버가 응답하면 서버 값을 쓰고 캐시에 남긴다`() {
        val cache = MapStore()
        val got = ParkingRepository(Transport(joy), cache, MapStore()).destination(req)
        assertEquals(Origin.SERVER, got?.origin)
        assertEquals("조이치과의원 봉천", got?.value?.store?.name)
        assertEquals(joy, cache.map[req.cacheFile])
    }

    @Test fun `서버가 실패하면 캐시를 쓴다`() {
        val cache = MapStore(mutableMapOf(req.cacheFile to joy))
        val got = ParkingRepository(Transport(error = IOException("timeout")), cache, MapStore()).destination(req)
        assertEquals(Origin.CACHE, got?.origin)
        assertEquals("11482007", got?.value?.store?.id)
    }

    @Test fun `캐시도 없으면 내장 스냅샷을 쓴다`() {
        val snapshot = MapStore(mutableMapOf(req.cacheFile to joy))
        val got = ParkingRepository(Transport(error = IOException("offline")), MapStore(), snapshot).destination(req)
        assertEquals(Origin.SNAPSHOT, got?.origin)
    }

    @Test fun `아무것도 없으면 null`() {
        assertNull(ParkingRepository(Transport(error = IOException("offline")), MapStore(), MapStore()).destination(req))
    }

    @Test fun `서버 응답 형식이 깨지면 캐시에 남기지 않고 다음 단계로 간다`() {
        val cache = MapStore(mutableMapOf(req.cacheFile to joy))
        val got = ParkingRepository(Transport("<html>502</html>"), cache, MapStore()).destination(req)
        assertEquals(Origin.CACHE, got?.origin)
        assertEquals(joy, cache.map[req.cacheFile])
    }

    @Test fun `캐시 내용이 깨졌으면 건너뛰고 스냅샷으로 간다`() {
        val cache = MapStore(mutableMapOf(req.cacheFile to "{broken"))
        val snapshot = MapStore(mutableMapOf(req.cacheFile to joy))
        val got = ParkingRepository(Transport(error = IOException("offline")), cache, snapshot).destination(req)
        assertEquals(Origin.SNAPSHOT, got?.origin)
    }

    @Test fun `캐시 쓰기가 실패해도 서버 값은 돌려준다`() {
        val failing = object : ResponseCache {
            override fun read(key: String): String? = null
            override fun write(key: String, body: String) = throw IOException("disk full")
        }
        val got = ParkingRepository(Transport(joy), failing, MapStore()).destination(req)
        assertEquals(Origin.SERVER, got?.origin)
    }
}
