package kr.inmc.core.config

import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Every disk touch in the plugin goes through here.
 *
 * A single worker thread serialises all reads and writes, which keeps the main thread free
 * (the spec forbids main-thread I/O) and removes any chance of two saves racing on the same
 * file. Callers hand in a closure and get the result back on the main thread.
 */
class ConfigService(private val plugin: Plugin) {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        // 플러그인마다 워커가 하나씩 따로 돈다. 이름에 플러그인을 박아두지 않으면
        // 스레드 덤프에서 어느 플러그인이 디스크를 붙잡고 있는지 구분할 수 없다.
        Thread(runnable, "${plugin.name}-io").apply { isDaemon = true }
    }

    val dataFolder: File get() = plugin.dataFolder

    /** 워커 안에서 경고를 남겨야 하는 호출자용. 어느 플러그인의 워커인지가 로그에 남는다. */
    val logger: java.util.logging.Logger get() = plugin.logger

    fun file(vararg path: String): File = File(plugin.dataFolder, path.joinToString(File.separator))

    /** Runs [work] off-thread and drops the result on the main thread. */
    fun <T> async(work: () -> T, then: (T) -> Unit) {
        executor.execute {
            val result = try {
                work()
            } catch (t: Throwable) {
                plugin.logger.log(java.util.logging.Level.SEVERE, "비동기 작업 실패", t)
                return@execute
            }
            if (!plugin.isEnabled) return@execute
            // 리전 스케줄러를 쓴다. Folia 에서 Bukkit.getScheduler() 는 아예 던진다.
            // 일반 Paper 에서도 같은 API 가 있고 동작이 같다 — 다음 틱에 메인에서 돈다.
            Bukkit.getGlobalRegionScheduler().run(plugin) { then(result) }
        }
    }

    /** Fire-and-forget off-thread work. */
    fun asyncRun(work: () -> Unit) {
        executor.execute {
            try {
                work()
            } catch (t: Throwable) {
                plugin.logger.log(java.util.logging.Level.SEVERE, "비동기 작업 실패", t)
            }
        }
    }

    // --- blocking helpers, only ever called from the worker thread --------------

    fun load(file: File): YamlConfiguration = YamlConfiguration.loadConfiguration(file)

    fun save(file: File, config: YamlConfiguration) {
        try {
            file.parentFile?.mkdirs()
            config.save(file)
        } catch (e: IOException) {
            plugin.logger.severe("파일 저장 실패 (${file.name}): ${e.message}")
        }
    }

    /** Copies a bundled resource when the target does not exist yet. Never overwrites. */
    fun copyDefault(resource: String, target: File) {
        if (target.exists()) return
        val stream = plugin.getResource(resource)
        if (stream == null) {
            plugin.logger.warning("내장 리소스를 찾을 수 없습니다: $resource")
            return
        }
        try {
            target.parentFile?.mkdirs()
            stream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: IOException) {
            plugin.logger.severe("리소스 복사 실패 ($resource): ${e.message}")
        }
    }

    /** Reads a bundled resource as UTF-8 text; used for the shipped message defaults. */
    fun readResource(resource: String): YamlConfiguration? {
        val stream = plugin.getResource(resource) ?: return null
        return stream.use { input ->
            YamlConfiguration.loadConfiguration(input.reader(StandardCharsets.UTF_8))
        }
    }

    /**
     * Drains queued writes and stops the worker. Called from onDisable, where blocking
     * briefly is both safe and necessary - the server is going down and the state files
     * have to land on disk.
     */
    fun shutdown() {
        executor.shutdown()
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.logger.warning("I/O 작업이 10초 안에 끝나지 않아 강제 종료합니다")
                executor.shutdownNow()
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            executor.shutdownNow()
        }
    }
}
