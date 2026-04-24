import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
}

// local.properties에서 API 키 로드
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "com.devkorea1m.weatherwake"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.devkorea1m.weatherwake"
        minSdk = 26
        targetSdk = 35
        versionCode = 24
        versionName = "1.2.4"

        buildConfigField(
            "String", "OWM_API_KEY",
            "\"${localProps.getProperty("owm.api.key", "")}\""
        )

        // 기상청(공공데이터포털) 초단기실황 serviceKey. KmaWeatherProvider 가 사용.
        // 빈 값이면 CrossValidatingWeatherProvider 가 KMA 호출 실패를 OWM 로 폴백.
        buildConfigField(
            "String", "KMA_SERVICE_KEY",
            "\"${localProps.getProperty("kma.service.key", "")}\""
        )

        // 디버그 전용 날씨 오버라이드. release 빌드엔 영향 없음 (WeatherCheckWorker가
        // BuildConfig.DEBUG 가드로 이중 체크). 값: ""(실제 날씨), "RAIN", "SNOW", "CLEAR"
        buildConfigField(
            "String", "WEATHER_OVERRIDE",
            "\"${localProps.getProperty("weather.override", "")}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":data-owm"))
    implementation(project(":data-kma"))
    implementation(project(":data-nws"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation("androidx.core:core:1.13.0")
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)
    implementation(libs.work.runtime)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)
    implementation(libs.play.services.location)
    implementation(libs.viewpager2)
    implementation(libs.activity.ktx)
    implementation(libs.fragment.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play.services)

    // 단위 테스트
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
