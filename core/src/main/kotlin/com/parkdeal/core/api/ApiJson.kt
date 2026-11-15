package com.parkdeal.core.api

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** 응답이 약속한 형식이 아니다 (프록시 오류 페이지, 잘린 응답, 깨진 캐시 파일 등). */
class ApiFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

object ApiJson {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun destination(body: String): DestinationResponse = parse(body) { json.decodeFromString(it) }

    fun lots(body: String): LotsResponse = parse(body) { json.decodeFromString(it) }

    fun walk(body: String): WalkResponse = parse(body) { json.decodeFromString(it) }

    private inline fun <T> parse(body: String, decode: (String) -> T): T =
        try {
            decode(body)
        } catch (e: SerializationException) {
            throw ApiFormatException("응답 형식이 아니다: ${body.take(60)}", e)
        } catch (e: IllegalArgumentException) {
            throw ApiFormatException("응답 형식이 아니다: ${body.take(60)}", e)
        }
}
