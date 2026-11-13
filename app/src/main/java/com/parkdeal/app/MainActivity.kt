package com.parkdeal.app

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import com.parkdeal.core.durationText

/** 뼈대 화면. 앱이 설치·실행되고 core 모듈이 연결됐는지만 보여준다. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "ParkDeal · 무료 주차 ${durationText(90)}"
            textSize = 48f
            gravity = Gravity.CENTER
        })
    }
}
