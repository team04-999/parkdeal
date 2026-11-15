import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// release 서명 키는 저장소 밖에 둔다. 경로는 PARKDEAL_SIGNING 또는 ~/.parkdeal-keys/signing.properties
val signingFile = file(System.getenv("PARKDEAL_SIGNING") ?: "${System.getProperty("user.home")}/.parkdeal-keys/signing.properties")
val signing = Properties().apply { if (signingFile.exists()) signingFile.inputStream().use { load(it) } }

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
    // 주행 제한 상태(CarUxRestrictionsManager)
    useLibrary("android.car")

    signingConfigs {
        if (signing.isNotEmpty()) create("release") {
            storeFile = file(signing.getProperty("storeFile"))
            storePassword = signing.getProperty("storePassword")
            keyAlias = signing.getProperty("keyAlias")
            keyPassword = signing.getProperty("keyPassword")
        }
    }

    buildTypes {
        debug {
            // 로컬 서버로 시험할 때: ./gradlew :app:assembleDebug -PparkdealApi=http://10.0.2.2:4000
            (project.findProperty("parkdealApi") as String?)?.let { buildConfigField("String", "PARKDEAL_API_BASE", "\"$it\"") }
        }
        release {
            // 키가 없는 곳(CI)에서는 서명 없이 빌드만 확인한다
            signingConfig = signingConfigs.findByName("release")
        }
    }

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
    implementation(libs.pleos.stt)
    testImplementation(libs.junit)
}
