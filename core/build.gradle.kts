// 요금 계산·추천·주차 세션 같은 순수 로직. Android 의존이 없어 JVM 에서 바로 테스트한다.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
