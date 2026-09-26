import inmc.build.InmcPluginExtension
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * core 와 일반 플러그인이 공통으로 쓰는 부분. 셰이딩 정책만 각자 다르다.
 */

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.gradleup.shadow")
    id("xyz.jpenilla.run-paper")
}

val inmc = extensions.create<InmcPluginExtension>("inmc").apply {
    paper.convention("26.2")
    pluginName.convention(project.name)
    runMemory.convention("2G")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

/** `26.1.2` → `26.1`. paper-plugin.yml 에 그대로 들어간다. */
val apiVersion = inmc.paper.map { it.split('.').take(2).joinToString(".") }

/** `26.1.2` → `io.papermc.paper:paper-api:26.1.2.build.+` */
val paperApi = inmc.paper.map { "io.papermc.paper:paper-api:$it.build.+" }

dependencies {
    addProvider("compileOnly", paperApi)
    addProvider("testImplementation", paperApi)

    add("testImplementation", libs.findLibrary("junit-jupiter").get())
    add("testImplementation", kotlin("test"))
    // stdlib 자동 추가는 gradle.properties 에서 껐다. 테스트는 JVM 에서 직접 도니 여기서 채운다.
    add("testImplementation", kotlin("stdlib"))
}

kotlin {
    jvmToolchain(25)
    compilerOptions { jvmTarget.set(JvmTarget.JVM_25) }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filteringCharset = "UTF-8"
    // api-version 을 여기서 주입하기 때문에 paper-plugin.yml 이 컴파일 대상과 어긋날 수 없다.
    val props = mapOf(
        "version" to project.version.toString(),
        "apiVersion" to apiVersion.get(),
    )
    inputs.properties(props)
    filesMatching("paper-plugin.yml") { expand(props) }
}

tasks.jar {
    archiveBaseName.set(inmc.pluginName)
    archiveClassifier.set("dev")   // 셰이딩 전 jar 은 -dev 로 구분
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveBaseName.set(inmc.pluginName)
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    exclude("META-INF/maven/**")
    exclude("META-INF/proguard/**")
    exclude("module-info.class")
    mergeServiceFiles()
}

// 같은 소스에서 항상 같은 해시가 나오게 한다. 서버에 올린 jar 이 빌드한 그 jar 인지
// 확인할 수 없으면 17번 함정("실행 중인 서버 위에 덮어쓰기")을 진단할 방법이 없다.
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

// ./gradlew :<모듈>:runServer — 실제 Paper 서버를 run/ 에 띄우고 방금 만든 jar 을 설치한다.
// EULA 는 일부러 자동 동의하지 않는다 (운영자가 직접 할 일).
tasks.runServer {
    minecraftVersion(inmc.paper.get())
    jvmArgs("-Xmx${inmc.runMemory.get()}")
}
