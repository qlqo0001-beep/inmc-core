plugins {
    id("inmc.paper-core")
}

group = "kr.inmc.core"

// 버전은 카탈로그가 정한다. 플러그인 쪽도 같은 값을 보므로 두 곳이 갈라질 수 없다.
version = libs.versions.inmc.core.get()

inmc {
    // core 는 가장 낮은 하한을 잡는다. 여기서 올리면 이 위에 올라가는 모든 플러그인의
    // 하한이 같이 올라간다.
    paper = "26.1.2"
    pluginName = "inmc-core"
}

dependencies {
    // 훅이 컴파일 타임에 시그니처를 보는 것만 여기 둔다.
    // MMOItems / MythicLib / ItemsAdder / Nexo / Oraxen / EcoItems 는 100% 리플렉션이다.
    compileOnly(libs.placeholderapi) { isTransitive = false }
    compileOnly(libs.vault.api) { isTransitive = false }

    // SqlWorker 테스트용. 서버에서는 Paper 가 드라이버를 갖고 있다.
    testImplementation(libs.sqlite.jdbc)
}
