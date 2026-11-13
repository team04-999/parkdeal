package com.parkdeal.app

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.TextView
import com.parkdeal.app.data.AssetSnapshot
import com.parkdeal.app.data.FileResponseCache
import com.parkdeal.app.data.HttpUrlTransport
import com.parkdeal.core.api.DestinationResponse
import com.parkdeal.core.api.Fetched
import com.parkdeal.core.api.LotsResponse
import com.parkdeal.core.api.Origin
import com.parkdeal.core.api.ParkingRepository
import com.parkdeal.core.durationText
import java.io.File
import java.util.concurrent.Executors

/**
 * 임시 화면. 데모 목적지의 주차 데이터를 서버 → 캐시 → 내장 스냅샷 순서로 불러와
 * 어디서 받았는지와 함께 보여준다. 내비 목적지·추천 화면은 다음 PR 에서 붙인다.
 */
class MainActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var text: TextView

    private val repository by lazy {
        ParkingRepository(
            transport = HttpUrlTransport(BuildConfig.PARKDEAL_API_BASE, "ParkDeal/${BuildConfig.VERSION_NAME} (Android)"),
            cache = FileResponseCache(File(filesDir, "api-cache")),
            snapshot = AssetSnapshot(assets),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        text = TextView(this).apply {
            textSize = 32f
            gravity = Gravity.CENTER
            text = "주차 정보를 불러오는 중…"
        }
        setContentView(text)
    }

    override fun onStart() {
        super.onStart()
        val place = DemoPlaces.all.first()
        io.execute {
            val dest = repository.destination(place.destinationRequest)
            val lots = repository.lots(place.lotsRequest)
            main.post { if (!isDestroyed) text.text = render(place.name, dest, lots) }
        }
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }

    private fun render(name: String, dest: Fetched<DestinationResponse>?, lots: Fetched<LotsResponse>?): String {
        if (dest == null && lots == null) return "$name 주변 주차 정보를 불러오지 못했어요"
        val lines = mutableListOf<String>()
        val store = dest?.value?.store
        if (store != null) {
            lines += "${store.name} · 예상 체류 ${durationText(store.stayMin)}"
            dest.value.benefits.firstOrNull { it.freeMin != null }?.let { b ->
                lines += "주차 혜택: ${durationText(b.freeMin!!)} 무료" + (b.lot?.let { " (${it.name})" } ?: "")
            }
        } else {
            lines += "$name: 가게 주차 혜택 정보 없음"
        }
        lots?.value?.lots?.let { list ->
            lines += "주변 주차장 ${list.size}곳 · 요금 정보 ${list.count { it.fee != null }}곳"
        }
        lines += ""
        lines += "정보 출처: ${store?.source?.name ?: "네이버 지도"} · ${originText(dest?.origin ?: lots?.origin)}"
        return lines.joinToString("\n")
    }

    private fun originText(origin: Origin?) = when (origin) {
        Origin.SERVER -> "실시간"
        Origin.CACHE -> "저장된 정보 (서버 연결 안 됨)"
        Origin.SNAPSHOT -> "내장 데이터 (오프라인)"
        null -> "-"
    }
}
