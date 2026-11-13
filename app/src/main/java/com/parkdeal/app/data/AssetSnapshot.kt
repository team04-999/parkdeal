package com.parkdeal.app.data

import android.content.res.AssetManager
import com.parkdeal.core.api.ResponseSource
import java.io.FileNotFoundException

/** 앱에 넣어 둔 데모 스냅샷 (assets/api-snapshot, scripts/snapshot_api.py 로 만든다). */
class AssetSnapshot(private val assets: AssetManager, private val dir: String = "api-snapshot") : ResponseSource {
    override fun read(key: String): String? =
        try {
            assets.open("$dir/$key").bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: FileNotFoundException) {
            null
        }
}
