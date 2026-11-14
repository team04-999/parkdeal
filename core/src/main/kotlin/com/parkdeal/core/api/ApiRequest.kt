package com.parkdeal.core.api

import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale

/**
 * 서버 요청 하나. 좌표는 소수 넷째 자리(약 11m)로 맞춘다 — 같은 목적지를 다시 열었을 때
 * 캐시가 맞고, 서버의 가게 매칭 반경(80m)보다 충분히 작다.
 */
sealed class ApiRequest {
    abstract val path: String

    /** 캐시·내장 스냅샷에서 이 요청을 찾는 키. */
    abstract val cacheKey: String

    /** 캐시 키를 파일 이름으로 쓸 수 있게 줄인 것. 가게 이름에 '/' 같은 문자가 있어도 안전하다. */
    val cacheFile: String get() = sha256(cacheKey).take(16) + ".json"

    data class Destination(val lat: Double, val lng: Double, val name: String?) : ApiRequest() {
        private val cleanName = name?.trim()?.takeIf { it.isNotEmpty() }

        override val path: String
            get() = "/v1/destination?lat=${coord(lat)}&lng=${coord(lng)}" +
                // encode(String, Charset) 는 Android API 33 부터라 minSdk 28 에서 쓸 수 없다
                (cleanName?.let { "&name=" + URLEncoder.encode(it, "UTF-8") } ?: "")

        // 서버는 이름의 공백을 무시하고 매칭하므로 캐시 키도 공백을 뺀다
        override val cacheKey: String
            get() = "destination|${coord(lat)}|${coord(lng)}|${cleanName?.replace(Regex("\\s+"), "") ?: ""}"
    }

    data class Lots(val lat: Double, val lng: Double, val radius: Int = 800) : ApiRequest() {
        override val path: String get() = "/v1/lots?lat=${coord(lat)}&lng=${coord(lng)}&radius=$radius"
        override val cacheKey: String get() = "lots|${coord(lat)}|${coord(lng)}|$radius"
    }

    protected fun coord(v: Double): String = String.format(Locale.ROOT, "%.4f", v)

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
