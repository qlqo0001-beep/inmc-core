// inmc-core 는 독립된 Gradle 빌드다.
//
// 플러그인들과 같은 폴더에 두면 "만든 플러그인 중 하나"로 보이지만, core 는 그 플러그인들이
// 올라타는 런타임이라 릴리스 주기가 다르다. 여기서 혼자 빌드·태그·배포하고,
// 플러그인 쪽(`제작플긴/`)은 카탈로그의 버전을 올려서 따라온다.
//
//   ./gradlew build          — core 만 빌드
//   ./gradlew :bl:...        — 관례 플러그인은 build-logic 에

pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // 이게 없으면 JDK 25 가 안 깔린 PC 에서 jvmToolchain(25) 이 자동으로 받아오지 못하고
    // "No matching toolchains found" 로 죽는다.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" }
        maven("https://repo.helpch.at/releases")                  { name = "helpchat" }   // PlaceholderAPI
        maven("https://jitpack.io")                               { name = "jitpack" }    // VaultAPI
        maven("https://maven.enginehub.org/repo/")                { name = "enginehub" }  // WorldGuard/Edit
    }
}

// 루트 프로젝트가 곧 core 모듈이다. 모듈이 하나뿐이라 한 겹 더 파지 않는다.
rootProject.name = "inmc-core"
