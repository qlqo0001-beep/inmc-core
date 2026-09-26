package kr.inmc.core

import kr.inmc.core.store.SqlDialect
import kr.inmc.core.store.SqlWorker
import java.nio.file.Files
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SqlWorkerTest {

    private fun worker(): SqlWorker {
        val dir = Files.createTempDirectory("sqlworker").toFile()
        return SqlWorker(Logger.getLogger("test"), "test-db").also { w ->
            w.open(dir.resolve("t.db")) { source ->
                source.connection().createStatement().use { it.execute("CREATE TABLE IF NOT EXISTS t (k TEXT PRIMARY KEY, v INTEGER NOT NULL)") }
            }
        }
    }

    @Test
    fun `공용 DB 를 안 적으면 공용 데이터도 로컬을 쓴다`() {
        val w = worker()
        try {
            assertNull(w.network)
            assertEquals(w.local, w.source(network = true))
            assertEquals(SqlDialect.SQLITE, w.local.dialect)
        } finally {
            w.shutdown()
        }
    }

    @Test
    fun `쓰기는 넘긴 순서대로 한 스레드에서`() {
        val w = worker()
        try {
            for (i in 1..50) w.run("쓰기 $i") {
                w.local.connection().prepareStatement("INSERT INTO t (k, v) VALUES ('a', ?) ON CONFLICT (k) DO UPDATE SET v = ?").use {
                    it.setInt(1, i); it.setInt(2, i); it.executeUpdate()
                }
            }
            val last = w.call { w.local.connection().createStatement().use { st -> st.executeQuery("SELECT v FROM t WHERE k = 'a'").use { it.next(); it.getInt(1) } } }
            assertEquals(50, last, "나중에 넘긴 쓰기가 마지막이어야 한다")
        } finally {
            w.shutdown()
        }
    }

    @Test
    fun `트랜잭션이 던지면 되돌린다`() {
        val w = worker()
        try {
            assertFailsWith<java.util.concurrent.ExecutionException> {
                w.call {
                    w.local.transaction { c ->
                        c.createStatement().use { it.execute("INSERT INTO t (k, v) VALUES ('b', 1)") }
                        error("중간에 실패")
                    }
                }
            }
            val count = w.call { w.local.connection().createStatement().use { st -> st.executeQuery("SELECT COUNT(*) FROM t WHERE k = 'b'").use { it.next(); it.getInt(1) } } }
            assertEquals(0, count)
        } finally {
            w.shutdown()
        }
    }
}
