dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    // 관례 플러그인도 본 빌드와 같은 카탈로그를 본다 — 버전이 두 군데로 갈라지지 않게.
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}

rootProject.name = "build-logic"
