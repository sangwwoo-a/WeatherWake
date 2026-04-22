pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "WeatherWake"
include(":core")
include(":data-owm")
include(":data-kma")
include(":data-nws")
include(":app-kr")
// :app-us 는 RegionalWeatherProvider 도입으로 단일 앱(app-kr) 이 좌표 기반으로
// KR/US/OTHER 를 자동 라우팅하면서 불필요해짐. v1.8 에서 제거.
