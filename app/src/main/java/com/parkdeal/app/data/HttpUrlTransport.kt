package com.parkdeal.app.data

import com.parkdeal.core.api.HttpTransport
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 주차 데이터 API 를 HttpURLConnection 으로 부른다. 의존성을 늘리지 않으려고 OkHttp 를 쓰지 않는다.
 *
 * 타임아웃을 짧게 둔다 — 운전자가 기다리는 화면이고, 실패해도 캐시·스냅샷이 있다.
 */
class HttpUrlTransport(
    private val baseUrl: String,
    private val userAgent: String,
    private val timeoutMs: Int = 2_500,
) : HttpTransport {
    override fun get(path: String): String {
        val conn = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("Accept", "application/json")
            // Cloudflare 는 알려진 봇 User-Agent 를 막는다. 앱 이름을 명시한다
            conn.setRequestProperty("User-Agent", userAgent)
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code: $path")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
