/**
 * inmc-core 전용. 서버 전체에서 kotlin-stdlib 을 **한 벌만** 들고 있는 쪽이다.
 */

plugins {
    id("inmc.paper-base")
}

dependencies {
    // relocate 하지 않는다. 의존 플러그인들이 core 의 클래스로더에서 이 stdlib 을 그대로
    // 집어가야 하기 때문이다. 접두사를 붙이면 core 가 넘겨주는 람다의 타입
    // (kotlin.jvm.functions.Function1) 이 상대편 것과 달라져 링크가 깨진다.
    //
    // 다른 서드파티 Kotlin 플러그인과 충돌하지 않는 이유: Paper 는 플러그인마다
    // 클래스로더를 따로 두고, join-classpath 를 선언한 플러그인만 여기를 들여다본다.
    add("implementation", kotlin("stdlib"))
}

tasks.shadowJar {
    // relocate 호출이 하나도 없는 것이 의도다. 위 주석 참조.
    // bStats 를 나중에 넣게 되면 그것만은 반드시 relocate 해야 한다 (가이드 함정 4번).
    exclude("META-INF/versions/9/module-info.class")
}
