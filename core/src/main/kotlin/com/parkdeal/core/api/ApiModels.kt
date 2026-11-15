package com.parkdeal.core.api

import kotlinx.serialization.Serializable

// parkdeal-server 의 응답 형식 (서버 레포 docs/spec.md). 서버가 필드를 늘려도 깨지지 않게
// 모르는 필드는 무시한다(ApiJson). 가게·혜택은 네이버 지도 수집본에서 가공한 구조화 값만 온다.

@Serializable
data class DestinationResponse(val store: Store? = null, val benefits: List<Benefit> = emptyList())

@Serializable
data class LotsResponse(val lots: List<Lot> = emptyList())

@Serializable
data class Store(
    val id: String,
    val name: String,
    val category: String? = null,
    val lat: Double,
    val lng: Double,
    /** 업종 기준 예상 체류 시간(분). 요금 계산의 기본 주차 시간이다. */
    val stayMin: Int,
    val source: Source,
)

/** 데이터 출처. 화면에 출처 표시로 쓴다. */
@Serializable
data class Source(val name: String, val url: String)

@Serializable
data class Benefit(
    val id: Int,
    /** conditional, all_free_min, free, paid_support, available, guide, paid */
    val type: String,
    /** purchase, meal, treatment, first_visit, visit */
    val condition: String? = null,
    /** plate, receipt_stamp, tablet, ticket, app, unknown */
    val redeem: String = "unknown",
    /** 조건 없는 무료 시간(분). */
    val freeMin: Int? = null,
    /** 금액 조건이 있는 무료 시간. minSpend 오름차순. */
    val tiers: List<Tier> = emptyList(),
    val validDays: String? = null,
    /** "HH:MM" */
    val validFrom: String? = null,
    val validTo: String? = null,
    val maxCars: Int? = null,
    val stackable: Boolean? = null,
    /** 자동 분류 결과를 사람이 아직 확인하지 않았다. */
    val needsReview: Boolean = false,
    val lot: Lot? = null,
)

@Serializable
data class Tier(val minSpend: Int, val freeMin: Int? = null)

@Serializable
data class Lot(
    val id: Int,
    val name: String,
    /** building, public, private, street, unknown */
    val kind: String,
    val lat: Double? = null,
    val lng: Double? = null,
    val entrance: LatLng? = null,
    val totalSpaces: Int? = null,
    val heightMm: Int? = null,
    val valet: Boolean? = null,
    val valetFee: Int? = null,
    val alerts: List<String> = emptyList(),
    val fee: Fee? = null,
    /** 요청 좌표(목적지)에서의 거리(m). */
    val distanceM: Int? = null,
    /** 이 주차장을 무료로 쓰게 해 주는 가게 혜택(같은 건물 다른 가게 포함). 서버가 안 주면 빈 목록. */
    val deals: List<Deal> = emptyList(),
    /** 서울시 실시간 빈자리 (공영 일부). 없으면 null. */
    val live: Live? = null,
)

@Serializable
data class LatLng(val lat: Double, val lng: Double)

/** 대표 요금 규칙. flatFee 가 있으면 정액, 아니면 최초 firstMin 분 firstFee 원 + unitMin 분마다 unitFee 원. */
@Serializable
data class Fee(
    val firstMin: Int? = null,
    val firstFee: Int? = null,
    val unitMin: Int? = null,
    val unitFee: Int? = null,
    val dailyMax: Int? = null,
    val flatFee: Int? = null,
    val source: String,
    val observedAt: String,
)

/** 주차장에 딸린 다른 가게 혜택. 필드 뜻은 [Benefit] 과 같다. */
@Serializable
data class Deal(
    val storeId: String,
    val storeName: String,
    val category: String? = null,
    val allFree: Boolean = false,
    val type: String,
    val condition: String? = null,
    val redeem: String = "unknown",
    val freeMin: Int? = null,
    val tiers: List<Tier> = emptyList(),
    val validDays: String? = null,
    val validFrom: String? = null,
    val validTo: String? = null,
    val maxCars: Int? = null,
    val needsReview: Boolean = false,
) {
    /** 목적지 혜택과 같은 방식으로 계산하려고 [Benefit] 으로 바꾼다. */
    fun asBenefit() = Benefit(
        id = 0, type = type, condition = condition, redeem = redeem, freeMin = freeMin, tiers = tiers,
        validDays = validDays, validFrom = validFrom, validTo = validTo, maxCars = maxCars, needsReview = needsReview,
    )
}

/** 실시간 빈자리: 전체 [total] 면 중 [available] 면 남음. */
@Serializable
data class Live(val total: Int, val available: Int, val updatedAt: String = "")

/** 실제 걷는 거리(m)·시간(분). */
@Serializable
data class WalkResponse(val distanceM: Int, val walkMin: Int, val source: String = "")
