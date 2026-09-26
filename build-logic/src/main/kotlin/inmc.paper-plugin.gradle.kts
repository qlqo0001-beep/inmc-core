/**
 * inmc-core 위에 올라가는 일반 플러그인.
 *
 * 4세대에는 이 파일의 내용이 build.gradle.kts 세 벌에 그대로 복사돼 있었다.
 */

plugins {
    id("inmc.paper-base")
}

// 관례 플러그인 안에서는 타입세이프 `libs.` 접근자가 생성되지 않는다.
// (inmc.paper-base 가 같은 줄을 갖고 있지만 스크립트가 다르므로 공유되지 않는다)
val libs = extensions.getByType<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

dependencies {
    // core 의 프레임워크·도메인 서비스를 컴파일 타임에만 본다.
    // 런타임에는 paper-plugin.yml 의 join-classpath 로 core 의 클래스로더가 붙는다.
    //
    // 좌표로 적지만 실제로는 `includeBuild("../inmc-core")` 가 소스 프로젝트로 치환한다.
    // core 를 고치면 다음 빌드에 바로 반영되고, 따로 publish 할 필요가 없다.
    add("compileOnly", libs.findLibrary("inmc-core").get())
    add("testImplementation", libs.findLibrary("inmc-core").get())

    // stdlib 은 core 가 제공한다. 여기서 번들하면 안 된다 — inmc.paper-core 의 주석 참조.
    add("compileOnly", kotlin("stdlib"))
}

tasks.shadowJar {
    // 남길 것이 사실상 자기 코드뿐이라 relocate 할 대상도 없다.
    // 4세대의 kotlin/annotations/intellij relocate 3줄이 여기서 사라졌다.
    dependencies {
        exclude(dependency("org.jetbrains.kotlin:.*"))
        exclude(dependency("org.jetbrains:annotations"))
    }
}
