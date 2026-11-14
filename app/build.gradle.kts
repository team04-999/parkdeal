plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.parkdeal.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.parkdeal.app"
        minSdk = 28 // Pleos Playground 배포 요구사항
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // 주차 데이터 API (parkdeal-server). 실패하면 캐시 → 내장 스냅샷으로 동작한다
        buildConfigField("String", "PARKDEAL_API_BASE", "\"https://parkdeal-api.daeuk.cloud\"")
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.navihelper)
    implementation(libs.pleos.tts)
    testImplementation(libs.junit)
}
