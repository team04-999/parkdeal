package com.parkdeal.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.parkdeal.app.data.AssetSnapshot
import com.parkdeal.app.data.FileResponseCache
import com.parkdeal.app.data.HttpUrlTransport
import com.parkdeal.app.navi.NaviSource
import com.parkdeal.app.parking.Parking
import com.parkdeal.app.parking.ParkingService
import com.parkdeal.app.parking.Voice
import com.parkdeal.app.ui.MiniMapView
import com.parkdeal.app.ui.Ui
import com.parkdeal.app.ui.Ui.MATCH
import com.parkdeal.app.ui.Ui.WRAP
import com.parkdeal.app.ui.Ui.dp
import com.parkdeal.app.ui.Ui.lp
import com.parkdeal.core.Briefing
import com.parkdeal.core.ParkingOption
import com.parkdeal.core.Recommendation
import com.parkdeal.core.Recommender
import com.parkdeal.core.api.DestinationResponse
import com.parkdeal.core.api.LotsResponse
import com.parkdeal.core.api.ApiRequest
import com.parkdeal.core.api.Origin
import com.parkdeal.core.navi.DestinationFollower
import com.parkdeal.core.navi.Place
import com.parkdeal.core.api.ParkingRepository
import com.parkdeal.core.durationText
import com.parkdeal.core.parking.Candidate
import com.parkdeal.core.parking.ExitReminder
import com.parkdeal.core.parking.ParkingSession
import com.parkdeal.core.parking.ParkingState
import com.parkdeal.core.parking.ParkingTalk
import java.io.File
import java.util.concurrent.Executors

/**
 * 정차 후 쓰는 화면. 주행 중에는 시스템이 이 화면을 가리고, 안내는 TTS·알림으로 한다.
 *  - 주차 비교: 목적지 주변 주차장을 "실제 낼 돈 + 걷는 거리"로 정렬
 *  - 주차 중: 경과 시간, 남은 무료 시간, 지금 나가면 낼 돈
 *  - 출차 리마인드: 가게 혜택 등록 확인
 * 목적지는 지금은 데모 목적지로 고정한다.
 */
class MainActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var content: FrameLayout
    private val tracker by lazy { Parking.tracker(this) }
    private val voice by lazy { Voice(this) }
    private val onState: (ParkingState) -> Unit = { render() }
    private val tick = object : Runnable {
        override fun run() { render(); main.postDelayed(this, 30_000) }
    }

    /** 지금 추천을 만드는 목적지. 내비 목적지가 오면 바뀐다. 내비가 없으면 첫 데모 목적지. */
    private var place: Place = DemoPlaces.all.first().let { Place(it.name, it.lat, it.lng) }
    private val follower = DestinationFollower()
    private val navi by lazy { NaviSource(this, ::onNaviDestination) }
    /** 내비 목적지로 추천을 만들었으면 불러온 뒤 한 번 브리핑한다. */
    private var briefPending = false
    private var dest: DestinationResponse? = null
    private var lots: LotsResponse? = null
    private var origin: Origin? = null
    private var failed = false
    private var stayMin = 60
    private var index = 0
    private var recommendation: Recommendation? = null

    private val repository by lazy {
        ParkingRepository(
            transport = HttpUrlTransport(BuildConfig.PARKDEAL_API_BASE, "ParkDeal/${BuildConfig.VERSION_NAME} (Android)"),
            cache = FileResponseCache(File(filesDir, "api-cache")),
            snapshot = AssetSnapshot(assets),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        content = FrameLayout(this).apply { setBackgroundColor(Ui.BG) }
        setContentView(content)
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        voice // TTS 엔진을 미리 띄워 첫 안내가 늦지 않게
        load()
        navi.start()
        demo(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        demo(intent)
    }

    /**
     * 시연 영상용: `am start ... --ei skipMin 90` 이면 진행 중 주차를 90분 앞당긴다.
     * 이 액티비티는 런처라 외부에 열려 있으므로 debug 빌드에서만 받고, 한 번 쓰면 지운다.
     */
    private fun demo(intent: Intent?) {
        if (!BuildConfig.DEBUG || intent == null) return
        if (intent.hasExtra(EXTRA_SKIP_MIN)) {
            val skip = intent.getIntExtra(EXTRA_SKIP_MIN, 0)
            intent.removeExtra(EXTRA_SKIP_MIN)
            tracker.backdate(skip)
        }
        // `--es dest 필앤필버거`: 데모 목적지로 내비 경로를 건다 (내비에서 목적지를 검색하는 대신)
        intent.getStringExtra(EXTRA_DEST)?.let { name ->
            intent.removeExtra(EXTRA_DEST)
            DemoPlaces.all.firstOrNull { it.name == name }?.let { navi.route(Place(it.name, it.lat, it.lng)) }
        }
    }

    /** 내비 목적지가 바뀌면 그 목적지로 추천을 다시 만들고, 불러오면 음성으로 브리핑한다. */
    private fun onNaviDestination(p: Place, remainMeters: Int) {
        val next = follower.onDestination(p) ?: return
        Log.d(TAG, "navi destination ${next.name} remain=${remainMeters}m")
        place = next
        index = 0
        dest = null; lots = null; recommendation = null; failed = false
        briefPending = true
        render()
        load()
    }

    /** 고른 주차장으로 내비 경로를 바꾼다. 목적지(가게)는 그대로 두고 추천도 유지한다. */
    private fun guide(o: ParkingOption) {
        val lat = o.lat; val lng = o.lng
        if (lat == null || lng == null) return
        follower.guidingTo(o.name)
        if (navi.route(Place(o.name, lat, lng))) {
            voice.speak("${o.name}(으)로 안내할게요.".replace("(으)로", if (endsWithBatchim(o.name)) "으로" else "로"))
        }
    }

    private fun endsWithBatchim(word: String): Boolean {
        val c = word.lastOrNull { !it.isWhitespace() } ?: return false
        return c in '가'..'힣' && (c - '가') % 28 != 0 && (c - '가') % 28 != 8 // ㄹ 받침은 "로"
    }

    override fun onStart() {
        super.onStart()
        Parking.observe(onState)
        main.post(tick)
        // 주차 중이면 서비스를 다시 띄운다 (프로세스가 다시 만들어진 경우)
        ParkingService.ensure(this)
    }

    override fun onStop() {
        Parking.unobserve(onState)
        main.removeCallbacks(tick)
        super.onStop()
    }

    override fun onDestroy() {
        io.shutdownNow()
        navi.stop()
        voice.close()
        super.onDestroy()
    }

    private fun load() {
        val p = place
        io.execute {
            val d = repository.destination(ApiRequest.Destination(p.lat, p.lng, p.name))
            val l = repository.lots(ApiRequest.Lots(p.lat, p.lng))
            main.post {
                if (isDestroyed || p != place) return@post // 그사이 목적지가 바뀌었다
                failed = d == null
                dest = d?.value
                lots = l?.value
                origin = d?.origin ?: l?.origin
                stayMin = d?.value?.store?.stayMin ?: 60
                recompute()
                val r = recommendation
                if (briefPending && r != null) { briefPending = false; voice.speak(Briefing.sentence(r, stayMin)) }
            }
        }
    }

    private fun recompute() {
        val d = dest ?: return render()
        recommendation = Recommender.recommend(d, lots ?: LotsResponse(emptyList()), stayMin)
        index = index.coerceIn(0, maxOf(0, (recommendation?.options?.size ?: 1) - 1))
        render()
    }

    private fun selected(): ParkingOption? = recommendation?.options?.getOrNull(index)

    /** 지금 고른 주차장을 "P 단에 넣으면 주차할 곳"으로 정한다. 주차 중·리마인드 중에는 건드리지 않는다. */
    private fun syncCandidate() {
        val st = tracker.state
        val o = selected() ?: return
        if (st.session != null || st.reminder != null) return
        tracker.candidate = Candidate(o, recommendation?.storeName, place.name)
        ParkingService.ensure(this)
    }

    private fun render() {
        if (!::content.isInitialized) return
        syncCandidate()
        val st = tracker.state
        val r = recommendation
        val screen = when {
            st.reminder != null -> reminderScreen(st.reminder!!)
            st.session != null -> parkedScreen(st.session!!)
            r != null && r.options.isNotEmpty() -> compareScreen(r)
            else -> emptyScreen()
        }
        content.removeAllViews()
        content.addView(screen, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    // ---- 화면

    private fun compareScreen(r: Recommendation): View = Ui.vbox(this, 28).apply {
        setPadding(dp(56), dp(44), dp(56), dp(28))
        addView(Ui.hbox(this@MainActivity).apply {
            addView(Ui.vbox(this@MainActivity, 6).apply {
                addView(Ui.text(this@MainActivity, "${place.name} 주변 · 800m 이내", 26, Ui.MUTED))
                addView(Ui.text(this@MainActivity, "주차 비교", 48, Ui.TEXT, 900))
            })
            addView(Ui.grow(this@MainActivity))
            addView(Ui.text(this@MainActivity, "머무를 시간", 26, Ui.MUTED).apply { setPadding(0, 0, dp(16), 0) })
            addView(Ui.hbox(this@MainActivity, 12).apply {
                stayChoices().forEach { m ->
                    addView(Ui.button(this@MainActivity, durationText(m), m == stayMin, 72, 28) { stayMin = m; recompute() }.apply {
                        background = Ui.rounded(if (m == stayMin) Ui.ACCENT else Ui.BUTTON, 20, this@MainActivity)
                        setPadding(dp(32), 0, dp(32), 0)
                    }, lp(WRAP, dp(72)))
                }
            })
        })
        val opts = r.options.take(5)
        addView(Ui.hbox(this@MainActivity, 32).apply {
            gravity = Gravity.TOP
            addView(MiniMapView(this@MainActivity).apply {
                destLat = place.lat; destLng = place.lng; options = opts; selected = index
            }, lp(dp(640), MATCH))
            addView(Ui.vbox(this@MainActivity, 14).apply {
                opts.forEachIndexed { i, o -> addView(optionRow(i, o, i == index), lp(MATCH, WRAP)) }
                addView(Ui.grow(this@MainActivity))
                addView(Ui.text(this@MainActivity, "P단에 넣으면 ${index + 1}번 주차장으로 주차 시간을 챙겨 드려요", 24, Ui.ACCENT_TEXT, 700))
                addView(Ui.hbox(this@MainActivity, 20).apply {
                    val o = opts[index]
                    val guided = follower.guiding == o.name
                    addView(Ui.button(this@MainActivity, if (guided) "${index + 1}번으로 안내 중" else "${index + 1}번으로 안내", true, 104, 34) {
                        guide(o); render()
                    }, lp(0, dp(104), 2f))
                    addView(Ui.button(this@MainActivity, "추천 듣기", false, 104, 30) { voice.speak(Briefing.sentence(r, stayMin)) }, lp(0, dp(104), 1f))
                    addView(Ui.button(this@MainActivity, "주차 시작", false, 104, 30) { tracker.startNow() }, lp(0, dp(104), 1f))
                })
            }, lp(0, MATCH, 1f))
        }, lp(MATCH, 0, 1f))
        addView(footer())
    }

    private fun optionRow(i: Int, o: ParkingOption, selected: Boolean): View = Ui.hbox(this, 24).apply {
        setPadding(dp(28), dp(20), dp(28), dp(20))
        background = Ui.rounded(Ui.CARD, 28, this@MainActivity, if (selected) Ui.GREEN else null)
        addView(numberPin(i + 1, selected))
        addView(Ui.vbox(this@MainActivity, 6).apply {
            addView(Ui.text(this@MainActivity, o.name, if (selected) 32 else 28, Ui.TEXT, 700).apply { maxLines = 1 })
            if (o.reason.isNotEmpty()) addView(Ui.text(this@MainActivity, o.reason, 22, Ui.GREEN, 700).apply { maxLines = 1 })
            addView(Ui.text(this@MainActivity, "도보 ${o.walkMinutes}분" + if (o.fee == null) " · 요금 정보 없음" else "", 22, Ui.MUTED))
        }, lp(0, WRAP, 1f))
        addView(Ui.text(this@MainActivity, Ui.won(o.fee), if (selected) 48 else 36, if (o.fee == 0) Ui.GREEN else Ui.TEXT, if (selected) 900 else 700))
        setOnClickListener { index = i; render() }
    }

    private fun parkedScreen(s: ParkingSession): View = Ui.vbox(this, 28).apply {
        setPadding(dp(56), dp(44), dp(56), dp(28))
        val now = System.currentTimeMillis()
        val left = s.freeLeftMin(now)
        val fee = s.fee(now)?.let { if (s.hasBenefit) it.withBenefit else it.withoutBenefit }
        addView(Ui.vbox(this@MainActivity, 28).apply {
            setPadding(dp(64), dp(56), dp(64), dp(56))
            background = Ui.rounded(Ui.CARD, 40, this@MainActivity, Ui.ACCENT, 2)
            addView(Ui.text(this@MainActivity, "주차 중 · ${s.destName}", 28, Ui.ACCENT_TEXT, 700))
            addView(Ui.text(this@MainActivity, s.lotName, 64, Ui.TEXT, 900).apply { maxLines = 2 })
            if (s.reason.isNotEmpty()) addView(pill(s.reason, 28, Ui.GREEN, Ui.GREEN_BG), lp(WRAP, WRAP))
            addView(Ui.hbox(this@MainActivity, 20).apply {
                addView(stat("주차한 시간", durationText(s.elapsedMin(now)), Ui.TEXT), lp(0, WRAP, 1f))
                addView(stat("남은 무료 시간",
                    when { left == null -> "혜택 없음"; left > 0 -> durationText(left); else -> "끝났어요" },
                    when { left == null -> Ui.MUTED; left in 0..ParkingSession.WARN_BEFORE_MIN -> Ui.AMBER; else -> Ui.GREEN }), lp(0, WRAP, 1f))
                addView(stat("지금 나가면", Ui.won(fee), if (fee == 0) Ui.GREEN else Ui.TEXT), lp(0, WRAP, 1f))
            })
            s.freeWarnAt()?.let { at ->
                if (at > now) addView(Ui.text(this@MainActivity, "무료 시간이 15분 남으면 음성으로 알려 드려요", 26, Ui.MUTED))
            }
            addView(Ui.grow(this@MainActivity))
            addView(Ui.button(this@MainActivity, "주차 상태 듣기", true, 112, 36) {
                voice.speak(ParkingTalk.status(s, System.currentTimeMillis()))
            }, lp(MATCH, dp(112)))
        }, lp(MATCH, 0, 1f))
        addView(Ui.text(this@MainActivity, "출발할 때(P → D) 혜택 등록을 확인해 드려요", 24, Ui.FAINT))
    }

    private fun reminderScreen(rm: ExitReminder): View = Ui.vbox(this, 28).apply {
        setPadding(dp(56), dp(44), dp(56), dp(28))
        val s = rm.session
        val fee = rm.fee
        val store = s.hasBenefit
        addView(Ui.vbox(this@MainActivity, 28).apply {
            setPadding(dp(64), dp(56), dp(64), dp(56))
            background = Ui.rounded(Ui.CARD, 40, this@MainActivity, Ui.AMBER)
            addView(Ui.text(this@MainActivity, "출발 전에 확인하세요 · P → D", 28, Ui.AMBER, 700))
            addView(Ui.text(this@MainActivity,
                if (store) "${s.storeName}에서\n무료 주차 등록하셨나요?" else "${s.lotName}\n주차 요금을 확인하세요",
                64, Ui.TEXT, 900).apply { setLineSpacing(0f, 1.15f) })
            if (fee != null) {
                val h = durationText(fee.minutes)
                addView(Ui.text(this@MainActivity, when {
                    store && fee.saved > 0 -> "등록하지 않으면 $h 기준 약 ${Ui.won(fee.withoutBenefit)}이 나와요. 등록하면 약 ${Ui.won(fee.withBenefit)}이에요."
                    store -> "$h 주차라 지금은 요금이 약 ${Ui.won(fee.withoutBenefit)}이에요. 오래 머물렀다면 등록을 확인하세요."
                    else -> "$h 기준 약 ${Ui.won(fee.withoutBenefit)}이에요."
                }, 30, Ui.TEXT2))
                addView(Ui.hbox(this@MainActivity, 20).apply {
                    addView(stat("주차한 시간", h, Ui.TEXT), lp(0, WRAP, 1f))
                    addView(stat("가게 혜택", if (store) durationText(s.freeMin) + " 무료" else "없음", Ui.TEXT), lp(0, WRAP, 1f))
                    addView(stat(if (store) "등록 시 예상" else "예상 요금",
                        Ui.won(if (store) fee.withBenefit else fee.withoutBenefit), Ui.GREEN), lp(0, WRAP, 1f))
                })
            }
            addView(Ui.grow(this@MainActivity))
            addView(Ui.hbox(this@MainActivity, 24).apply {
                addView(Ui.button(this@MainActivity, if (store) "등록했어요" else "확인", true, 112, 38) {
                    tracker.dismissReminder()
                }, lp(0, dp(112), 1f))
                if (store) addView(Ui.button(this@MainActivity, "다시 듣기", false, 112, 34) {
                    voice.speak(ParkingTalk.exitReminder(rm))
                }, lp(0, dp(112), 1f))
            })
        }, lp(MATCH, 0, 1f))
    }

    private fun emptyScreen(): View = Ui.vbox(this, 40).apply {
        setPadding(dp(72), dp(56), dp(72), dp(32))
        addView(Ui.hbox(this@MainActivity, 20).apply {
            addView(logo(56)); addView(Ui.text(this@MainActivity, "빡딜", 32, Ui.TEXT, 900))
        })
        addView(Ui.vbox(this@MainActivity, 24).apply {
            setPadding(dp(72), dp(64), dp(72), dp(64))
            background = Ui.rounded(Ui.CARD, 40, this@MainActivity)
            if (failed) {
                addView(Ui.text(this@MainActivity, "주차 정보를 불러오지 못했어요", 56, Ui.TEXT, 900))
                addView(Ui.button(this@MainActivity, "다시 시도", true, 104, 34) { failed = false; render(); load() }, lp(dp(360), dp(104)))
            } else {
                addView(Ui.text(this@MainActivity, place.name, 28, Ui.ACCENT_TEXT, 700))
                addView(Ui.text(this@MainActivity, "주변 주차를\n비교하고 있어요", 64, Ui.TEXT, 900).apply { setLineSpacing(0f, 1.15f) })
            }
        }, lp(MATCH, WRAP))
    }

    // ---- 조각

    private fun footer(): View = Ui.hbox(this).apply {
        addView(Ui.hbox(this@MainActivity, 14).apply {
            addView(logo(40)); addView(Ui.text(this@MainActivity, "빡딜", 24, Ui.TEXT, 900))
        })
        addView(Ui.grow(this@MainActivity))
        addView(Ui.text(this@MainActivity, "정보 출처: 네이버 지도 · ${originText(origin)}", 22, Ui.FAINT))
    }

    private fun stayChoices(): List<Int> {
        val base = dest?.store?.stayMin ?: 60
        return (listOf(base) + listOf(60, 120, 180)).distinct().sorted().take(4)
    }

    private fun logo(size: Int) = Ui.text(this, "P", size * 6 / 10, Ui.TEXT, 900).apply {
        gravity = Gravity.CENTER
        background = Ui.rounded(Ui.ACCENT, size * 2 / 7, this@MainActivity)
        layoutParams = lp(dp(size), dp(size))
    }

    private fun pill(s: String, size: Int, fg: Int, bg: Int) = Ui.text(this, s, size, fg, 700).apply {
        setPadding(dp(28), dp(14), dp(28), dp(14)); background = Ui.rounded(bg, 999, this@MainActivity)
    }

    private fun numberPin(n: Int, selected: Boolean) = Ui.text(this, "$n", if (selected) 30 else 26, if (selected) Ui.BG else Ui.TEXT, 900).apply {
        gravity = Gravity.CENTER
        val s = if (selected) 60 else 50
        background = Ui.rounded(if (selected) Ui.GREEN else Ui.PIN, 999, this@MainActivity)
        layoutParams = lp(dp(s), dp(s))
    }

    private fun stat(label: String, value: String, color: Int) = Ui.vbox(this, 6).apply {
        setPadding(dp(28), dp(24), dp(28), dp(24)); background = Ui.rounded(Ui.RAISED, 24, this@MainActivity)
        addView(Ui.text(this@MainActivity, label, 22, Ui.MUTED))
        addView(Ui.text(this@MainActivity, value, 40, color, 700))
    }

    private fun originText(origin: Origin?) = when (origin) {
        Origin.SERVER -> "실시간"
        Origin.CACHE -> "저장된 정보 (서버 연결 안 됨)"
        Origin.SNAPSHOT -> "내장 데이터 (오프라인)"
        null -> "-"
    }

    private companion object {
        const val EXTRA_SKIP_MIN = "skipMin"
        const val EXTRA_DEST = "dest"
        const val TAG = "ParkDeal"
    }
}
