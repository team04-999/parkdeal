package com.parkdeal.app

import com.parkdeal.core.api.ApiJson
import com.parkdeal.core.api.ApiRequest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 데모 목적지마다 내장 스냅샷이 core 의 캐시 키 규칙대로 들어 있는지 본다.
 * scripts/snapshot_api.py 가 같은 규칙으로 파일 이름을 만드는데, 두 규칙이 어긋나면
 * 오프라인에서 스냅샷을 못 찾는다 — 그 어긋남을 여기서 잡는다.
 */
class SnapshotAssetsTest {
    private val dir = File("src/main/assets/api-snapshot")

    @Test fun `데모 목적지마다 목적지와 주차장 스냅샷이 있고 파싱된다`() {
        assertTrue("데모 목적지가 하나 이상 있어야 한다", DemoPlaces.all.isNotEmpty())
        for (place in DemoPlaces.all) {
            val dest = File(dir, place.destinationRequest.cacheFile)
            val lots = File(dir, place.lotsRequest.cacheFile)
            assertTrue("${place.name} 목적지 스냅샷 없음: ${dest.name}", dest.exists())
            assertTrue("${place.name} 주차장 스냅샷 없음: ${lots.name}", lots.exists())
            assertNotNull(ApiJson.destination(dest.readText()).store)
            ApiJson.lots(lots.readText())
        }
    }
}
