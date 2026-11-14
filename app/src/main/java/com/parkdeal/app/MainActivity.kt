package com.parkdeal.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.TextView
import com.parkdeal.app.data.AssetSnapshot
import com.parkdeal.app.data.FileResponseCache
import com.parkdeal.app.data.HttpUrlTransport
import com.parkdeal.app.parking.Parking
import com.parkdeal.app.parking.ParkingService
import com.parkdeal.core.Briefing
import com.parkdeal.core.Recommender
import com.parkdeal.core.api.LotsResponse
import com.parkdeal.core.api.Origin
import com.parkdeal.core.api.ParkingRepository
import com.parkdeal.core.durationText
import com.parkdeal.core.parking.Candidate
import com.parkdeal.core.parking.ParkingState
import com.parkdeal.core.parking.ParkingTalk
import java.io.File
import java.util.concurrent.Executors

/**
 * 임시 화면. 데모 목적지의 추천 1순위를 "P단에 넣으면 주차할 곳"으로 정하고,
 * 주차 상태(대기 → 주차 중 → 출차 리마인드)를 글자로 보여준다. 화면 디자인은 다음 PR.
 */
class MainActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var text: TextView
    private var plan = "주차 정보를 불러오는 중…"
    private val tracker by lazy { Parking.tracker(this) }
    private val onState: (ParkingState) -> Unit = { render() }

    private val repository by lazy {
        ParkingRepository(
            transport = HttpUrlTransport(BuildConfig.PARKDEAL_API_BASE, "ParkDeal/${BuildConfig.VERSION_NAME} (Android)"),
            cache = FileResponseCache(File(filesDir, "api-cache")),
            snapshot = AssetSnapshot(assets),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        text = TextView(this).apply { textSize = 28f; gravity = Gravity.CENTER; setPadding(48, 48, 48, 48) }
        setContentView(text)
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onStart() {
        super.onStart()
        Parking.observe(onState)
        render()
        // 주차 중이면 서비스를 다시 띄운다 (프로세스가 다시 만들어진 경우)
        ParkingService.ensure(this)
        val place = DemoPlaces.all.first()
        io.execute {
            val dest = repository.destination(place.destinationRequest)
            val lots = repository.lots(place.lotsRequest)
            main.post {
                if (isDestroyed) return@post
                if (dest == null) { plan = "${place.name} 주변 주차 정보를 불러오지 못했어요"; render(); return@post }
                val stay = dest.value.store?.stayMin ?: 60
                val r = Recommender.recommend(dest.value, lots?.value ?: LotsResponse(emptyList()), stay)
                val top = r.options.firstOrNull()
                if (top != null && tracker.state.session == null) {
                    tracker.candidate = Candidate(top, r.storeName, place.name)
                    ParkingService.ensure(this)
                }
                plan = Briefing.sentence(r, stay) + "\n\n정보 출처: 네이버 지도 · " + originText(dest.origin)
                render()
            }
        }
    }

    override fun onStop() {
        Parking.unobserve(onState)
        super.onStop()
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }

    private fun render() {
        val now = System.currentTimeMillis()
        val st = tracker.state
        text.text = when {
            st.session != null -> "주차 중\n\n" + ParkingTalk.status(st.session!!, now)
            st.reminder != null -> "출차\n\n" + ParkingTalk.exitReminder(st.reminder!!) +
                "\n(${durationText(st.reminder!!.session.elapsedMin(st.reminder!!.endedAt))} 주차)"
            else -> plan + (tracker.candidate?.let { "\n\nP단에 넣으면 ${it.option.name} 주차를 시작해요" } ?: "")
        }
    }

    private fun originText(origin: Origin) = when (origin) {
        Origin.SERVER -> "실시간"
        Origin.CACHE -> "저장된 정보 (서버 연결 안 됨)"
        Origin.SNAPSHOT -> "내장 데이터 (오프라인)"
    }
}
