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
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.HorizontalScrollView
import android.widget.TextView
import android.widget.FrameLayout
import com.parkdeal.app.data.AssetSnapshot
import com.parkdeal.app.data.FileResponseCache
import com.parkdeal.app.data.HttpUrlTransport
import com.parkdeal.app.drive.DrivingRestriction
import com.parkdeal.app.drive.VehicleProfile
import com.parkdeal.app.navi.NaviSource
import com.parkdeal.app.voice.Listener
import com.parkdeal.core.voice.VoiceCommand
import com.parkdeal.app.parking.Parking
import com.parkdeal.app.parking.ParkingService
import com.parkdeal.app.parking.Voice
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
import com.parkdeal.core.api.WalkResponse
import com.parkdeal.core.navi.ApproachTrigger
import com.parkdeal.core.navi.Bookmarks
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
import com.parkdeal.core.parking.ParkCheck
import com.parkdeal.app.ui.Kit
import android.widget.LinearLayout
import com.parkdeal.core.ExitFee
import com.parkdeal.core.FeeRule
import com.parkdeal.core.FeeText
import com.parkdeal.core.OtherDeal
import com.parkdeal.core.api.StopsResponse
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
        // 정차 중에는 남은 초를 보여주므로 1초마다
        override fun run() { render(); main.postDelayed(this, if (tracker.state.session?.stop == true) 1_000 else 30_000) } // 정차 막대는 초 단위로 줄어든다
    }

    /** 지금 추천을 만드는 목적지. 내비에 목적지를 넣으면 정해진다. 없으면 목적지를 기다리는 화면. */
    private var place: Place? = null
    /** 지금 화면에 보이는 추천의 목적지. 새 목적지를 불러오는 동안에는 [place] 와 다르다(이전 화면을 유지). */
    private var shownPlace: Place? = null
    /** 내비가 안내 중인 목적지가 있는지. 있으면 주차장은 경유지로 넣는다. */
    private var naviRoute = false
    private val follower = DestinationFollower()
    private val navi by lazy { NaviSource(this, ::onNaviDestination, ::onRemain, ::onWaypointArrived, { bookmarks = Bookmarks(it); Log.d(TAG, "즐겨찾기 ${it.size}곳: ${it.map { b -> b.kind }}") }) }
    private var bookmarks = Bookmarks(emptyList())
    /** 목적지가 집·회사면 그 즐겨찾기 이름. 이때는 주차 안내를 먼저 말하지 않는다 (PRD S11). */
    private var routineName: String? = null
    private val approach = ApproachTrigger()
    /** 이 차의 특성. 전기차면 전기차 불가 주차장을 맨 뒤로 보낸다. */
    private var vehicle = com.parkdeal.core.Vehicle.UNKNOWN
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
    /** 후보별로 고른 혜택 가게 (PRD "다른 혜택 가게로 바꾸기") */
    private val pickedDeal = mutableMapOf<Int, String>()
    /** 정차 허용 구간 (정차 칩을 고르면 받는다) */
    private var stops: StopsResponse? = null
    /** 내비가 알려 준 남은 거리(m) */
    private var lastRemain: Int? = null

    private val repository by lazy {
        ParkingRepository(
            transport = HttpUrlTransport(BuildConfig.PARKDEAL_API_BASE, "ParkDeal/${BuildConfig.VERSION_NAME} (Android)"),
            cache = FileResponseCache(File(filesDir, "api-cache")),
            snapshot = AssetSnapshot(assets),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        content = FrameLayout(this).apply { setBackgroundColor(Ui.GROUND) }
        setContentView(content)
        // 권한은 켜자마자 묻지 않는다: 우리 화면에서 무엇에 쓰는지 먼저 말하고 묻는다 (permitScreen)
        voice // TTS·STT 엔진을 미리 띄워 첫 안내·첫 질문이 늦지 않게
        listener
        driving
        VehicleProfile.detect(this) { v -> vehicle = v; Log.d(TAG, "차량 $v"); recompute() }
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
        if (remainMeters > 0) lastRemain = remainMeters
        val next = follower.onDestination(p) ?: return
        stops = null
        Log.d(TAG, "navi destination ${next.name} remain=${remainMeters}m")
        place = next
        failed = false
        routineName = bookmarks.routine(next)?.name
        // 집·회사로 가는 길이면 묻지도 않은 주차 안내를 하지 않는다. 앱을 열면 목록은 보여준다
        briefPending = routineName == null
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
        if (meters > 0) { val changed = lastRemain?.let { kotlin.math.abs(it - meters) >= 100 } ?: true; lastRemain = meters; if (changed && driving.restricted) render() }
        if (destName != shownPlace?.name) return
        val t = target() ?: return
        if (routineName == null && approach.onRemain(destName, meters)) voice.speak(NaviTalk.approaching(t))
    }

    /** 경유지 도착(PRD S6): 자리가 없을 때 갈 다음 후보까지 말한다. */
    /** 경유지(고른 주차장) 근처에 왔다: 주행 화면을 "곧 도착해요 / 자리가 없으면" 으로 바꾼다. 주차하거나 3분 지나면 되돌린다. */
    private var arrivedAt: String? = null
    private val clearArrived = Runnable { arrivedAt = null; render() }

    private fun onWaypointArrived(name: String) {
        arrivedAt = name
        main.removeCallbacks(clearArrived); main.postDelayed(clearArrived, 3 * 60_000L)
        render()
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

    /** 화면이 앞에 있는지. 포그라운드 서비스는 이때만 시작할 수 있다. */
    private var foreground = false

    override fun onStart() {
        super.onStart()
        foreground = true
        Parking.observe(onState)
        main.post(tick)
        // 주차 중이면 서비스를 다시 띄운다 (프로세스가 다시 만들어진 경우)
        ParkingService.ensure(this)
    }

    override fun onStop() {
        foreground = false
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
                animList = true // 새 추천: 보드가 차례로 올라온다 (정차 중일 때만)
                recompute()
                val r = recommendation
                if (briefPending && r != null) { briefPending = false; voice.speak(Briefing.sentence(r, stayMin)) }
            }
        }
    }

    /** 정차 칩을 고르면 목적지 근처 정차 허용 구간을 받는다 */
    private fun loadStops() {
        val p = shownPlace ?: return render()
        render()
        io.execute {
            val r = repository.stops(ApiRequest.Stops(p.lat, p.lng))?.value ?: StopsResponse()
            main.post { if (shownPlace == p) { stops = r; render() } }
        }
    }

    private fun recompute() {
        val d = dest ?: return render()
        recommendation = Recommender.recommend(d, lots ?: LotsResponse(emptyList()), stayMin, vehicle)
        index = index.coerceIn(0, maxOf(0, (recommendation?.options?.size ?: 1) - 1))
        render()
        fetchWalks()
    }

    // ---- 실제 걷는 시간 (서버가 T맵 보행자 경로로 구한다). 없으면 직선거리 어림값을 쓴다

    private val walks = mutableMapOf<Int, WalkResponse>()
    private var walksFor: Place? = null

    /** 상위 후보 5곳의 주차장 → 목적지 실제 도보. 받는 대로 화면에 반영한다. */
    private fun fetchWalks() {
        val p = shownPlace ?: return
        if (walksFor != p) { walks.clear(); walksFor = p }
        val targets = recommendation?.options.orEmpty().take(5).filter { it.lat != null && it.lng != null && it.lotId !in walks }
        if (targets.isEmpty()) return
        io.execute {
            for (o in targets) {
                val w = repository.walk(ApiRequest.Walk(o.lat!!, o.lng!!, p.lat, p.lng))?.value ?: continue
                main.post { if (walksFor == p) { walks[o.lotId] = w; render() } }
            }
        }
    }

    private fun walkText(o: ParkingOption) =
        walks[o.lotId]?.let { "걸어서 ${it.walkMin}분 · ${it.distanceM}m" }
            // 같은 건물·바로 옆이면 보행 경로가 나오지 않는다 (검토판 A3)
            ?: if (o.walkMeters < NEAR_M) "목적지 바로 앞" else "걸어서 약 ${o.walkMinutes}분"

    private fun selected(): ParkingOption? = recommendation?.options?.getOrNull(index)

    /** 내비 옆 반쪽 창처럼 좁은 창. 지도·기록 패널을 빼고 목록을 전체 폭으로 쓴다. */
    private fun compact() = resources.configuration.screenWidthDp < 1100

    /** 좁은 창에서는 큰 글자를 3/4 로 줄인다 (최소 12sp 규칙은 큰 글자에만 쓰므로 해당 없음). */
    private fun fit(sp: Int) = if (compact()) sp * 3 / 4 else sp

    /** 창 크기가 바뀌면(분할 비율 조절) 다시 만들지 않고 다시 그린다. 추천·선택 상태가 그대로 남는다. */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        render()
    }
    private fun shownName() = routineName ?: shownPlace?.name ?: "목적지"

    /** 지금 고른 주차장을 "P 단에 넣으면 주차할 곳"으로 정한다. 주차 중·리마인드 중에는 건드리지 않는다. */
    private fun syncCandidate() {
        val st = tracker.state
        val sel = selected() ?: return
        if (st.session != null || st.reminder != null) return
        // 목적지 혜택이 없고 다른 가게 혜택을 쓰는 곳이면, 그 가게 기준으로 주차를 기록한다 (출차 때 그 가게 등록을 묻는다)
        // 실제 도보를 알면 주차 기록에도 그 거리를 쓴다 (주차 직후 "걸어서 N분" 안내)
        val o = walks[sel.lotId]?.let { w -> sel.copy(walkMeters = w.distanceM) } ?: sel
        val d = chosenDeal(o)
        val stopSeg = stops?.segments?.firstOrNull { it.allowedNow }
        tracker.candidate = when {
            // 정차: 허용 구간에 5분 (좌표가 없어 위치 확인은 하지 않는다)
            stayMin == STOP_MIN && stopSeg != null -> Candidate(
                ParkingOption(-1, stopSeg.desc, null, null, 0, 0, 0, "", null), null, shownName(), STOP_MIN, stop = true)
            d != null && !isOwn(o, d) -> Candidate(d.applyTo(o), d.storeName, shownName(), stayMin)
            d != null && d.storeName != recommendation?.storeName -> Candidate(d.applyTo(o), d.storeName, shownName(), stayMin)
            else -> Candidate(o, recommendation?.storeName, shownName(), stayMin)
        }
        if (foreground) ParkingService.ensure(this) // 뒤에 있을 때는 시작할 수 없다(Android 12+)
    }

    // ---- 그리기와 움직임
    // 움직임은 세 가지뿐이고 전부 운전자가 한 일에 답한다: 화면이 바뀔 때, 다른 곳을 골랐을 때, 금액이 바뀌었을 때.
    // 주행 제한 중에는 하나도 쓰지 않는다 (docs/pleos-guidelines.md 1장: 주행 중 장식·반복 애니메이션 금지).

    /** 지금 보이는 화면 종류. 바뀔 때만 한 번 전환한다 */
    private var lastScreen: String? = null
    /** 이번 그리기에서 움직여도 되는지 (주행 제한 중이면 false) */
    private var motion = true
    /** 다음 그리기 한 번만: 목록이 차례로 올라온다 / 고른 곳 판이 바뀐다 / 금액이 굴러간다 */
    private var animList = false
    private var animDetail = false
    private var animFee = false
    private val shownFee = mutableMapOf<String, Int>()

    private fun render() {
        if (!::content.isInitialized) return
        // 주간·야간 테마: 시스템(Pleos) 설정을 따른다
        Ui.theme((resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES)
        content.setBackgroundColor(Ui.GROUND)
        syncCandidate()
        val st = tracker.state
        if (st.session != null) arrivedAt = null
        val r = recommendation
        // 주행 제한 중에는 줄인 화면만: 고른 곳 하나 + 짧은 목록, 입력·지도·긴 글 없음
        val drive = driving.restricted
        motion = !drive
        val now = System.currentTimeMillis()
        val key = when {
            st.reminder != null -> "reminder"
            st.session?.stop == true -> "stop"
            st.session != null -> if (st.session!!.elapsedMin(now) < 10 && st.session!!.farFrom == null) "parked" else "parking"
            // 처음 한 번, 정차 중에만: 시스템 권한 창을 띄우기 전에 무엇에 쓰는지 알린다
            !drive && !permitSkipped && !permitAsked() && missingPermits().isNotEmpty() -> "permit"
            r != null && r.options.isNotEmpty() -> if (drive) "drive" else "compare"
            place == null -> "waiting"
            else -> "empty"
        }
        val screen = when (key) {
            "reminder" -> reminderScreen(st.reminder!!, drive)
            "stop", "parked", "parking" -> parkedScreen(st.session!!)
            "drive" -> drivingScreen(r!!)
            "compare" -> compareScreen(r!!)
            "waiting" -> waitingScreen()
            "permit" -> permitScreen()
            else -> emptyScreen()
        }
        val scrolls = HashMap<String, Int>()
        if (key == lastScreen) collectScroll(content, scrolls)
        content.removeAllViews()
        content.addView(screen, FrameLayout.LayoutParams(MATCH, MATCH))
        if (scrolls.isNotEmpty()) restoreScroll(screen, scrolls)
        if (motion && lastScreen != null && key != lastScreen) {
            appear(screen, 20, 0, 260)
        }
        lastScreen = key
        animList = false; animDetail = false; animFee = false
        heard?.let { h ->
            content.addView(Ui.text(this, h, 30, Ui.ON_INK, 700).apply {
                setPadding(dp(36), dp(22), dp(36), dp(22))
                background = Ui.rounded(Ui.INK, 28, this@MainActivity)
                elevation = dp(8).toFloat()
            // 버튼 줄 바로 위에 띄운다. 위쪽에 두면 머무를 시간 버튼을 가린다 (검토판 A6)
            }, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(190) })
        }
    }

    /** 다시 그려도 목록이 맨 위로 튀지 않게 스크롤 위치를 옮겨 준다 */
    private fun collectScroll(v: View, out: MutableMap<String, Int>) {
        (v.tag as? String)?.takeIf { it.startsWith(Kit.SCROLL) }?.let { out[it] = if (v is HorizontalScrollView) v.scrollX else v.scrollY }
        if (v is ViewGroup) for (i in 0 until v.childCount) collectScroll(v.getChildAt(i), out)
    }

    private fun restoreScroll(root: View, saved: Map<String, Int>) {
        root.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                root.viewTreeObserver.removeOnPreDrawListener(this)
                fun walk(v: View) {
                    (v.tag as? String)?.let { saved[it] }?.let { if (v is HorizontalScrollView) v.scrollTo(it, 0) else v.scrollTo(0, it) }
                    if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
                }
                walk(root)
                return true
            }
        })
    }

    /** 아래에서 살짝 올라오며 나타난다. [order] 만큼 늦게 출발해 목록이 차례로 선다 */
    private fun rise(v: View, order: Int) {
        if (!motion) return
        appear(v, 18, 35L * order, 280)
    }

    /**
     * 살짝 올라오며 나타난다. 애니메이션이 어떤 이유로든 돌지 않아도(창이 가려짐·시스템 설정)
     * 화면이 빈 채로 남지 않게, 끝날 시각에 한 번 더 제자리로 돌려 놓는다.
     */
    private fun appear(v: View, fromDp: Int, delay: Long, duration: Long) {
        v.alpha = 0f; v.translationY = dp(fromDp).toFloat()
        v.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(duration).setInterpolator(DecelerateInterpolator(1.6f)).start()
        main.postDelayed({ v.animate().cancel(); v.alpha = 1f; v.translationY = 0f }, delay + duration + 80)
    }

    /** 머물 시간·혜택 가게를 바꿔 금액이 달라지면 이전 금액에서 굴러간다 */
    private fun rollFee(key: String, tv: TextView, value: Int?) {
        val prev = shownFee[key]
        if (value == null) { shownFee.remove(key); return }
        shownFee[key] = value
        if (!motion || !animFee || prev == null || prev == value) return
        ValueAnimator.ofInt(prev, value).apply {
            duration = 420; interpolator = DecelerateInterpolator(2f)
            addUpdateListener { tv.text = Ui.won(it.animatedValue as Int) }
            start()
        }
    }

    // ---- 화면: PRD 시안의 데이터는 그대로, 배치는 "가격표 보드"로 (ui/Ui.kt 머리말)

    private val kit by lazy { Kit(this) }

    /** 맨 윗줄 오른쪽 끝의 "말로 묻기" */
    private fun micPill(): View = Ui.text(this, "말로 묻기", 24, Ui.INK, 700).apply {
        gravity = Gravity.CENTER
        setPadding(dp(28), 0, dp(28), 0); minimumHeight = dp(68); minHeight = dp(68)
        background = Ui.rounded(android.graphics.Color.TRANSPARENT, 999, this@MainActivity, Ui.INK3, 2)
        Ui.tap(this, 34) { ask() }
    }

    private fun clock(ms: Long): String {
        val t = java.time.Instant.ofEpochMilli(ms).atZone(SEOUL)
        val h = t.hour
        return "${if (h < 12) "오전" else "오후"} ${if (h % 12 == 0) 12 else h % 12}:${"%02d".format(t.minute)}"
    }

    private fun remainText(): String = lastRemain?.let { if (it >= 1000) "남은 %.1fkm".format(it / 1000.0) else "남은 ${it}m" } ?: ""

    /** 이 후보에서 고른 혜택 가게 (없으면 첫째) */
    private fun chosenDeal(o: ParkingOption): OtherDeal? = o.deals.firstOrNull { it.storeName == pickedDeal[o.lotId] } ?: o.deals.firstOrNull()

    /** 목적지 가게 혜택인가 (시안의 "혜택 적용") */
    private fun isOwn(o: ParkingOption, d: OtherDeal?) = d != null && d.storeName == recommendation?.storeName && o.reason.isNotEmpty()

    /** 보드의 큰 금액: 목적지 혜택이면 혜택 적용 금액, 아니면 일반 주차 요금 */
    private fun headFee(o: ParkingOption): Pair<String, String> {
        val d = chosenDeal(o)
        return when {
            o.blocked -> (o.notes.firstOrNull() ?: "진입 불가") to "불가"
            isOwn(o, d) -> "혜택 적용" to Ui.won(d!!.fee)
            else -> "일반 주차 시" to (o.normalFee?.let { Ui.won(it) } ?: "요금 정보 없음")
        }
    }

    private fun cond(d: OtherDeal) = d.reason.removePrefix(d.storeName).trim()

    /** 주차 비교 (PRD S2) · 정차를 고르면 정차 구간 (PRD S10) · 즐겨찾기 근처 (S11) */
    private fun compareScreen(r: Recommendation): View = kit.pad(Ui.vbox(this, 0), compact()).apply {
        val root: LinearLayout = this
        val narrow = compact()
        val routine = routineName
        val loading = place != null && place != shownPlace
        addView(kit.topBar(remainText(), micPill()), lp(MATCH, WRAP))
        addView(kit.title(if (routine != null) "$routine 주변 주차장" else shownName(), fit(60)).apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }, lp(MATCH, WRAP).apply { topMargin = dp(10) })
        addView(kit.body(when {
            loading -> "${place?.name} 주변을 찾는 중…"
            routine != null -> "즐겨찾기 '$routine' 근처라 자동으로 띄우지 않았어요"
            stayMin == STOP_MIN -> "주변 정차 · 5분 이내 · 구청이 정한 허용 구간이에요"
            else -> "주변 주차 · ${durationText(stayMin)} 머물면 · 누르면 내비에서 경로를 보여줘요"
        }, Ui.BODY, if (loading) Ui.INK else Ui.INK2, if (loading) 700 else 400).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END },
            lp(MATCH, WRAP).apply { topMargin = dp(8) })
        addView(kit.segmented(stayChoices().map { (if (it == STOP_MIN) "정차" else if (it >= 1440) "종일" else durationText(it)) to (it == stayMin) }) { i ->
            stayMin = stayChoices()[i]; animFee = true; if (stayMin == STOP_MIN) loadStops() else recompute()
        }, lp(MATCH, WRAP).apply { topMargin = dp(24) })
        val body: View = if (stayMin == STOP_MIN) stopView(r, narrow) else lotList(r, narrow)
        addView(body, lp(MATCH, 0, 1f).apply { topMargin = dp(20) })
        addView(Ui.hbox(this@MainActivity, 16).apply {
            val now = java.time.ZonedDateTime.now(SEOUL)
            val saved = MonthSummary.of(tracker.state.history, now.year, now.monthValue, SEOUL).saved
            if (saved > 0) {
                addView(Ui.text(this@MainActivity, "${now.monthValue}월 혜택으로", Ui.CAP, Ui.INK2, 600))
                addView(kit.price(Ui.won(saved), Ui.CAP, true))
                addView(Ui.text(this@MainActivity, "아꼈어요", Ui.CAP, Ui.INK2, 600))
            }
            val seoul = r.options.any { it.lotId < 0 }
            // 좁은 창에서는 출처가 잘리므로 "아꼈어요"가 없을 때만 같은 줄에 두고, 있으면 바로 윗줄로 올린다
            val source = "정보 출처: 네이버 지도" + (if (seoul) " · 서울 열린데이터광장" else "") + " · ${originText(origin)}"
            if (narrow && saved > 0) {
                root.addView(Ui.text(this@MainActivity, source, 20, Ui.INK3).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END },
                    lp(MATCH, WRAP).apply { topMargin = dp(12) })
                addView(Ui.grow(this@MainActivity))
            } else addView(Ui.text(this@MainActivity, source, 20, Ui.INK3).apply {
                gravity = Gravity.END; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.START
            }, lp(0, WRAP, 1f))
            // 기어를 읽을 수 없는 차를 위한 수동 시작
            addView(Ui.text(this@MainActivity, "직접 주차 시작", Ui.CAP, Ui.INK, 700).apply {
                gravity = Gravity.CENTER; setPadding(dp(24), 0, dp(24), 0); minimumHeight = dp(60); minHeight = dp(60)
                background = Ui.rounded(Ui.SUNKEN, 18, this@MainActivity)
                Ui.tap(this, 18) { tracker.startNow() }
            })
        }, lp(MATCH, WRAP).apply { topMargin = dp(10) })
    }

    /**
     * 후보 보드. 좁은 창은 한 열(고른 줄만 아래로 펼침),
     * 넓은 창은 왼쪽에 보드(비교) · 오른쪽에 고른 곳 판과 안내 버튼(결정).
     */
    private fun lotList(r: Recommendation, narrow: Boolean): View {
        val opts = r.options.take(8)
        val rows = Ui.vbox(this).apply {
            opts.forEachIndexed { i, o ->
                val on = i == index
                // 줄을 누르면 바로 경유지로: 옆의 내비 지도에 그 주차장 경로가 그려진다
                val row = boardRow(i, o, on, lite = false, below = if (narrow && on) lotExpand(o) else null) {
                    index = i; animDetail = true; guide(o, speak = false); render()
                }
                if (animList) rise(row, i)
                addView(row, lp(MATCH, WRAP))
            }
        }
        val list = kit.scroll(rows, "lots")
        if (narrow) return list
        val sel = r.options.getOrNull(index) ?: opts.first()
        val guided = follower.guiding == sel.name
        val detail = lotSheet(sel)
        if (animDetail || animList) rise(detail, if (animList) 2 else 0)
        val sheet = kit.sheet(Ui.vbox(this, 24)).apply {
            addView(kit.scroll(detail, "sheet:${sel.lotId}"), lp(MATCH, 0, 1f))
            addView(kit.big(if (guided) "${sel.name} 안내 중" else "여기로 안내", !guided) { guide(sel); render() }, lp(MATCH, WRAP))
        }
        return Ui.hbox(this, 36).apply {
            gravity = Gravity.TOP
            addView(list, lp(0, MATCH, 1.18f))
            addView(sheet, lp(0, MATCH, 1f))
        }
    }

    /** 주의할 것(만차 잦음·기계식·협소·차종 제한)만 주홍, 발렛·높이·빈자리는 보통 딱지 */
    private fun noteTag(s: String, size: Int = 20): View =
        kit.tag(s, !(s.startsWith("발렛") || s.startsWith("무료 발렛") || s.startsWith("높이") || s.startsWith("빈자리")), size)

    private fun noteList(o: ParkingOption): List<String> =
        o.notes.filter { it != o.notes.firstOrNull() || !o.blocked } + listOfNotNull("혜택 확인 필요".takeIf { o.how?.needsReview == true })

    private fun walkShort(o: ParkingOption) = walks[o.lotId]?.let { "도보 ${it.walkMin}분" } ?: if (o.walkMeters < NEAR_M) "바로 앞" else "도보 약 ${o.walkMinutes}분"

    /**
     * 보드 한 줄.
     *  - 비교([lite] 아님, PRD lotCard): 금액 열 = 목적지 가게 혜택이면 노란 가격표, 아니면 일반 요금.
     *    다른 가게 혜택은 아래 줄에 작은 가격표로 붙인다.
     *  - 주행·정차 목록([lite], PRD .crow): 금액 열 = 혜택을 쓰면 낼 돈, 그 아래 일반 요금.
     */
    private fun boardRow(i: Int, o: ParkingOption, on: Boolean, lite: Boolean, below: View? = null, onClick: () -> Unit): View {
        val narrow = compact()
        val d = chosenDeal(o)
        val own = isOwn(o, d)
        val more = (o.deals.size - 1).takeIf { it > 0 }?.let { "외 ${it}곳" }
        val price: TextView
        val note: String?
        if (lite) {
            price = when {
                o.blocked -> kit.price("불가", 30, false, Ui.WARN)
                d != null -> kit.price(Ui.won(d.fee), 30, true, soft = !own)
                else -> kit.price(Ui.won(o.fee), 30, false)
            }
            note = if (own) "목적지 가게 혜택" else if (d != null) o.normalFee?.takeIf { it != d.fee }?.let { "일반 ${Ui.won(it)}" } else null
        } else {
            val (label, fee) = headFee(o)
            val size = fit(40).coerceAtLeast(34)
            // 일반 요금을 모르는 곳은 아는 금액(다른 가게 혜택)을 금액 열에 올린다
            val viaOther = !o.blocked && !own && o.normalFee == null && d != null
            price = when {
                o.blocked -> kit.price(fee, 26, false, Ui.WARN)
                viaOther -> kit.price(Ui.won(d!!.fee), size, true, soft = true)
                o.normalFee == null && !own -> kit.price(fee, Ui.CAP, false, Ui.INK3)
                else -> kit.price(fee, size, own)
            }
            note = when { viaOther -> "다른 가게 혜택"; o.normalFee == null && !own && !o.blocked -> null; else -> label }
            if (!o.blocked) rollFee("row:${o.lotId}", price, if (own || viaOther) d?.fee else o.normalFee)
        }
        val lines = buildList {
            // 주행·정차 목록은 열이 좁다: 주의 딱지를 이름 옆이 아니라 아래 줄 앞에 둔다 (이름이 잘리지 않게)
            if (lite) (d?.reason ?: o.rule?.let { FeeText.rule(it) })?.let { text ->
                val warnNote = o.notes.firstOrNull()
                add(if (warnNote == null) kit.line(text) else Ui.hbox(this@MainActivity, 10).apply { addView(noteTag(warnNote)); addView(kit.line(text), lp(0, WRAP, 1f)) })
            } ?: o.notes.firstOrNull()?.let { add(kit.left(noteTag(it))) }
            else {
                // 넓은 창은 요금 규칙이 오른쪽 판에 있다. 좁은 창은 줄에 같이 둔다
                if (narrow || d == null) o.rule?.let { add(kit.line(FeeText.rule(it), color = Ui.INK3)) }
                if (d != null) {
                    val text = d.reason + (more?.let { " · $it" } ?: "")
                    add(if (own || o.normalFee == null) kit.line(text) else kit.line(text, Ui.won(d.fee)))
                }
            }
        }
        val notes = if (lite) o.notes.take(1) else noteList(o).take(2)
        // 좁은 창: 딱지를 이름 옆에 두면 이름이 잘린다. 이름 아래 한 줄로 내린다
        val tagLine = if (narrow && !lite) Ui.hbox(this, 8).apply { addView(kit.tag(walkShort(o))); notes.forEach { addView(noteTag(it)) } } else null
        val tags = if (narrow || lite) emptyList() else notes.map { noteTag(it) }
        return kit.row(i + 1, on, follower.guiding == o.name, o.name, tags, listOfNotNull(tagLine) + lines, if (narrow && !lite) null else walkShort(o), price, note,
            dim = o.blocked, below = below, minH = if (lite) 104 else 116, onClick = onClick)
    }

    /** 이 주차장의 혜택 가게들. 여러 곳이면 고를 수 있다 (PRD "다른 혜택 가게로 바꾸기") */
    private fun dealBox(o: ParkingOption, always: Boolean): View? {
        val d = chosenDeal(o) ?: return null
        if (o.deals.size < 2 && !always) return null
        return Ui.vbox(this, 10).apply {
            addView(Ui.text(this@MainActivity, if (o.deals.size > 1) "다른 혜택 가게로 바꾸기" else "혜택 가게", 24, Ui.INK, 700).apply { setPadding(dp(4), 0, 0, dp(4)) })
            if (o.deals.size > 1) o.deals.take(4).forEach { x ->
                addView(kit.deal(x.storeName, cond(x), Ui.won(x.fee), x.storeName == d.storeName, soft = !isOwn(o, x)) {
                    pickedDeal[o.lotId] = x.storeName; animFee = true; render()
                }, lp(MATCH, WRAP))
            } else addView(kit.deal(d.storeName, cond(d), Ui.won(d.fee), null, soft = !isOwn(o, d)), lp(MATCH, WRAP))
        }
    }

    /** 넓은 창 오른쪽 판: 고른 곳의 전부 (PRD lotCard 펼친 모습) */
    private fun lotSheet(o: ParkingOption): LinearLayout = Ui.vbox(this, 20).apply {
        val d = chosenDeal(o)
        val own = isOwn(o, d)
        val (feeLabel, fee) = headFee(o)
        addView(kit.title(o.name, Ui.TITLE).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
        o.rule?.let { addView(kit.body(FeeText.rule(it), 24)) }
        addView(Ui.hbox(this@MainActivity, 8).apply {
            addView(kit.tag(walkShort(o), false, 22))
            noteList(o).take(3).forEach { addView(noteTag(it, 22)) }
        })
        addView(Ui.hbox(this@MainActivity, 24).apply {
            gravity = Gravity.BOTTOM
            setPadding(0, dp(6), 0, dp(6))
            addView(Ui.vbox(this@MainActivity, 10).apply {
                // 일반 요금을 모르는 곳은 아는 금액(다른 가게 혜택)을 올린다 (보드 줄과 같은 규칙)
                val viaOther = !o.blocked && !own && o.normalFee == null && d != null
                addView(Ui.text(this@MainActivity, if (viaOther) "${d!!.storeName} 혜택을 쓰면" else feeLabel, 24, Ui.INK3, 600).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                val big = when {
                    viaOther -> kit.price(Ui.won(d!!.fee), 84, true, soft = true)
                    else -> kit.price(fee, if (fee.endsWith("원")) 84 else 40, own && !o.blocked, if (o.blocked) Ui.WARN else if (fee.endsWith("원")) Ui.INK else Ui.INK3)
                }
                if (!o.blocked) rollFee("sheet", big, if (own || viaOther) d?.fee else o.normalFee)
                addView(big, lp(WRAP, WRAP))
            }, lp(0, WRAP, 1f))
            addView(Ui.vbox(this@MainActivity, 6).apply {
                gravity = Gravity.END
                addView(Ui.text(this@MainActivity, "목적지까지", 24, Ui.INK3, 600), lp(WRAP, WRAP))
                addView(Ui.text(this@MainActivity, walkText(o).removePrefix("목적지 "), 28, Ui.INK, 800), lp(WRAP, WRAP))
            })
        })
        addView(kit.hairline())
        dealBox(o, always = true)?.let { addView(it) }
    }

    /** 좁은 창에서 고른 줄 아래: 걷는 길과 다른 혜택 가게 고르기 */
    private fun lotExpand(o: ParkingOption): View = Ui.vbox(this, 14).apply {
        setPadding(dp(70), 0, 0, 0)
        addView(kit.wide("목적지까지", walkText(o).removePrefix("목적지 ")))
        dealBox(o, always = false)?.let { addView(it) }
    }

    /** 정차 (PRD S10). 데이터가 있는 구만 구간을 보여주고, 없으면 그렇게 말한다 */
    private fun stopView(r: Recommendation, narrow: Boolean): View {
        val st = stops
        val lotsCol = kit.col(4, kit.listHead("5분 넘게 머물면", "누르면 바로 경유지 변경"),
            *r.options.take(5).mapIndexed { i, o -> boardRow(i, o, false, lite = true) { index = i; stayMin = shownDefaultStay(); guide(o, speak = false); recompute() } }.toTypedArray())
        val left: View = when {
            st == null -> kit.col(12, kit.label("정차 구간을 찾는 중…"))
            !st.covered -> kit.col(18,
                kit.label("이 지역은 정차 구간 정보가 없어요"),
                kit.title("5분이라도\n주차장을 이용해 주세요", Ui.TITLE),
                kit.body(if (st.coveredGus.isEmpty()) "정차 정보를 받지 못했어요." else "지금은 ${st.coveredGus.joinToString(" · ")}만 알려 드려요."))
            st.segments.isEmpty() -> kit.col(18, kit.label("근처에 정차 허용 구간이 없어요"), kit.body("${st.gu} ${st.road ?: ""} 주변"))
            else -> {
                val top = st.segments.first()
                kit.col(18,
                    kit.label(if (top.allowedNow) "잠깐이면 여기 세워도 돼요" else "지금은 정차 허용 시간이 아니에요", if (top.allowedNow) Ui.INK2 else Ui.WARN),
                    kit.title(top.desc, 40),
                    kit.body(listOfNotNull(top.road, if (top.sameRoad) "같은 도로" else null, top.today?.let { "오늘 $it" } ?: "오늘 허용 없음", top.lengthM?.let { "${it}m" }).joinToString(" · ")),
                    if (st.segments.size > 1) kit.table(st.segments.drop(1).take(3).map { (if (it.sameRoad) "같은 도로" else it.road) to it.desc }) else null,
                    kit.warn("소화전 · 교차로 모퉁이 · 버스정류소 · 횡단보도 앞은 5분 안이라도 안 돼요"))
            }
        }
        return kit.scroll(if (narrow) kit.col(30, left, lotsCol) else Ui.hbox(this, 48).apply {
            gravity = Gravity.TOP
            addView(kit.sheet(left as LinearLayout), lp(0, WRAP, 1f)); addView(lotsCol, lp(0, WRAP, 1.1f))
        }, "stops")
    }

    private fun shownDefaultStay() = dest?.store?.stayMin ?: 60

    /**
     * 주행 중 (PRD S3·S4·S5) · 경유지 곧 도착 (S6). 고른 곳 하나와 낼 돈을 가장 크게, 옆에 다른 후보 6줄.
     * 새 목적지를 찾는 동안에는 이전 안내를 흐리게 두고 한 줄만 띄운다 (PRD G7: 전체 화면 전환 없음).
     */
    private fun drivingScreen(r: Recommendation): View = kit.pad(Ui.vbox(this, 0), compact()).apply {
        val narrow = compact()
        val opts = r.options
        val guided = opts.indexOfFirst { it.name == follower.guiding }
        val topIndex = if (guided >= 0) guided else index
        val top = opts[topIndex]
        val arrived = arrivedAt != null && arrivedAt == top.name
        val loading = place != null && place != shownPlace
        val d = chosenDeal(top)
        val pay = if (d != null && !isOwn(top, d)) d.fee else top.fee
        val dimmed = mutableListOf<View>()
        fun <T : View> T.dimmable(): T = also { dimmed += it }
        val hero = kit.left(kit.price(Ui.won(pay), if (narrow) 104 else 148, pay != null && (d != null || top.freeMin > 0), soft = d != null && !isOwn(top, d))).dimmable()
        val line = if (arrived) kit.body("세우고 ${walkText(top)}", 30, Ui.INK, 600)
            else kit.body(listOfNotNull("${durationText(stayMin)} 머물면", walkText(top), top.normalFee?.let { "일반 주차 시 ${Ui.won(it)}" }).joinToString(" · "))
        val left = kit.col(18,
            if (loading) kit.notice("${place?.name} 기준으로 다시 찾는 중") else null,
            kit.label(when { arrived -> "곧 도착해요"; guided >= 0 -> "안내 중"; else -> "주차는 여기로" }).dimmable(),
            kit.title(top.name, fit(60)).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }.dimmable(),
            (d?.reason ?: top.reason).takeIf { it.isNotEmpty() }?.let { kit.left(kit.chip(it)) }?.dimmable(),
            kit.spring().takeIf { !narrow },
            hero,
            line.dimmable(),
            kit.spring().takeIf { !narrow },
            // 새 응답이 올 때까지 안내 버튼은 막는다 (엉뚱한 곳 안내 방지)
            if (guided < 0 && !arrived) kit.big("여기로 안내", enabled = !loading) { guide(top); render() } else null,
        )
        if (loading) dimmed.forEach { it.alpha = 0.35f }
        val others = opts.withIndex().filter { it.index != topIndex }.take(6)
        val list = kit.col(4, kit.listHead(if (arrived) "자리가 없으면 · 근처 다른 곳 · ${others.size}곳" else "다른 후보 · ${others.size}곳",
            if (arrived) "누르면 바로 경유지 변경" else "밀어서 더 보기"),
            kit.scroll(Ui.vbox(this@MainActivity).apply {
                others.forEach { (i, o) -> addView(boardRow(i, o, false, lite = true) { index = i; guide(o); render() }, lp(MATCH, WRAP)) }
            }, "others"))
        if (loading) list.alpha = 0.35f
        addView(kit.topBar(if (arrived) "경유지 근처" else remainText(), micPill()), lp(MATCH, WRAP))
        addView(kit.twoCol(narrow, left, list), lp(MATCH, 0, 1f).apply { topMargin = dp(16) })
    }

    /** 주차 직후 (PRD S7) · 주차 중 (S15) · 정차 중 (S16) */
    private fun parkedScreen(s: ParkingSession): View {
        if (s.stop) return stopTimerScreen(s)
        val now = System.currentTimeMillis()
        return if (s.elapsedMin(now) < 10 && s.farFrom == null) parkedNowScreen(s, now) else parkingScreen(s, now)
    }

    private fun walkBack(s: ParkingSession, toCar: Boolean): View {
        val w = if (s.walkMeters < NEAR_M) "바로 앞" else "걸어서 ${s.walkMinutes}분 · ${s.walkMeters}m"
        return kit.wide(if (toCar) "차로 돌아가는 길" else "${s.destName}까지", w)
    }

    /** 이 주차장의 다른 혜택 가게 (주차 직후 바꾸기) */
    private fun lotDeals(s: ParkingSession): List<OtherDeal> =
        recommendation?.options?.firstOrNull { it.lotId == s.lotId }?.deals.orEmpty()

    /** 넓은 창에서는 오른쪽 열을 떠 있는 판에 담는다 */
    private fun side(v: LinearLayout): View? = when {
        v.childCount == 0 -> null
        compact() -> kit.scroll(v)
        // 판은 내용만큼만. 빈 흰 판이 화면을 채우지 않게 한다
        else -> FrameLayout(this).apply {
            addView(kit.sheet(Ui.vbox(this@MainActivity)).apply { addView(kit.scroll(v), lp(MATCH, WRAP)) }, FrameLayout.LayoutParams(MATCH, WRAP))
        }
    }

    private fun parkedNowScreen(s: ParkingSession, now: Long): View = kit.pad(Ui.vbox(this, 0), compact()).apply {
        val planned = s.plannedMin ?: shownDefaultStay()
        val rule = s.rule
        val left = kit.col(20,
            s.farFrom?.let { placeCheck(s) },
            kit.label("${s.lotName}에 세웠어요"),
            kit.title(if (s.hasBenefit) "${s.storeName}\n이용하고 주차 등록하세요" else "${s.lotName}\n주차 시간을 챙겨 드려요", fit(56)),
            kit.spring().takeIf { !compact() },
            rule?.let {
                val f = ExitFee.of(it, planned, s.freeMin)
                if (s.hasBenefit) kit.duo(Triple("등록하면", Ui.won(f.withBenefit), true), Triple("안 하면 · ${durationText(planned)}", Ui.won(f.withoutBenefit), false), fit(64))
                else kit.duo(Triple("${durationText(planned)} 머물면", Ui.won(f.withoutBenefit), false), null, fit(64))
            },
        )
        if (!compact()) left.addView(walkBack(s, false), lp(MATCH, WRAP))
        val picks = lotDeals(s).filter { it.storeName != s.storeName }
        val right = kit.col(14,
            kit.table(listOfNotNull(
                "세운 시각" to clock(s.startedAt),
                "머물 예정" to "${durationText(planned)} · ${clock(s.startedAt + planned * 60_000L)}까지",
                rule?.let { "요금 규칙" to FeeText.rule(it) },
                s.redeemHint?.takeIf { s.hasBenefit }?.let { "등록 방법" to it },
            )),
            if (picks.isNotEmpty()) Ui.text(this@MainActivity, "다른 혜택 가게로 바꾸기", 24, Ui.INK, 700).apply { setPadding(dp(4), dp(14), 0, 0) } else null,
            *picks.take(3).map { x -> kit.deal(x.storeName, cond(x), Ui.won(x.fee), false, soft = true) { tracker.switchDeal(x) } }.toTypedArray(),
        )
        addView(kit.topBar("P · 주차 시작", micPill()), lp(MATCH, WRAP))
        addView(kit.twoCol(compact(), left, side(right), 1.15f), lp(MATCH, 0, 1f).apply { topMargin = dp(16) })
        // 좁은 창: 걷는 길은 스크롤 밖 맨 아래에 고정
        if (compact()) addView(walkBack(s, false), lp(MATCH, WRAP).apply { topMargin = dp(14) })
    }

    private fun parkingScreen(s: ParkingSession, now: Long): View = kit.pad(Ui.vbox(this, 0), compact()).apply {
        val e = s.elapsedMin(now)
        val rule = s.rule
        val fee = s.fee(now)
        val planned = s.plannedMin
        val left = kit.col(18,
            s.farFrom?.let { placeCheck(s) },
            kit.label("${s.lotName}에 세운 지"),
            Ui.text(this@MainActivity, durationText(e), if (compact()) 96 else 132, Ui.INK, 800).apply { letterSpacing = -0.03f; maxLines = 1 },
            kit.body("${clock(s.startedAt)}부터" + (planned?.let { p -> if (e <= p) " · 예정(${durationText(p)})까지 ${durationText(p - e)} 남았어요" else " · 예정보다 ${durationText(e - p)} 더 머물렀어요" } ?: "")),
            kit.spring().takeIf { !compact() },
            fee?.let {
                if (s.hasBenefit) kit.duo(Triple("지금 나가면 · 등록했으면", Ui.won(it.withBenefit), true), Triple("등록 안 했으면", Ui.won(it.withoutBenefit), false), fit(64))
                else kit.duo(Triple("지금 나가면", Ui.won(it.withoutBenefit), false), null, fit(64))
            },
            rule?.let { r -> nextStepText(s, r, e)?.let { kit.warn(it) } },
        )
        if (!compact()) left.addView(walkBack(s, true), lp(MATCH, WRAP))
        val right = kit.col(16,
            rule?.let { Ui.text(this@MainActivity, "요금이 오르는 계단", 24, Ui.INK, 700).apply { setPadding(dp(4), 0, 0, dp(4)) } },
            rule?.let { r -> kit.ladder(FeeText.ladder(r, e).map { Triple(it.label, Ui.won(it.fee), it.now) }) },
            if (s.hasBenefit) kit.table(listOfNotNull(
                "등록할 가게" to s.reason.ifEmpty { s.storeName ?: "" },
                s.redeemHint?.let { "등록 방법" to it })) else null,
        )
        addView(kit.topBar("P · 주차 중", micPill()), lp(MATCH, WRAP))
        addView(kit.twoCol(compact(), left, side(right), 1.15f), lp(MATCH, 0, 1f).apply { topMargin = dp(16) })
        if (compact()) addView(walkBack(s, true), lp(MATCH, WRAP).apply { topMargin = dp(14) })
    }

    /** 다음에 낼 돈이 오르는 시각 한 줄 */
    private fun nextStepText(s: ParkingSession, r: FeeRule, e: Int): String? {
        val left = s.freeLeftMin(System.currentTimeMillis())
        return if (s.hasBenefit && left != null && left > 0) {
            val n = FeeText.nextStep(r, e, s.freeMin) ?: return "${clock(s.startedAt + s.freeMin * 60_000L)}까지 무료예요"
            "${clock(s.startedAt + n.atMin * 60_000L)}에 무료가 끝나요 · 등록해도 ${Ui.won(n.fee)}부터 붙어요"
        } else {
            val n = FeeText.nextStep(r, e) ?: return "하루 최대 요금이에요 · 더 오르지 않아요"
            "${clock(s.startedAt + n.atMin * 60_000L)}에 ${Ui.won(n.fee)}으로 올라요" + if (n.dailyMax) " · 하루 최대" else ""
        }
    }

    /**
     * 정차 중 5분 타이머 (PRD S16). 큰 글자는 분 단위("3분 남았어요"), 마지막 1분만 초.
     * 1분 = 막대 하나. 남은 시간만큼 막대가 차 있고 지나면 위에서부터 빠진다.
     */
    private fun stopTimerScreen(s: ParkingSession): View = kit.pad(Ui.vbox(this, 0), compact()).apply {
        val now = System.currentTimeMillis()
        val end = s.startedAt + STOP_MIN * 60_000L
        val leftS = ((end - now) / 1000).toInt().coerceAtLeast(0)
        val lastMinute = leftS < 60
        val over = leftS == 0
        val hot = if (lastMinute) Ui.WARN else Ui.INK
        val bigText = when { over -> "끝났어요"; lastMinute -> "${leftS}초"; else -> "${leftS / 60}분" } // 분은 내림: 실제보다 넉넉해 보이지 않게 (PRD S16)
        val head = kit.col(18,
            kit.label("정차 중"),
            kit.title(s.lotName, if (compact()) 34 else Ui.TITLE).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END },
            kit.spring().takeIf { !compact() },
            Ui.hbox(this@MainActivity, 18).apply {
                gravity = Gravity.BOTTOM
                addView(Ui.text(this@MainActivity, bigText, if (compact()) 112 else if (over) 132 else 180, hot, 800).apply { letterSpacing = -0.03f; maxLines = 1 })
                if (!over) addView(kit.body("남았어요", 36, hot, 600), lp(WRAP, WRAP).apply { bottomMargin = dp(if (compact()) 14 else 26) })
            },
            kit.body("${clock(s.startedAt)} 정차 시작 · ${clock(end).substringAfter(" ")}까지", 28),
            kit.spring().takeIf { !compact() },
        )
        val warn = kit.warn(when {
            over -> "정차 5분이 지났어요 · 더 있으려면 주차장으로 옮겨 주세요"
            lastMinute -> "${leftS}초 뒤 정차 시간이 끝나요 · 더 있으려면 주차장으로"
            else -> "소화전 · 교차로 모퉁이 · 버스정류소 · 횡단보도 앞은 5분 안이라도 안 돼요"
        })
        // 막대 5개: i 번째 막대는 남은 (leftS/60 - i) 만큼 아래에서부터 찬다
        val remain = leftS / 60.0
        val bars = kit.bars((0 until STOP_MIN).map { (remain - it).coerceIn(0.0, 1.0) }, if (compact()) 150 else 760, if (!lastMinute && Ui.night) Ui.INK2 else hot)
        val button = kit.big("5분 넘게 걸리면 · 주차장 보기", false) { tracker.cancelSession(); stayMin = shownDefaultStay(); recompute() }
        addView(kit.topBar("P · 정차 중", micPill()), lp(MATCH, WRAP))
        if (compact()) addView(kit.col(22, head, bars, kit.spring(), warn, button), lp(MATCH, 0, 1f).apply { topMargin = dp(16) })
        else addView(Ui.hbox(this@MainActivity, 64).apply {
            addView(kit.col(22, head.also { it.tag = null }, warn, button).also { c ->
                (head.layoutParams as LinearLayout.LayoutParams).apply { height = 0; weight = 1f }
            }, lp(0, MATCH, 1.15f))
            addView(kit.sheet(Ui.vbox(this@MainActivity)).apply {
                addView(Ui.hbox(this@MainActivity).apply {
                    addView(Ui.text(this@MainActivity, "남은 시간", 24, Ui.INK, 700)); addView(Ui.grow(this@MainActivity))
                    addView(Ui.text(this@MainActivity, "한 칸에 1분", Ui.CAP, Ui.INK3))
                }, lp(MATCH, WRAP))
                addView(kit.spring())
                addView(bars, lp(MATCH, WRAP))
            }, lp(0, MATCH, 1f))
        }, lp(MATCH, 0, 1f).apply { topMargin = dp(16) })
    }

    /** 출차 확인 (PRD S8). 주행 중이면 표를 빼고 질문·금액·버튼만 */
    private fun reminderScreen(rm: ExitReminder, drive: Boolean): View = kit.pad(Ui.vbox(this, 0), compact()).apply {
        val s = rm.session
        val fee = rm.fee
        val store = s.hasBenefit
        val mins = fee?.minutes ?: s.elapsedMin(rm.endedAt)
        val left = kit.col(18,
            kit.label("출발 전에 확인하세요"),
            kit.title(if (store) "${s.storeName}에서\n주차 등록하셨나요?" else "${s.lotName}\n주차 요금을 확인하세요", fit(56)),
            fee?.let {
                if (store) kit.duo(Triple("등록했으면", Ui.won(it.withBenefit), true), Triple("안 했으면", Ui.won(it.withoutBenefit), false), fit(64))
                else kit.duo(Triple("${durationText(mins)} 주차", Ui.won(it.withoutBenefit), false), null, fit(64))
            },
            kit.spring(),
            kit.big(if (store) "등록했어요" else "확인") { tracker.dismissReminder(registered = store) },
            if (store) kit.big("아직이요", false) { tracker.dismissReminder(registered = false) } else null,
            Ui.text(this@MainActivity, "답은 이번 달 아낀 돈에 기록돼요", Ui.CAP, Ui.INK3).apply { gravity = Gravity.CENTER },
        )
        addView(kit.topBar("P → D · 한 번만 물어요", micPill()), lp(MATCH, WRAP))
        if (drive) return@apply addView(left, lp(MATCH, 0, 1f).apply { topMargin = dp(16) })
        val r = s.rule
        val now = java.time.ZonedDateTime.now(SEOUL)
        val sum = MonthSummary.of(tracker.state.history, now.year, now.monthValue, SEOUL)
        val right = kit.col(16,
            kit.table(listOfNotNull(
                "주차장" to s.lotName,
                "세운 시간" to "${clock(s.startedAt)} → ${clock(rm.endedAt).removePrefix("오전 ").removePrefix("오후 ")} · ${durationText(mins)}",
                s.plannedMin?.let { p -> "예정보다" to if (mins > p) "${durationText(mins - p)} 더 머물렀어요" else if (mins < p) "${durationText(p - mins)} 일찍 나왔어요" else "예정대로예요" },
                r?.let { "안 했을 때" to FeeText.formula(it, mins) },
                (r as? FeeRule.Metered)?.dailyMax?.let { m -> "하루 최대" to Ui.won(m) + if ((fee?.withoutBenefit ?: 0) >= m) " · 이미 최대예요" else "" },
                s.redeemHint?.takeIf { store }?.let { "등록 방법" to it },
            )),
            kit.hairline(),
            kit.duo(Triple("${now.monthValue}월 혜택으로 아낀 돈", Ui.won(sum.saved), sum.saved > 0), Triple("${now.monthValue}월 주차", "${sum.count}번", false), 44)
                .apply { background = null },
        )
        addView(kit.twoCol(compact(), left, side(right), 1.15f), lp(MATCH, 0, 1f).apply { topMargin = dp(16) })
    }

    /** 추천 없음·실패·불러오는 중 (PRD S12). 실패에는 언제나 다음 행동 버튼이 있다 */
    private fun emptyScreen(): View = kit.pad(Ui.vbox(this, 0), compact()).apply {
        val p = place
        val noLots = recommendation?.options?.isEmpty() == true
        addView(kit.topBar(""), lp(MATCH, WRAP))
        addView(kit.spring())
        addView(kit.col(24,
            when {
                failed -> kit.title("추천을 불러오지 못했어요", fit(56))
                noLots -> kit.title("800m 안에 아는 주차장이 없어요", fit(56))
                else -> kit.title("${p?.name ?: "목적지"} 주변\n주차를 찾고 있어요", fit(56))
            },
            when {
                failed -> kit.body("인터넷 연결이 불안정해요. 내비 안내는 그대로 쓸 수 있어요.", 30)
                noLots -> kit.body("지금은 서울 지하철 2호선 역 주변만 알려 드려요.", 30)
                else -> null
            },
            if (failed) kit.left(kit.big("다시 시도") { failed = false; render(); load() }.apply { minimumWidth = dp(420) }) else null,
        ), lp(MATCH, WRAP))
        addView(kit.spring())
        addView(kit.spring())
    }

    /** 추천과 다른 곳에 세운 것 같을 때: 차 근처 후보 중 고르거나 "여기 맞아요". */
    private fun placeCheck(s: ParkingSession): View = kit.col(12,
        kit.warn("${s.lotName}에서 ${s.farFrom!!.distanceM}m 떨어진 곳이에요 · 어디에 세우셨나요?"),
        *ParkCheck.guesses(s, recommendation?.options.orEmpty()).map { o ->
            kit.deal(o.name, "차 근처 주차장 · ${o.rule?.let { FeeText.rule(it) } ?: "요금 정보 없음"}", "여기", false, tagged = false) {
                tracker.relocate(Candidate(o, recommendation?.storeName, s.destName, s.plannedMin))
            }
        }.toTypedArray(),
        kit.big("${s.lotName} 맞아요", false) { tracker.confirmPlace() },
    )

    /**
     * 목적지 없음 (PRD S1). 이 앱이 뭘 하는지 한 문장 + 버튼 하나 + 실제 보드 미리보기.
     * 미리보기는 데모 목적지의 실제 계산 결과를 정적으로 보여준다: 노란 "0원"이 먼저 보이게.
     */
    private fun waitingScreen(): View {
        val narrow = compact()
        val intro = Ui.vbox(this, 0).apply {
            addView(kit.title("목적지 주변\n주차 혜택", if (narrow) 60 else 84).apply { setLineSpacing(0f, 1.1f) })
            addView(kit.body("같은 건물 다른 가게 혜택까지 찾아서\n실제로 낼 돈으로 비교해 드려요", if (narrow) 28 else 32).apply { setLineSpacing(0f, 1.45f) },
                lp(MATCH, WRAP).apply { topMargin = dp(24) })
            addView(kit.left(Ui.button(this@MainActivity, "내비에서 목적지 찾기", true, 112, 32) {
                if (!navi.openNavigation()) voice.speak("내비에 목적지를 설정해 주세요.")
            }.apply { minimumWidth = dp(460) }), lp(MATCH, WRAP).apply { topMargin = dp(40) })
        }
        val preview = Ui.vbox(this, 0).apply {
            setPadding(dp(20), dp(32), dp(20), dp(14))
            background = Ui.rounded(Ui.SURFACE, 36, this@MainActivity)
            addView(Ui.hbox(this@MainActivity, 16).apply {
                setPadding(dp(24), 0, dp(24), dp(12))
                addView(Ui.text(this@MainActivity, "필앤필버거 강남본점", 34, Ui.INK, 800).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, lp(0, WRAP, 1f))
                addView(Ui.text(this@MainActivity, "1시간 30분 머물면", 24, Ui.INK3))
            }, lp(MATCH, WRAP))
            addView(kit.row(1, true, false, "아크플레이스", emptyList(),
                listOf(kit.line("필앤필버거 강남본점 이용 시 2시간 무료")), null, kit.price("0원", 40, true), "혜택 적용"), lp(MATCH, WRAP))
            addView(kit.row(2, false, false, "테헤란로 146 주차장", listOf(kit.tag("도보 1분")),
                listOf(kit.line("1시간 무료 · 이후 10분당 1,000원", color = Ui.INK3), kit.line("화이팅정형외과의원 이용 시 무료 · 외 2곳", "0원")),
                null, kit.price("3,000원", 40, false), "일반 주차 시").apply { background = null }, lp(MATCH, WRAP))
        }
        return kit.pad(Ui.vbox(this, 0), narrow).apply {
            addView(kit.topBar(""), lp(MATCH, WRAP))
            if (narrow) {
                addView(intro, lp(MATCH, WRAP).apply { topMargin = dp(48) })
                addView(preview, lp(MATCH, WRAP).apply { topMargin = dp(56) })
            } else addView(Ui.hbox(this@MainActivity, 64).apply {
                addView(intro, lp(0, WRAP, 1f))
                addView(preview, lp(0, WRAP, 1.1f))
            }, lp(MATCH, 0, 1f).apply { bottomMargin = dp(72) })
        }
    }

    // ---- 권한 안내

    /** "나중에"는 이번 실행에서만 넘긴다. 다음에 켜면 다시 안내한다. */
    private var permitSkipped = false
    private fun permitAsked() = getPreferences(MODE_PRIVATE).getBoolean(KEY_PERMIT_ASKED, false)
    private fun missingPermits() = listOf(Manifest.permission.POST_NOTIFICATIONS, FUSED_LOCATION)
        .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

    /**
     * 시스템 권한 창은 앱이 모양도 문구도 바꿀 수 없고, Pleos 차 위치 권한은 "알 수 없는 작업"이라고만 나온다.
     * 그래서 창을 띄우기 전에 무엇을 왜 허용하는지 우리 화면에서 먼저 말한다.
     */
    private fun permitScreen(): View = kit.pad(Ui.vbox(this, 0), compact()).apply {
        val need = missingPermits()
        val rows = buildList {
            if (Manifest.permission.POST_NOTIFICATIONS in need) add("알림" to "출차할 시간이 되면 알려 드려요")
            if (FUSED_LOCATION in need) add("차 위치" to "추천과 다른 곳에 세웠는지 확인해요")
        }
        addView(kit.topBar(""), lp(MATCH, WRAP))
        addView(kit.spring())
        addView(kit.col(28,
            kit.title(if (rows.size > 1) "시작하기 전에\n두 가지만 허용해 주세요" else "시작하기 전에\n${rows.first().first}${if (rows.first().first == "알림") "을" else "를"} 허용해 주세요", fit(56)),
            FrameLayout(this@MainActivity).apply {
                addView(kit.sheet(Ui.vbox(this@MainActivity, 0).apply { addView(kit.table(rows), lp(MATCH, WRAP)) }),
                    FrameLayout.LayoutParams(if (compact()) MATCH else dp(820), WRAP))
            },
            kit.body("이어서 뜨는 창에서 ‘허용’을 눌러 주세요. 위치는 주차할 때 한 번만 읽어요.", 26, Ui.INK3),
            Ui.hbox(this@MainActivity, 16).apply {
                addView(Ui.button(this@MainActivity, "허용하고 시작", true, 112, 32) { requestPermissions(need.toTypedArray(), REQ_PERMIT) }.apply { minimumWidth = dp(400) })
                addView(Ui.button(this@MainActivity, "나중에", false, 112, 30) { permitSkipped = true; render() })
            },
        ), lp(MATCH, WRAP))
        addView(kit.spring())
        addView(kit.spring())
    }

    // ---- 음성으로 묻기 (SpeechToText)

    private val listener by lazy { Listener(this, ::onListen) }
    /** 듣는 중·들은 말을 화면 위에 띄우는 한 줄. null 이면 숨김. */
    private var heard: String? = null

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
        // 허용하든 안 하든 한 번 물었으면 다시 막아서지 않는다
        if (requestCode == REQ_PERMIT) { getPreferences(MODE_PRIVATE).edit().putBoolean(KEY_PERMIT_ASKED, true).apply(); render() }
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

    /** 머무를 시간 칩: 정차(5분)·30분·1시간·2시간·3시간·5시간·8시간·12시간·종일 + 이 가게 기본 체류 */
    private fun stayChoices(): List<Int> = (listOf(STOP_MIN, 30, 60, 120, 180, 300, 480, 720, 1440) + shownDefaultStay()).distinct().sorted()

    private fun originText(origin: Origin?) = when (origin) {
        Origin.SERVER -> "실시간"
        Origin.CACHE -> "저장된 정보 (서버 연결 안 됨)"
        Origin.SNAPSHOT -> "내장 데이터 (오프라인)"
        null -> "-"
    }

    private companion object {
        const val EXTRA_SKIP_MIN = "skipMin"
        const val REQ_MIC = 2
        const val REQ_PERMIT = 3
        const val KEY_PERMIT_ASKED = "permitAsked"
        /** 걷는 거리(어림)가 이보다 짧으면 "목적지 바로 앞" */
        const val NEAR_M = 40
        const val FUSED_LOCATION = "pleos.car.permission.FUSED_LOCATION"
        const val EXTRA_SAY = "say"
        const val EXTRA_DEST = "dest"
        const val EXTRA_REMAIN = "remain"
        const val EXTRA_ARRIVE = "arrive"
        const val TAG = "ParkDeal"
        /** 정차 = 5분 이내 (도로교통법 제2조) */
        const val STOP_MIN = 5
        val SEOUL: java.time.ZoneId = java.time.ZoneId.of("Asia/Seoul")
    }
}
