package com.parkdeal.app.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Locale

/**
 * Pleos SpeechToText(온디바이스)로 한 번 듣는다. 차량 마이크 음성은 외부 음성 인식 서비스로 보내지 않는다
 * (공모전 규칙). 시작은 화면 버튼으로 하고, 말이 끝나면(EPD) 자동으로 멈춘다.
 */
class Listener(context: Context, private val onState: (State) -> Unit) {
    sealed interface State {
        data object Listening : State
        data class Partial(val text: String) : State
        data class Final(val text: String) : State
        data class Failed(val reason: String) : State
    }

    private val stt = SpeechToText(context.applicationContext, Mode.ON_DEVICE)
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var ready = false
    private var lastText = ""
    private var finished = false
    /** 조용하면 말 끝 감지(EPD)가 오지 않아 계속 듣는다. 일정 시간이 지나면 멈춘다. */
    private val timeout = Runnable {
        if (finished) return@Runnable
        runCatching { stt.stop() }
        if (lastText.isBlank()) { finished = true; onState(State.Failed("timeout")) } else finish(lastText)
    }

    private val results = object : ResultListener {
        override fun onUpdated(text: String, isFinal: Boolean) {
            lastText = text
            main.post { if (isFinal) finish(text) else onState(State.Partial(text)) }
        }
        override fun onUpdatedEpdData(start: Long, end: Long) {}
        override fun onStartedRecognition() { main.post { onState(State.Listening) } }
        override fun onEndedRecognition() { main.post { finish(lastText) } }
        override fun onError(type: ErrorType) { main.post { if (!finished) { finished = true; onState(State.Failed(type.toString())) } } }
        override fun onReady() {}
        override fun onUpdatedRms(rms: Double) {}
    }

    init {
        stt.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                stt.addListener(results)
                stt.setLocale(Locale.KOREA)
                ready = true
            }
            override fun onFailure(type: ErrorType) { Log.w(TAG, "STT 초기화 실패 $type") }
        })
    }

    /** 한 번 듣기 시작. 준비가 안 됐으면 false. */
    fun listen(): Boolean {
        if (!ready) return false
        lastText = ""; finished = false
        onState(State.Listening)
        stt.request()
        main.removeCallbacks(timeout)
        main.postDelayed(timeout, LISTEN_MS)
        return true
    }

    fun cancel() { runCatching { stt.stop() } }

    private fun finish(text: String) {
        if (finished) return
        finished = true
        main.removeCallbacks(timeout)
        Log.d(TAG, "들은 말: $text")
        onState(State.Final(text))
    }

    fun close() { main.removeCallbacks(timeout); runCatching { stt.removeListener(results) }; runCatching { stt.release() } }

    private companion object {
        const val TAG = "ParkDeal"
        const val LISTEN_MS = 8_000L
    }
}
