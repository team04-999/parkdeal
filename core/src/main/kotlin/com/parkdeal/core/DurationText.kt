package com.parkdeal.core

/** 분을 안내 문구로 바꾼다: 45 → "45분", 120 → "2시간", 90 → "1시간 30분". */
fun durationText(min: Int): String = when {
    min < 60 -> "${min}분"
    min % 60 == 0 -> "${min / 60}시간"
    else -> "${min / 60}시간 ${min % 60}분"
}
