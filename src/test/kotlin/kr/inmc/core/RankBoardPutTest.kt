package kr.inmc.core

import kr.inmc.core.rank.Better
import kr.inmc.core.rank.Outcome
import kr.inmc.core.rank.RankBoard
import kr.inmc.core.rank.RankMode
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **파생 랭킹**(`put`)이 누적 랭킹(`record`)을 건드리지 않는지 못박는다.
 *
 * 둘은 정반대다 — `record` 는 사건을 쌓고, `put` 은 시점의 값을 덮어쓴다. 하나의 보드가 둘
 * 다를 지원하므로, `put` 을 더하면서 기존 누적 동작이 조금이라도 달라지면 숫자야구의 순위가
 * 조용히 틀어진다.
 */
class RankBoardPutTest {

    private val alice: UUID = UUID.randomUUID()
    private val bob: UUID = UUID.randomUUID()

    private fun outcome(score: Long, record: Long = score) =
        Outcome(record = record, tiebreak = 0L, score = score, summary = emptyList())

    // --- put 자체 -----------------------------------------------------------------

    @Test
    fun `put 은 덮어쓴다`() {
        val board = RankBoard()

        board.put(alice, "Alice", score = 100)
        board.put(alice, "Alice", score = 250)

        assertEquals(250L, board.entryOf(alice)!!.score, "같은 값을 두 번 올려도 불어나면 안 된다")
    }

    @Test
    fun `같은 값을 여러 번 올려도 결과가 같다`() {
        // 파생 랭킹은 주기적으로 다시 계산된다. 멱등하지 않으면 가만히 있어도 순위가 오른다.
        val board = RankBoard()

        repeat(10) { board.put(alice, "Alice", score = 42) }

        assertEquals(42L, board.entryOf(alice)!!.score)
    }

    @Test
    fun `put 한 사람은 순위에 뜬다`() {
        val board = RankBoard()

        board.put(alice, "Alice", score = 10)
        board.put(bob, "Bob", score = 30)

        val top = board.top(RankMode.CUMULATIVE_SCORE, Better.HIGHER, 10)
        assertEquals(listOf("Bob", "Alice"), top.map { it.name })
    }

    @Test
    fun `기록 모드에서도 순위에 뜬다`() {
        // 사이즈 랭킹이 이 모드를 쓴다. hasRecord 를 세우지 않으면 아무도 안 보인다.
        val board = RankBoard()

        board.put(alice, "Alice", score = 0, record = 80)

        assertTrue(board.entryOf(alice)!!.eligible(RankMode.BEST_RECORD))
        assertEquals(80L, board.top(RankMode.BEST_RECORD, Better.HIGHER, 10).first().best)
    }

    @Test
    fun `뺄 수 있다`() {
        val board = RankBoard()
        board.put(alice, "Alice", score = 10)

        assertTrue(board.remove(alice))
        assertEquals(null, board.entryOf(alice))
        assertFalse(board.remove(alice), "두 번 뺄 수는 없다")
    }

    @Test
    fun `이름이 갱신된다`() {
        val board = RankBoard()

        board.put(alice, "옛이름", score = 10)
        board.put(alice, "새이름", score = 10)

        assertEquals("새이름", board.entryOf(alice)!!.name)
    }

    // --- 기존 누적 동작이 그대로인지 -------------------------------------------------

    @Test
    fun `record 는 여전히 누적한다`() {
        val board = RankBoard()

        board.record(alice, "Alice", outcome(10), cleared = true, Better.HIGHER)
        board.record(alice, "Alice", outcome(10), cleared = true, Better.HIGHER)

        assertEquals(20L, board.entryOf(alice)!!.score, "record 는 쌓여야 한다")
        assertEquals(2L, board.entryOf(alice)!!.plays)
        assertEquals(2L, board.entryOf(alice)!!.clears)
    }

    @Test
    fun `record 는 더 좋은 기록만 남긴다`() {
        val board = RankBoard()

        board.record(alice, "Alice", outcome(0, record = 50), cleared = true, Better.HIGHER)
        board.record(alice, "Alice", outcome(0, record = 30), cleared = true, Better.HIGHER)

        assertEquals(50L, board.entryOf(alice)!!.best)
    }

    @Test
    fun `put 뒤에 record 를 해도 누적이 정상이다`() {
        // 한 보드에 두 방식이 섞이는 일은 없어야 하지만, 섞여도 record 쪽이 망가지면 안 된다.
        val board = RankBoard()

        board.put(alice, "Alice", score = 100)
        board.record(alice, "Alice", outcome(10), cleared = true, Better.HIGHER)

        assertEquals(110L, board.entryOf(alice)!!.score)
    }

    @Test
    fun `put 은 plays 를 최소 1 로 세운다`() {
        // CUMULATIVE_SCORE 모드의 eligible 이 plays 를 본다. 0 이면 순위에서 사라진다.
        val board = RankBoard()

        board.put(alice, "Alice", score = 10)

        assertTrue(board.entryOf(alice)!!.plays >= 1)
        assertTrue(board.entryOf(alice)!!.eligible(RankMode.CUMULATIVE_SCORE))
    }

    @Test
    fun `put 이 record 가 쌓아둔 plays 를 깎지 않는다`() {
        val board = RankBoard()
        board.record(alice, "Alice", outcome(10), cleared = true, Better.HIGHER)
        board.record(alice, "Alice", outcome(10), cleared = true, Better.HIGHER)

        board.put(alice, "Alice", score = 5)

        assertEquals(2L, board.entryOf(alice)!!.plays, "plays 를 1 로 되돌리면 안 된다")
    }
}
