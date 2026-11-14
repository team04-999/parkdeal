package com.parkdeal.app.parking

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Locale

/**
 * Pleos TextToSpeech(온디바이스)로 안내 문장을 읽는다.
 * 읽기 전에 오디오 포커스를 잠깐 받아(내비 음성은 줄어든다) 끝나면 돌려준다 (docs/pleos-guidelines.md 1장).
 */
class Voice(context: Context) {
    private val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        .build()
    @Volatile private var ready = false
    private var pending: String? = null

    private val events = object : EventListener {
        override fun onStart() { log("tts start") }
        override fun onReady() {}
        override fun onDone() = release("done")
        override fun onError(type: ErrorType) = release("error $type")
        override fun onStop() = release("stop")
        override fun onUpdatedRms(rms: Double) {}
    }

    init {
        // addEventListener 는 initialize 성공 뒤에만 부른다 (전에 부르면 SDK 안에서 죽는다)
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                tts.addEventListener(events)
                tts.setLocale(Locale.KOREA) // "ko-KR". Locale.KOREAN("ko") 은 엔진이 받지 않는다
                ready = true
                main.post { pending?.let { pending = null; speak(it) } }
            }
            override fun onFailure(type: ErrorType) { log("tts init failed $type") }
        })
    }

    /** 초기화 전이면 마지막 문장 하나만 기억했다가 준비되면 읽는다. */
    fun speak(text: String) {
        if (!ready) { pending = text; return }
        audio.requestAudioFocus(focus)
        // 포커스를 받은 직후 바로 읽으면 첫 음절이 잘린다. 다른 소리가 줄어들 틈을 준다
        main.postDelayed({ tts.speak(text, true) }, PRE_ROLL_MS)
    }

    fun close() {
        main.removeCallbacksAndMessages(null)
        audio.abandonAudioFocusRequest(focus)
        runCatching { tts.release() }
    }

    private fun release(why: String) {
        log("tts $why")
        audio.abandonAudioFocusRequest(focus)
    }

    private fun log(msg: String) { Log.d(TAG, msg) }

    private companion object {
        const val TAG = "ParkDeal"
        const val PRE_ROLL_MS = 180L
    }
}
