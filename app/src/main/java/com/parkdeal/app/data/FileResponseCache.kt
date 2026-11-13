package com.parkdeal.app.data

import com.parkdeal.core.api.ResponseCache
import java.io.File

/**
 * 마지막 서버 응답을 파일로 남긴다. cacheDir 가 아니라 filesDir 에 둔다 — cacheDir 는 저장 공간이
 * 부족하면 시스템이 지우는데, 이 캐시는 서버가 안 될 때 쓰는 대비책이라 남아 있어야 한다.
 */
class FileResponseCache(private val dir: File) : ResponseCache {
    override fun read(key: String): String? = File(dir, key).takeIf { it.isFile }?.readText()

    override fun write(key: String, body: String) {
        dir.mkdirs()
        // 임시 파일에 다 쓴 뒤 이름을 바꾼다. 쓰다가 프로세스가 죽어도 반쯤 쓴 파일이 남지 않는다
        val tmp = File(dir, "$key.tmp")
        tmp.writeText(body)
        if (!tmp.renameTo(File(dir, key))) {
            tmp.delete()
            error("캐시 파일을 바꾸지 못했다: $key")
        }
    }
}
