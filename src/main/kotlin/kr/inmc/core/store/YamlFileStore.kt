package kr.inmc.core.store

import kr.inmc.core.config.ConfigService
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

/**
 * 파일 하나에 통째로 저장하는 상태의 공통 골격.
 *
 * 세 플러그인에 `load / flush / flushBlocking / write / serialize` 다섯 짝이 여덟 벌 있었다.
 * 내용은 전부 다르지만 **순서와 스레드 규칙은 같았다.** 그 규칙만 여기 둔다.
 *
 * **스레드 계약 — 이게 이 클래스의 존재 이유다.**
 * - 워커가 하는 일은 파일을 **읽고 쓰는 것뿐**이다.
 * - [read] 와 [write] 는 **메인 스레드**에서 돈다.
 *
 * [write] 를 워커로 옮기고 싶어지겠지만 그러면 안 된다. YAML 을 만드는 사이에 값이 바뀌면
 * 저장된 파일이 어느 시점의 상태인지 알 수 없게 되고, 그건 테스트로 잡히지 않는다.
 * (가이드 함정 16번과 같은 이유다.)
 */
abstract class YamlFileStore(
    protected val io: ConfigService,
    private val path: List<String>,
    /** 파일 맨 위에 붙는 설명. `#` 는 없어도 된다 — [commentedHeader] 참조. */
    header: String = "",
    /** 저장 실패 로그에 쓸 이름. 예: "플레이 기록" */
    private val what: String,
) {

    private val header = commentedHeader(header)

    @Volatile
    private var dirty = false

    /** 값이 바뀌었다고 표시한다. 표시하지 않으면 [flush] 가 아무것도 쓰지 않는다. */
    fun markDirty() {
        dirty = true
    }

    protected fun isDirty(): Boolean = dirty

    /** 읽은 내용을 메모리에 반영한다. **메인 스레드.** */
    protected abstract fun read(config: YamlConfiguration)

    /** 메모리 상태를 YAML 로 옮긴다. **메인 스레드.** 위 계약 참조. */
    protected abstract fun write(config: YamlConfiguration)

    fun load(then: () -> Unit = {}) {
        io.async({
            val target = file()
            if (target.exists()) io.load(target) else YamlConfiguration()
        }) { config ->
            read(config)
            dirty = false
            then()
        }
    }

    fun flush() {
        if (!dirty) return
        dirty = false
        val text = render()
        // 여기서 직접 잡는다. asyncRun 도 예외를 삼키긴 하지만 "비동기 작업 실패" 라고만 남겨서,
        // 로그만 보고 어느 파일이 안 써졌는지 알 수 없다.
        io.asyncRun {
            runCatching { writeText(text) }
                .onFailure { io.logger.severe(what + " 저장 실패: " + it.message) }
        }
    }

    /** onDisable 경로. 워커가 곧 닫히므로 호출한 스레드에서 직접 쓰고, dirty 여부를 보지 않는다. */
    fun flushBlocking() {
        runCatching { writeText(render()) }
            .onFailure { io.logger.severe(what + " 저장 실패: " + it.message) }
        dirty = false
    }

    private fun file(): File = io.file(*path.toTypedArray())

    private fun render(): String = header + YamlConfiguration().also { write(it) }.saveToString()

    private fun writeText(text: String) {
        kr.inmc.core.util.AtomicFiles.write(file(), text)
    }
}

/**
 * 머리말을 YAML 주석으로 만든다. `#` 로 시작하지 않는 줄에 `# ` 를 붙이고, 끝을 줄바꿈으로 맞춘다.
 *
 * 머리말은 본문 앞에 **글자 그대로** 붙는다. 세 플러그인이 `#` 없이, 끝 줄바꿈도 없이 넘겼고
 * 그 결과가 `…습니다.items:` 한 줄이었다 — 첫 키가 설명문에 붙어 뿌리 키가 사라지고, 다음
 * 부팅에서 **정의·무덤이 통째로 없는 것으로 읽혔다.** 오류가 나는 경우(물고기)도 있었지만
 * 대부분은 조용했다. 이미 주석인 머리말은 한 글자도 바뀌지 않는다.
 */
internal fun commentedHeader(header: String): String {
    if (header.isEmpty()) return ""
    val body = header.split('\n').joinToString("\n") { line ->
        if (line.isBlank() || line.trimStart().startsWith("#")) line else "# $line"
    }
    return if (body.endsWith("\n")) body else body + "\n"
}
