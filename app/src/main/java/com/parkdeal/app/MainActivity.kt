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
import com.parkdeal.app.drive.DrivingRestriction
import com.parkdeal.app.navi.NaviSource
import com.parkdeal.app.voice.Listener
import com.parkdeal.core.voice.VoiceCommand
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
import com.parkdeal.core.navi.ApproachTrigger
import com.parkdeal.core.navi.DestinationFollower
import com.parkdeal.core.navi.NaviTalk
import com.parkdeal.core.navi.Place
import com.parkdeal.core.api.ParkingRepository
import com.parkdeal.core.durationText
import com.parkdeal.core.parking.Candidate
import com.parkdeal.core.parking.ExitReminder
import com.parkdeal.core.parking.MonthSummary
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

    /** 지금 추천을 만드는 목적지. 내비에 목적지를 넣으면 정해진다. 없으면 목적지를 기다리는 화면. */
    private var place: Place? = null
    /** 지금 화면에 보이는 추천의 목적지. 새 목적지를 불러오는 동안에는 [place] 와 다르다(이전 화면을 유지). */
    private var shownPlace: Place? = null
    /** 내비가 안내 중인 목적지가 있는지. 있으면 주차장은 경유지로 넣는다. */
    private var naviRoute = false
    private val follower = DestinationFollower()
    private val navi by lazy { NaviSource(this, ::onNaviDestination, ::onRemain, ::onWaypointArrived) }
    private val approach = ApproachTrigger()
    private val driving by lazy { DrivingRestriction(this) { render() } }
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
        voice // TTS·STT 엔진을 미리 띄워 첫 안내·첫 질문이 늦지 않게
        listener
        driving
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
        // 에뮬레이터 내비는 위치가 움직이지 않아 아래 두 이벤트가 오지 않는다. 같은 처리 경로를 직접 부른다
        // `--ei remain 800`: 목적지까지 800m 남음 / `--es arrive 아크플레이스`: 경유지 도착
        if (intent.hasExtra(EXTRA_REMAIN)) {
            val m = intent.getIntExtra(EXTRA_REMAIN, 0)
            intent.removeExtra(EXTRA_REMAIN)
            shownPlace?.let { onRemain(it.name, m) }
        }
        intent.getStringExtra(EXTRA_ARRIVE)?.let { intent.removeExtra(EXTRA_ARRIVE); onWaypointArrived(it) }
        // `--es say "2번으로"`: 말한 것처럼 처리한다 (에뮬레이터 마이크 없이 시연)
        intent.getStringExtra(EXTRA_SAY)?.let { intent.removeExtra(EXTRA_SAY); onListen(Listener.State.Final(it)) }
        // `--es dest 필앤필버거`: 데모 목적지로 내비 경로를 건다 (내비에서 목적지를 검색하는 대신)
        intent.getStringExtra(EXTRA_DEST)?.let { name ->
            intent.removeExtra(EXTRA_DEST)
            DemoPlaces.all.firstOrNull { it.name == name }?.let { navi.route(Place(it.name, it.lat, it.lng)) }
        }
    }

    /** 내비 목적지가 바뀌면 그 목적지로 추천을 다시 만들고, 불러오면 음성으로 브리핑한다. */
    private fun onNaviDestination(p: Place, remainMeters: Int) {
        naviRoute = true
        val next = follower.onDestination(p) ?: return
        Log.d(TAG, "navi destination ${next.name} remain=${remainMeters}m")
        place = next
        failed = false
        briefPending = true
        // 이전 추천은 새 응답이 올 때까지 그대로 둔다 (화면이 "불러오는 중"으로 번쩍이지 않게)
        render()
        load()
    }

    /** 지금 안내하는 주차장: 경유지로 넣은 곳, 없으면 고른 곳. */
    private fun target(): ParkingOption? {
        val opts = recommendation?.options ?: return null
        return opts.firstOrNull { it.name == follower.guiding } ?: opts.getOrNull(index)
    }

    /** 목적지 1km 안: 출발 때 들은 브리핑을 잊었을 때쯤 고른 곳을 한 번 더 말한다. */
    private fun onRemain(destName: String, meters: Int) {
        if (destName != shownPlace?.name) return
        val t = target() ?: return
        if (approach.onRemain(destName, meters)) voice.speak(NaviTalk.approaching(t))
    }

    /** 경유지 도착(PRD S6): 자리가 없을 때 갈 다음 후보까지 말한다. */
    private fun onWaypointArrived(name: String) {
        val opts = recommendation?.options ?: return
        val next = opts.firstOrNull { it.name != name }
        voice.speak(NaviTalk.waypointArrived(name, next))
    }

    /** 고른 주차장으로 내비 경로를 바꾼다. 목적지(가게)는 그대로 두고 추천도 유지한다. */
    private fun guide(o: ParkingOption, speak: Boolean = true) {
        val lat = o.lat; val lng = o.lng
        if (lat == null || lng == null) return
        follower.guidingTo(o.name)
        if (navi.via(Place(o.name, lat, lng), hasRoute = naviRoute) && speak) {
            voice.speak("${o.name}${if (endsWithBatchim(o.name)) "을" else "를"} 경유지로 넣었어요.")
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
        driving.close()
        listener.close()
        voice.close()
        super.onDestroy()
    }

    private fun load() {
        val p = place ?: return render()
        io.execute {
            val d = repository.destination(ApiRequest.Destination(p.lat, p.lng, p.name))
            val l = repository.lots(ApiRequest.Lots(p.lat, p.lng))
            main.post {
                if (isDestroyed || p != place) return@post // 그사이 목적지가 바뀌었다
                failed = d == null
                if (d == null) { dest = null; lots = null; recommendation = null; shownPlace = p; return@post render() }
                dest = d.value
                lots = l?.value
                origin = d.origin
                stayMin = d.value.store?.stayMin ?: 60
                index = 0
                shownPlace = p
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
    private fun shownName() = shownPlace?.name ?: "목적지"

    /** 지금 고른 주차장을 "P 단에 넣으면 주차할 곳"으로 정한다. 주차 중·리마인드 중에는 건드리지 않는다. */
    private fun syncCandidate() {
        val st = tracker.state
        val o = selected() ?: return
        if (st.session != null || st.reminder != null) return
        tracker.candidate = Candidate(o, recommendation?.storeName, shownName())
        ParkingService.ensure(this)
    }

    private fun render() {
        if (!::content.isInitialized) return
        syncCandidate()
        val st = tracker.state
        val r = recommendation
        // 주행 제한 중에는 줄인 화면만: 고른 곳 하나 + 짧은 목록, 입력·지도·긴 글 없음
        val drive = driving.restricted
        val screen = when {
            st.reminder != null -> if (drive) drivingReminder(st.reminder!!) else reminderScreen(st.reminder!!)
            st.session != null -> parkedScreen(st.session!!)
            r != null && r.options.isNotEmpty() -> if (drive) drivingScreen(r) else compareScreen(r)
            else -> emptyScreen()
        }
        content.removeAllViews()
        content.addView(screen, FrameLayout.LayoutParams(MATCH, MATCH))
        heard?.let { h ->
            content.addView(Ui.text(this, h, 32, Ui.TEXT, 700).apply {
                setPadding(dp(36), dp(22), dp(36), dp(22))
                background = Ui.rounded(Ui.GLEO_BG, 24, this@MainActivity, Ui.GLEO, 2)
                elevation = dp(8).toFloat()
            }, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(24) })
        }
    }

    // ---- 화면

    private fun compareScreen(r: Recommendation): View = Ui.vbox(this, 28).apply {
        setPadding(dp(56), dp(44), dp(56), dp(28))
        addView(Ui.hbox(this@MainActivity).apply {
            addView(Ui.vbox(this@MainActivity, 6).apply {
                val loading = place != null && place != shownPlace
                addView(Ui.text(this@MainActivity,
                    if (loading) "${place?.name} 주변을 찾는 중…" else "${shownName()} 주변 · 800m 이내",
                    26, if (loading) Ui.ACCENT_TEXT else Ui.MUTED))
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
                destLat = shownPlace?.lat ?: 0.0; destLng = shownPlace?.lng ?: 0.0; options = opts; selected = index
            }, lp(dp(640), MATCH))
            addView(Ui.vbox(this@MainActivity, 14).apply {
                opts.forEachIndexed { i, o -> addView(optionRow(i, o, i == index), lp(MATCH, WRAP)) }
                addView(Ui.grow(this@MainActivity))
                addView(Ui.hbox(this@MainActivity, 16).apply {
                    addView(Ui.text(this@MainActivity, "P단에 넣으면 ${index + 1}번 주차장으로 주차 시간을 챙겨 드려요", 24, Ui.ACCENT_TEXT, 700), lp(0, WRAP, 1f))
                    // 기어를 읽을 수 없는 차를 위한 수동 시작
                    addView(Ui.button(this@MainActivity, "지금 주차 시작", false, 56, 22) { tracker.startNow() }.apply {
                        setPadding(dp(24), 0, dp(24), 0); background = Ui.rounded(Ui.RAISED, 16, this@MainActivity)
                    }, lp(WRAP, dp(56)))
                })
                addView(Ui.hbox(this@MainActivity, 20).apply {
                    val o = opts[index]
                    val guided = follower.guiding == o.name
                    addView(Ui.button(this@MainActivity, if (guided) "${index + 1}번 경유 중" else "${index + 1}번 경유지로", true, 104, 34) {
                        guide(o); render()
                    }, lp(0, dp(104), 2f))
                    addView(micButton(104), lp(0, dp(104), 1f))
                    addView(Ui.button(this@MainActivity, "추천 듣기", false, 104, 30) { voice.speak(Briefing.sentence(r, stayMin)) }, lp(0, dp(104), 1f))
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
        // 카드를 누르면 바로 경유지로: 옆의 내비 지도에 그 주차장 경로가 그려진다 (PRD S2)
        setOnClickListener { index = i; guide(o, speak = false); render() }
    }

    private fun parkedScreen(s: ParkingSession): View = Ui.vbox(this, 28).apply {
        setPadding(dp(56), dp(44), dp(56), dp(28))
        val now = System.currentTimeMillis()
        val left = s.freeLeftMin(now)
        val fee = s.fee(now)?.let { if (s.hasBenefit) it.withBenefit else it.withoutBenefit }
        addView(Ui.vbox(this@MainActivity, 28).apply {
            setPadding(dp(64), dp(56), dp(64), dp(56))
            background = Ui.rounded(Ui.CARD, 40, this@MainActivity, Ui.ACCENT, 2)
            val justParked = s.elapsedMin(now) < 10
            addView(Ui.text(this@MainActivity, if (justParked) "${s.lotName}에 세웠어요" else "주차 중 · ${s.destName}", 28, Ui.ACCENT_TEXT, 700))
            if (justParked && s.hasBenefit) {
                // 세운 직후 할 일 하나: 혜택 가게 이용하고 등록하기 (PRD S7)
                addView(Ui.text(this@MainActivity, "${s.storeName}\n이용하고 주차 등록하세요", 56, Ui.TEXT, 900).apply { setLineSpacing(0f, 1.15f) })
            } else {
                addView(Ui.text(this@MainActivity, s.lotName, 64, Ui.TEXT, 900).apply { maxLines = 2 })
            }
            if (s.reason.isNotEmpty()) addView(pill(s.reason, 28, Ui.GREEN, Ui.GREEN_BG), lp(WRAP, WRAP))
            if (justParked) addView(Ui.text(this@MainActivity, "${s.destName}까지 걸어서 ${s.walkMinutes}분", 30, Ui.TEXT2, 700))
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
            addView(Ui.hbox(this@MainActivity, 24).apply {
                addView(Ui.button(this@MainActivity, "주차 상태 듣기", true, 112, 36) {
                    voice.speak(ParkingTalk.status(s, System.currentTimeMillis()))
                }, lp(0, dp(112), 2f))
                addView(micButton(112), lp(0, dp(112), 1f))
            })
        }, lp(MATCH, 0, 1f))
        addView(Ui.text(this@MainActivity, "출발할 때(P → D) 혜택 등록을 확인해 드려요", 24, Ui.FAINT))
    }

    private fun reminderScreen(rm: ExitReminder): View = Ui.hbox(this, 28).apply {
        setPadding(dp(56), dp(44), dp(56), dp(28))
        gravity = Gravity.TOP
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
                addView(Ui.button(this@MainActivity, if (store) "등록했어요" else "확인", true, 112, 36) {
                    tracker.dismissReminder(registered = store)
                }, lp(0, dp(112), 2f))
                if (store) addView(Ui.button(this@MainActivity, "못 했어요", false, 112, 32) {
                    tracker.dismissReminder(registered = false)
                }, lp(0, dp(112), 1f))
                if (store) addView(Ui.button(this@MainActivity, "다시 듣기", false, 112, 32) {
                    voice.speak(ParkingTalk.exitReminder(rm))
                }, lp(0, dp(112), 1f))
            })
        }, lp(0, MATCH, 1f))
        addView(monthPanel(), lp(dp(440), MATCH))
    }

    /** 이번 달 주차 기록: 낸 돈(추정)과 가게 혜택으로 아낀 돈. 출차 리마인드를 닫을 때 쌓인다. */
    private fun monthPanel(): View = Ui.vbox(this, 22).apply {
        setPadding(dp(40), dp(44), dp(40), dp(40))
        background = Ui.rounded(Ui.CARD, 40, this@MainActivity)
        val now = java.time.ZonedDateTime.now(SEOUL)
        val history = tracker.state.history
        val sum = MonthSummary.of(history, now.year, now.monthValue, SEOUL)
        addView(Ui.text(this@MainActivity, "${now.monthValue}월 주차 기록", 32, Ui.TEXT, 900))
        addView(Ui.vbox(this@MainActivity, 6).apply {
            addView(Ui.text(this@MainActivity, "혜택으로 아낀 금액", 22, Ui.MUTED))
            addView(Ui.text(this@MainActivity, Ui.won(sum.saved), 56, Ui.GREEN, 900))
            addView(Ui.text(this@MainActivity, "주차 ${sum.count}번 · 낸 돈 약 ${Ui.won(sum.paid)}", 22, Ui.MUTED))
        })
        addView(View(this@MainActivity).apply { setBackgroundColor(Ui.BUTTON) }, lp(MATCH, dp(1)))
        val recent = history.takeLast(4).reversed()
        if (recent.isEmpty()) addView(Ui.text(this@MainActivity, "아직 기록이 없어요.\n출차 확인을 마치면 쌓여요.", 24, Ui.MUTED).apply { setLineSpacing(0f, 1.4f) })
        recent.forEach { e ->
            val d = java.time.Instant.ofEpochMilli(e.endedAt).atZone(SEOUL)
            addView(Ui.hbox(this@MainActivity, 16).apply {
                addView(Ui.vbox(this@MainActivity, 4).apply {
                    addView(Ui.text(this@MainActivity, e.lotName, 24, Ui.TEXT, 700).apply { maxLines = 1 })
                    addView(Ui.text(this@MainActivity, "${d.monthValue}/${d.dayOfMonth} · ${durationText(e.minutes)}" +
                        if (e.saved > 0) " · ${Ui.won(e.saved)} 아낌" else "", 20, if (e.saved > 0) Ui.GREEN else Ui.MUTED))
                }, lp(0, WRAP, 1f))
                addView(Ui.text(this@MainActivity, Ui.won(e.paid), 26, Ui.TEXT, 700))
            })
        }
    }

    /** 목적지 없음(S1) · 불러오는 중 · 정보 없음(S12). 다음에 할 일 하나만 안내한다. */
    private fun emptyScreen(): View = Ui.vbox(this, 40).apply {
        setPadding(dp(72), dp(56), dp(72), dp(32))
        addView(Ui.hbox(this@MainActivity, 20).apply {
            addView(logo(56)); addView(Ui.text(this@MainActivity, "빡딜", 32, Ui.TEXT, 900))
        })
        val p = place
        val noLots = recommendation?.options?.isEmpty() == true
        addView(Ui.vbox(this@MainActivity, 24).apply {
            setPadding(dp(72), dp(64), dp(72), dp(64))
            background = Ui.rounded(Ui.CARD, 40, this@MainActivity)
            when {
                p == null -> {
                    addView(Ui.text(this@MainActivity, "목적지 주변 주차 혜택", 28, Ui.ACCENT_TEXT, 700))
                    addView(Ui.text(this@MainActivity, "내비에 목적지를 설정하면\n주변 주차를 비교해 드려요", 60, Ui.TEXT, 900).apply { setLineSpacing(0f, 1.15f) })
                    addView(Ui.text(this@MainActivity, "같은 건물 가게 혜택까지 찾아서 실제로 낼 돈으로 비교해요", 28, Ui.MUTED))
                }
                failed -> {
                    addView(Ui.text(this@MainActivity, p.name, 28, Ui.ACCENT_TEXT, 700))
                    addView(Ui.text(this@MainActivity, "주차 정보를 받지 못했어요", 56, Ui.TEXT, 900))
                    addView(Ui.text(this@MainActivity, "인터넷 연결을 확인하고 다시 시도해 주세요. 아직 정보가 없는 지역일 수도 있어요.", 28, Ui.MUTED))
                    addView(Ui.button(this@MainActivity, "다시 시도", true, 104, 34) { failed = false; render(); load() }, lp(dp(360), dp(104)))
                }
                noLots -> {
                    addView(Ui.text(this@MainActivity, p.name, 28, Ui.ACCENT_TEXT, 700))
                    addView(Ui.text(this@MainActivity, "800m 안에 아는 주차장이 없어요", 56, Ui.TEXT, 900))
                    addView(Ui.text(this@MainActivity, "지금은 서울 지하철 2호선 역 주변 정보만 있어요.", 28, Ui.MUTED))
                }
                else -> {
                    addView(Ui.text(this@MainActivity, p.name, 28, Ui.ACCENT_TEXT, 700))
                    addView(Ui.text(this@MainActivity, "주변 주차를\n비교하고 있어요", 64, Ui.TEXT, 900).apply { setLineSpacing(0f, 1.15f) })
                }
            }
        }, lp(MATCH, WRAP))
    }

    /** 주행 중 (PRD S3·S4): 고른 곳(경유 중이면 그곳)을 크게, 다른 후보 3곳은 한 줄씩. 탭 한 번이면 경유지 변경. */
    private fun drivingScreen(r: Recommendation): View = Ui.vbox(this, 24).apply {
        setPadding(dp(56), dp(40), dp(56), dp(28))
        val opts = r.options
        val guided = opts.indexOfFirst { it.name == follower.guiding }
        val topIndex = if (guided >= 0) guided else index
        val top = opts[topIndex]
        addView(Ui.hbox(this@MainActivity, 14).apply {
            addView(logo(40)); addView(Ui.text(this@MainActivity, "빡딜", 24, Ui.TEXT, 900))
            addView(Ui.grow(this@MainActivity))
            addView(Ui.text(this@MainActivity, shownName(), 26, Ui.MUTED))
        })
        addView(Ui.vbox(this@MainActivity, 20).apply {
            setPadding(dp(56), dp(44), dp(56), dp(44))
            background = Ui.rounded(Ui.CARD, 40, this@MainActivity, if (guided >= 0) Ui.ACCENT else null, 2)
            addView(Ui.text(this@MainActivity, if (guided >= 0) "경유 중" else "주차는 여기로", 30, Ui.ACCENT_TEXT, 700))
            addView(Ui.hbox(this@MainActivity, 32).apply {
                gravity = Gravity.BOTTOM
                addView(Ui.text(this@MainActivity, top.name, 56, Ui.TEXT, 900).apply { maxLines = 1 }, lp(0, WRAP, 1f))
                addView(Ui.text(this@MainActivity, Ui.won(top.fee), 96, if (top.fee == 0) Ui.GREEN else Ui.TEXT, 900))
            })
            if (top.reason.isNotEmpty()) addView(pill(top.reason, 28, Ui.GREEN, Ui.GREEN_BG), lp(WRAP, WRAP))
            addView(Ui.text(this@MainActivity, "${durationText(stayMin)} 머물면 · 목적지까지 걸어서 ${top.walkMinutes}분", 28, Ui.TEXT2))
            if (guided < 0) addView(Ui.button(this@MainActivity, "여기로 경유", true, 104, 36) { guide(top); render() }, lp(MATCH, dp(104)))
        }, lp(MATCH, WRAP))
        addView(Ui.text(this@MainActivity, "다른 후보", 24, Ui.MUTED))
        opts.withIndex().filter { it.index != topIndex }.take(3).forEach { (i, o) ->
            addView(Ui.hbox(this@MainActivity, 24).apply {
                setPadding(dp(28), dp(18), dp(28), dp(18))
                background = Ui.rounded(Ui.CARD, 24, this@MainActivity)
                addView(numberPin(i + 1, false))
                addView(Ui.text(this@MainActivity, o.name, 30, Ui.TEXT, 700).apply { maxLines = 1 }, lp(0, WRAP, 1f))
                addView(Ui.text(this@MainActivity, Ui.won(o.fee), 36, if (o.fee == 0) Ui.GREEN else Ui.TEXT, 900))
                setOnClickListener { index = i; guide(o); render() }
            }, lp(MATCH, WRAP))
        }
        addView(Ui.grow(this@MainActivity))
        addView(micButton(104), lp(MATCH, dp(104)))
        addView(Ui.text(this@MainActivity, "\"다른 데는?\" \"2번으로\"처럼 말해도 돼요 · 정차하면 전체 비교가 열려요", 22, Ui.FAINT))
    }

    /** 주행 중 출차 확인 (PRD S8): 질문 하나와 버튼 둘. 금액 비교·기록은 정차 후에. */
    private fun drivingReminder(rm: ExitReminder): View = Ui.vbox(this, 32).apply {
        setPadding(dp(56), dp(56), dp(56), dp(40))
        val s = rm.session
        val store = s.hasBenefit
        val fee = rm.fee
        addView(Ui.text(this@MainActivity, "출발 전에 확인하세요", 30, Ui.AMBER, 700))
        addView(Ui.text(this@MainActivity,
            if (store) "${s.storeName}에서\n주차 등록하셨나요?" else "${s.lotName}\n주차 요금을 확인하세요", 60, Ui.TEXT, 900).apply { setLineSpacing(0f, 1.15f) })
        if (fee != null) addView(Ui.text(this@MainActivity,
            if (store && fee.saved > 0) "안 하면 약 ${Ui.won(fee.withoutBenefit)}" else "약 ${Ui.won(fee.withoutBenefit)}", 36, Ui.TEXT2, 700))
        addView(Ui.grow(this@MainActivity))
        addView(Ui.hbox(this@MainActivity, 24).apply {
            addView(Ui.button(this@MainActivity, if (store) "등록했어요" else "확인", true, 120, 38) {
                tracker.dismissReminder(registered = store)
            }, lp(0, dp(120), 1f))
            if (store) addView(Ui.button(this@MainActivity, "못 했어요", false, 120, 36) {
                tracker.dismissReminder(registered = false)
            }, lp(0, dp(120), 1f))
            addView(micButton(120), lp(0, dp(120), 1f))
        })
    }

    // ---- 음성으로 묻기 (SpeechToText)

    private val listener by lazy { Listener(this, ::onListen) }
    /** 듣는 중·들은 말을 화면 위에 띄우는 한 줄. null 이면 숨김. */
    private var heard: String? = null

    private fun micButton(height: Int) = Ui.button(this, "말로 묻기", false, height, 30) { ask() }

    private fun ask() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
            return
        }
        if (!listener.listen()) voice.speak("음성 인식을 준비하고 있어요. 잠시 뒤에 다시 눌러 주세요.")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_MIC && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) ask()
    }

    private fun onListen(state: Listener.State) {
        heard = when (state) {
            Listener.State.Listening -> "듣고 있어요…"
            is Listener.State.Partial -> "“${state.text}”"
            is Listener.State.Final -> if (state.text.isBlank()) null else "“${state.text}”"
            is Listener.State.Failed -> "잘 듣지 못했어요"
        }
        render()
        when (state) {
            is Listener.State.Final -> { answer(VoiceCommand.parse(state.text)); main.postDelayed({ heard = null; render() }, 3_000) }
            is Listener.State.Failed -> { voice.speak("잘 듣지 못했어요. 다시 눌러 주세요."); main.postDelayed({ heard = null; render() }, 3_000) }
            else -> {}
        }
    }

    /** 알아들은 명령을 실행하고 한 문장으로 답한다. */
    private fun answer(cmd: VoiceCommand) {
        val st = tracker.state
        val r = recommendation
        val opts = r?.options.orEmpty()
        when (cmd) {
            VoiceCommand.Recommend ->
                voice.speak(if (r != null) Briefing.sentence(r, stayMin) else "내비에 목적지를 먼저 설정해 주세요.")
            is VoiceCommand.Pick -> {
                val o = opts.getOrNull(cmd.rank - 1) ?: return voice.speak("${cmd.rank}번 후보는 없어요.")
                index = cmd.rank - 1; guide(o); render()
            }
            VoiceCommand.Next -> {
                if (opts.size < 2) return voice.speak("다른 후보가 없어요.")
                val cur = opts.indexOfFirst { it.name == follower.guiding }.takeIf { it >= 0 } ?: index
                val n = (cur + 1) % opts.size
                val o = opts[n]
                index = n; render()
                voice.speak("${n + 1}번 ${o.name}, ${Ui.won(o.fee)}이에요. \"${n + 1}번으로\"라고 하면 경유지로 넣을게요.")
            }
            VoiceCommand.Status -> {
                val s = st.session
                val o = target()
                voice.speak(when {
                    s != null -> ParkingTalk.status(s, System.currentTimeMillis())
                    o != null -> "${o.name}에 ${durationText(stayMin)} 머물면 ${Ui.won(o.fee)}이에요."
                    else -> "아직 주차 정보가 없어요."
                })
            }
            is VoiceCommand.Registered -> {
                if (st.reminder == null) return voice.speak("지금은 확인할 출차가 없어요.")
                tracker.dismissReminder(registered = cmd.yes)
                voice.speak(if (cmd.yes) "기록했어요. 안전 운전하세요." else "미등록으로 기록했어요.")
            }
            VoiceCommand.Unknown -> voice.speak("\"제일 싼 곳\", \"2번으로\", \"지금 얼마야\"처럼 말씀해 주세요.")
        }
    }

    // ---- 조각

    private fun footer(): View = Ui.hbox(this).apply {
        addView(Ui.hbox(this@MainActivity, 14).apply {
            addView(logo(40)); addView(Ui.text(this@MainActivity, "빡딜", 24, Ui.TEXT, 900))
        })
        val now = java.time.ZonedDateTime.now(SEOUL)
        val saved = MonthSummary.of(tracker.state.history, now.year, now.monthValue, SEOUL).saved
        if (saved > 0) addView(Ui.text(this@MainActivity, "${now.monthValue}월 혜택으로 ${Ui.won(saved)} 아꼈어요", 22, Ui.GREEN, 700).apply {
            setPadding(dp(24), 0, 0, 0)
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
        const val REQ_MIC = 2
        const val EXTRA_SAY = "say"
        const val EXTRA_DEST = "dest"
        const val EXTRA_REMAIN = "remain"
        const val EXTRA_ARRIVE = "arrive"
        const val TAG = "ParkDeal"
        val SEOUL: java.time.ZoneId = java.time.ZoneId.of("Asia/Seoul")
    }
}
