package com.parkdeal.core.api

/** 서버에 GET 하고 본문을 돌려준다. 실패하면 예외를 던진다. 구현은 app(HttpURLConnection). */
fun interface HttpTransport {
    fun get(path: String): String
}

/** 요청 키로 저장된 응답 본문을 읽는다. */
fun interface ResponseSource {
    fun read(key: String): String?
}

/** 마지막으로 받은 서버 응답을 남겨 두는 곳. */
interface ResponseCache : ResponseSource {
    fun write(key: String, body: String)
}

/** 값과, 그 값을 어디서 가져왔는지. 화면에 "실시간/저장된 정보"를 구분해 보여준다. */
data class Fetched<T>(val value: T, val origin: Origin)

enum class Origin {
    /** 방금 서버에서 받았다 */
    SERVER,

    /** 서버가 응답하지 않아, 전에 받아 둔 것을 쓴다 */
    CACHE,

    /** 받아 둔 것도 없어, 앱에 넣어 둔 데모 스냅샷을 쓴다 */
    SNAPSHOT,
}

/**
 * 주차 데이터는 **서버 → 캐시 → 내장 스냅샷** 순서로 가져온다. 시연장 인터넷이 끊기거나
 * 홈랩 서버가 내려가도 앱이 멈추지 않게 하는 것이 목적이다(docs/pleos-guidelines.md 4절).
 *
 * 블로킹 호출이다. 메인 스레드에서 부르지 않는다.
 */
class ParkingRepository(
    private val transport: HttpTransport,
    private val cache: ResponseCache,
    private val snapshot: ResponseSource,
) {
    fun destination(request: ApiRequest.Destination): Fetched<DestinationResponse>? = fetch(request, ApiJson::destination)

    fun lots(request: ApiRequest.Lots): Fetched<LotsResponse>? = fetch(request, ApiJson::lots)

    fun stops(request: ApiRequest.Stops): Fetched<StopsResponse>? = fetch(request, ApiJson::stops)

    fun walk(request: ApiRequest.Walk): Fetched<WalkResponse>? = fetch(request, ApiJson::walk)

    private fun <T> fetch(request: ApiRequest, parse: (String) -> T): Fetched<T>? {
        val key = request.cacheFile

        runCatching { transport.get(request.path) }.getOrNull()?.let { body ->
            // 형식이 맞는 응답만 캐시에 남긴다 — 프록시 오류 페이지로 좋은 캐시를 덮어쓰지 않게
            parseOrNull(body, parse)?.let { value ->
                runCatching { cache.write(key, body) }
                return Fetched(value, Origin.SERVER)
            }
        }
        readParsed(cache, key, parse)?.let { return Fetched(it, Origin.CACHE) }
        readParsed(snapshot, key, parse)?.let { return Fetched(it, Origin.SNAPSHOT) }
        return null
    }

    private fun <T> readParsed(source: ResponseSource, key: String, parse: (String) -> T): T? =
        runCatching { source.read(key) }.getOrNull()?.let { parseOrNull(it, parse) }

    private fun <T> parseOrNull(body: String, parse: (String) -> T): T? =
        try {
            parse(body)
        } catch (_: ApiFormatException) {
            null
        }
}
