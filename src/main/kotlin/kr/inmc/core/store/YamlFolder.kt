package kr.inmc.core.store

import kr.inmc.core.config.ConfigService
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

/**
 * 항목 하나당 파일 하나인 폴더의 **저장 경로만** 담당한다.
 *
 * 여섯 개 레지스트리(몬스터·스포너·등장조건·수식어·상자·게임)가 dirty id 집합을 비우고,
 * 각각을 직렬화해, 워커로 넘겨 `<id>.yml` 로 쓰는 **같은 알고리즘**을 각자 갖고 있었다.
 * 로그 문구와 `save` 호출만 달랐다.
 *
 * **상속이 아니라 보유해서 쓴다.** 레지스트리들의 바깥 인터페이스는 서로 다르다 —
 * `load` 와 `loadAll` 이 섞여 있고, `WorldSettingsRegistry` 는 인자를 하나 더 받으며,
 * 어떤 것은 `create`/`delete` 에서 인덱스를 다시 만든다. 베이스 클래스로 묶으려면 그 차이를
 * 전부 훅으로 뚫어야 하고, 그러면 아무에게도 맞지 않는 베이스가 된다. 진짜 중복인 저장
 * 경로만 여기 두고 나머지는 각자 갖는다.
 *
 * **스레드 계약** — [readAll] 은 워커에서, [flushDirty] 의 `render` 는 메인에서 돈다.
 * YAML 을 만드는 사이에 값이 바뀌면 저장된 파일이 어느 시점의 것인지 알 수 없게 된다.
 */
class YamlFolder(
    private val io: ConfigService,
    private val logger: Logger,
    private val folderName: String,
    /** 파일 맨 위에 붙는 설명. `#` 는 없어도 된다 — [commentedHeader] 참조. */
    header: String = "",
    /** 로그에 쓸 이름. 예: "몬스터" */
    private val what: String,
) {

    private val header = commentedHeader(header)

    val folder: File get() = io.file(folderName)

    private val dirty = ConcurrentHashMap.newKeySet<String>()

    fun markDirty(id: String) {
        dirty.add(id)
    }

    fun forget(id: String) {
        dirty.remove(id)
    }

    fun clearDirty() {
        dirty.clear()
    }

    fun isDirty(): Boolean = dirty.isNotEmpty()

    /**
     * 폴더의 `.yml` 을 전부 읽어 [parse] 에 넘긴다. **워커 스레드에서 부를 것.**
     *
     * 한 파일이 깨져도 나머지는 살린다 — 손으로 고친 파일 하나 때문에 플러그인이 안 뜨는
     * 일은 없어야 한다. [parse] 가 null 을 주거나 던지면 그 파일만 건너뛴다.
     */
    fun <T : Any> readAll(
        skip: (String) -> Boolean = { false },
        parse: (id: String, config: YamlConfiguration) -> T?,
    ): List<Pair<String, T>> {
        val dir = folder
        dir.mkdirs()
        val files = dir.listFiles { f: File -> f.isFile && f.name.endsWith(".yml") } ?: emptyArray()
        return files.mapNotNull { file ->
            val id = file.nameWithoutExtension
            if (skip(id)) return@mapNotNull null
            try {
                parse(id, io.load(file))?.let { id to it }
            } catch (t: Throwable) {
                logger.severe(what + " 파일을 읽지 못했습니다 (" + file.name + "): " + t.message)
                null
            }
        }
    }

    /**
     * dirty 인 것만 저장한다. [render] 는 **메인 스레드**에서 돌며, null 을 주면 건너뛴다
     * (직렬화 실패를 호출자가 자기 문구로 로그에 남긴 뒤 null 을 주면 된다).
     */
    fun flushDirty(render: (id: String) -> YamlConfiguration?) {
        val snapshots = drain(render)
        if (snapshots.isEmpty()) return
        io.asyncRun { writeAll(snapshots) }
    }

    /** onDisable 경로. 호출한 스레드에서 직접 쓴다. */
    fun flushDirtyBlocking(render: (id: String) -> YamlConfiguration?) {
        val snapshots = drain(render)
        if (snapshots.isEmpty()) return
        writeAll(snapshots)
    }

    fun deleteFile(id: String) {
        dirty.remove(id)
        io.asyncRun { File(folder, "$id.yml").delete() }
    }

    private fun drain(render: (id: String) -> YamlConfiguration?): List<Pair<String, String>> {
        if (dirty.isEmpty()) return emptyList()
        val pending = dirty.toList()
        dirty.removeAll(pending.toSet())
        return pending.mapNotNull { id -> render(id)?.let { id to it.saveToString() } }
    }

    private fun writeAll(snapshots: List<Pair<String, String>>) {
        folder.mkdirs()
        for ((id, text) in snapshots) {
            try {
                kr.inmc.core.util.AtomicFiles.write(File(folder, "$id.yml"), header + text)
            } catch (t: Throwable) {
                logger.severe(what + " 저장 실패 (" + id + "): " + t.message)
            }
        }
    }
}
