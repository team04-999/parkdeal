package com.parkdeal.app.parking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.parkdeal.app.MainActivity
import com.parkdeal.core.parking.Gear
import com.parkdeal.core.parking.ParkingEvent
import com.parkdeal.core.parking.ParkingSession
import com.parkdeal.core.parking.ParkingTalk
import com.parkdeal.core.parking.ParkingTracker

/**
 * 화면이 가려져도(주행 중) 기어를 받아 주차를 챙기는 포그라운드 서비스.
 *  - P 진입 → 주차 시작, 무료 종료 15분 전 알림 + 음성
 *  - 주차 중 D·R → 출차 리마인드 알림 + 음성
 * 할 일이 있을 때만 돈다: 주차할 후보가 있거나 주차 중일 때. 출차 안내를 마치면 스스로 멈춘다.
 */
class ParkingService : Service() {
    private lateinit var tracker: ParkingTracker
    private lateinit var gears: VehicleGearSource
    private lateinit var voice: Voice
    private val handler = Handler(Looper.getMainLooper())
    private val warnTask = Runnable { onFreeWarn() }
    /** 주차 시각이 바뀌면(시간 건너뛰기, 다른 화면에서 시작) 경고 타이머를 다시 건다. */
    private val onState: (com.parkdeal.core.parking.ParkingState) -> Unit = { scheduleWarn() }

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        tracker = Parking.tracker(this)
        voice = Voice(this)
        gears = VehicleGearSource(this)
        gears.start(onGear = ::onGear, onUnavailable = { Log.w(TAG, "기어를 읽을 수 없음: ${it.message}") })
        // 기준 기어를 잡은 뒤: 앱이 꺼진 사이 출발했으면 출차로 처리
        handler.postDelayed({ handle(tracker.settle()) }, ParkingTracker.SETTLE_MS + 200)
        scheduleWarn()
        Parking.observe(onState)
        Log.d(TAG, "service start session=${tracker.state.session?.lotName} candidate=${tracker.candidate?.option?.name}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!needed(tracker)) stopSelf()
        return START_STICKY
    }

    private fun onGear(gear: Gear) {
        Log.d(TAG, "gear $gear")
        handle(tracker.onGear(gear))
    }

    private fun handle(event: ParkingEvent?) {
        when (val e = event) {
            is ParkingEvent.Started -> {
                Log.d(TAG, "parking started ${e.session.lotName} free=${e.session.freeMin}")
                voice.speak(ParkingTalk.parked(e.session))
                scheduleWarn()
                // 추천과 다른 곳에 세웠는지 차 위치로 확인한다
                CarLocation.once(this) { lat, lng ->
                    val far = tracker.checkPlace(lat, lng) ?: return@once
                    val text = "${e.session.lotName}에서 ${far.distanceM}m 떨어진 곳이에요. 다른 곳에 세우셨으면 화면에서 골라 주세요."
                    Log.d(TAG, "place check: $text")
                    alert(NOTI_PLACE, "어디에 세우셨나요?", text)
                    handler.postDelayed({ voice.speak(text) }, PLACE_SPEAK_DELAY_MS)
                }
            }
            is ParkingEvent.Ended -> {
                handler.removeCallbacks(warnTask)
                val text = ParkingTalk.exitReminder(e.reminder)
                Log.d(TAG, "parking ended: $text")
                alert(NOTI_EXIT, "출발 전에 확인하세요", text)
                voice.speak(text)
                // 음성이 끝날 시간을 두고 멈춘다. 리마인드는 저장돼 있어 화면에서 다시 볼 수 있다
                handler.postDelayed({ if (!needed(tracker)) stopSelf() }, STOP_AFTER_EXIT_MS)
            }
            null -> {}
        }
    }

    /** 무료 종료 경고는 타이머 한 번으로 건다 (주기적으로 깨지 않는다). */
    private fun scheduleWarn() {
        handler.removeCallbacks(warnTask)
        val s = tracker.state.session ?: return
        val at = s.freeWarnAt() ?: return
        val now = System.currentTimeMillis()
        if ((s.freeLeftMin(now) ?: 0) <= ParkingSession.WARN_BEFORE_MIN) return // 이미 지났다
        handler.postDelayed(warnTask, (at - now).coerceAtLeast(0))
        Log.d(TAG, "free warn in ${(at - now) / 1000}s")
    }

    private fun onFreeWarn() {
        val s = tracker.state.session ?: return
        val text = ParkingTalk.freeWarning(s, System.currentTimeMillis())
        alert(NOTI_FREE, "무료 주차 시간이 곧 끝나요", text)
        voice.speak(text)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        Parking.unobserve(onState)
        gears.stop()
        voice.close()
        Log.d(TAG, "service stop")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun alert(id: Int, title: String, text: String) {
        val n = Notification.Builder(this, CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(openApp(id))
            .setAutoCancel(true)
            .build()
        runCatching { getSystemService(NotificationManager::class.java).notify(id, n) }
            .onFailure { Log.w(TAG, "알림 실패 ${it.message}") }
    }

    private fun openApp(id: Int) = PendingIntent.getActivity(
        this, id,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "주차 확인", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ALERT, "주차 알림", NotificationManager.IMPORTANCE_HIGH))
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("ParkDeal")
            .setContentText("P단에 넣으면 주차 시간을 챙겨 드려요")
            .setContentIntent(openApp(0))
            .build()
        startForeground(NOTI_ONGOING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    companion object {
        private const val TAG = "ParkDeal"
        private const val CHANNEL = "parking_ongoing"
        private const val CHANNEL_ALERT = "parking_alert"
        private const val NOTI_ONGOING = 1
        private const val NOTI_EXIT = 10
        private const val NOTI_FREE = 11
        private const val NOTI_PLACE = 12
        /** 주차 직후 안내가 끝난 뒤에 말한다 */
        private const val PLACE_SPEAK_DELAY_MS = 7_000L
        private const val STOP_AFTER_EXIT_MS = 15_000L

        private fun needed(t: ParkingTracker) = t.candidate != null || t.state.session != null

        /** 후보를 정했거나 주차 중이면 서비스를 띄운다. 할 일이 없으면 띄우지 않는다. */
        fun ensure(context: Context) {
            if (!needed(Parking.tracker(context))) return
            context.startForegroundService(Intent(context, ParkingService::class.java))
        }
    }
}
