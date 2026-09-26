package kr.inmc.core.store

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger

enum class SqlDialect { SQLITE, MYSQL }

/**
 * DB 하나. 연결은 [SqlWorker] 의 쓰기 스레드에서만 만진다 — 스레드가 하나라 풀이 필요 없고, 쓰기 순서가 저절로 지켜진다.
 * MySQL 은 오래 놀면 연결을 끊으므로 쓸 때마다 살아 있는지 보고 다시 붙는다.
 *
 * JDBC 드라이버는 들고 다니지 않는다 — Paper 가 sqlite-jdbc 와 mysql-connector-j 를 서버 라이브러리로 갖고 있다.
 */
class SqlSource(
    val label: String,
    private val url: String,
    private val user: String = "",
    private val password: String = "",
) {
    val dialect: SqlDialect = if (url.startsWith("jdbc:sqlite:")) SqlDialect.SQLITE else SqlDialect.MYSQL

    private var connection: Connection? = null

    fun connection(): Connection {
        val current = connection
        if (current != null && !current.isClosed && (dialect == SqlDialect.SQLITE || current.isValid(2))) return current
        runCatching { current?.close() }
        val fresh = if (user.isEmpty()) DriverManager.getConnection(url) else DriverManager.getConnection(url, user, password)
        if (dialect == SqlDialect.SQLITE) {
            fresh.createStatement().use {
                // WAL: 읽기가 쓰기를 막지 않는다. NORMAL: 커밋마다 fsync 하지 않되 WAL 이라 크래시에도 깨지지 않는다.
                it.execute("PRAGMA journal_mode=WAL")
                it.execute("PRAGMA synchronous=NORMAL")
                it.execute("PRAGMA busy_timeout=5000")
            }
        }
        connection = fresh
        return fresh
    }

    /** [block] 을 한 트랜잭션으로. 던지면 되돌리고 다시 던진다. */
    fun <T> transaction(block: (Connection) -> T): T {
        val connection = connection()
        val autoCommit = connection.autoCommit
        connection.autoCommit = false
        try {
            val result = block(connection)
            connection.commit()
            return result
        } catch (t: Throwable) {
            runCatching { connection.rollback() }
            throw t
        } finally {
            connection.autoCommit = autoCommit
        }
    }

    fun close() {
        runCatching { connection?.close() }
        connection = null
    }
}

/**
 * 로컬 SQLite 하나 + 적었으면 공용 DB 하나, 그리고 둘을 만지는 **쓰기 스레드 하나**.
 *
 * 메인 스레드는 기다리지 않는다([run]). 기다리는 것([call])은 켜질 때와 비동기 사건(접속 직전 등)에서만.
 * 스키마와 쿼리는 각 플러그인이 갖는다 — 여기는 연결과 순서만.
 */
open class SqlWorker(private val logger: Logger, threadName: String) {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, threadName).apply { isDaemon = true }
    }

    lateinit var local: SqlSource
        private set

    /** 여러 서버가 같이 쓰는 DB. 안 적었으면 null — 그때 "공용" 데이터도 [local] 을 쓴다. */
    var network: SqlSource? = null
        private set

    /**
     * 연결을 만들고 [schema] 를 두 DB 에 돌린다(기다린다). 공용 DB 주소는 비워 두면 없음.
     */
    fun open(file: File, networkUrl: String = "", networkUser: String = "", networkPassword: String = "", schema: (SqlSource) -> Unit) {
        runCatching { Class.forName("org.sqlite.JDBC") }
        local = SqlSource("local", "jdbc:sqlite:" + file.absolutePath)
        if (networkUrl.isNotBlank()) {
            runCatching { Class.forName("com.mysql.cj.jdbc.Driver") }
            network = SqlSource("network", networkUrl, networkUser, networkPassword)
        }
        call {
            schema(local)
            network?.let(schema)
        }
    }

    /** 공용 데이터가 쓰는 DB — 공용 DB 가 없으면 로컬. */
    fun source(network: Boolean): SqlSource = if (network) this.network ?: local else local

    /** 쓰기 스레드에서 돌리고 **기다린다.** 켜질 때·비동기 사건에서만. */
    fun <T> call(block: () -> T): T = executor.submit(Callable(block)).get(30, TimeUnit.SECONDS)

    /** 쓰기 스레드에 넘기고 돌아간다. 실패는 로그로. */
    fun run(what: String, block: () -> Unit) {
        if (executor.isShutdown) return
        executor.execute {
            runCatching(block).onFailure { logger.log(Level.SEVERE, "DB 작업 실패 ($what)", it) }
        }
    }

    /** 끄기. 줄 선 쓰기를 다 하고 닫는다. */
    fun shutdown() {
        executor.shutdown()
        if (!executor.awaitTermination(20, TimeUnit.SECONDS)) logger.severe("DB 쓰기가 20초 안에 끝나지 않았습니다 - 마지막 몇 건이 빠졌을 수 있습니다")
        if (::local.isInitialized) local.close()
        network?.close()
    }
}
