package kr.inmc.core

import kr.inmc.core.input.Clicks
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 유령 좌클릭 판정(2세대 낚시 `lastRightClickTick` 의 규칙).
 *
 * 우클릭으로 낚싯대를 쓰면 같은 틱에 팔 휘두르기가 `LEFT_CLICK_AIR` 로 한 번 더 온다. 그대로 받으면 미니게임에서
 * 우클릭 한 번이 "우 + 좌" 가 되어 틀린다.
 */
class ClicksTest {

    private val me: UUID = UUID.randomUUID()
    private val other: UUID = UUID.randomUUID()

    @Test
    fun `같은 틱에 우클릭 뒤에 온 좌클릭은 유령이다`() {
        val t = Clicks.Tracker()
        t.right(me, 100)
        assertTrue(t.left(me, 100))
    }

    @Test
    fun `다음 틱의 좌클릭은 진짜다`() {
        val t = Clicks.Tracker()
        t.right(me, 100)
        assertFalse(t.left(me, 101))
    }

    @Test
    fun `우클릭 없는 좌클릭은 진짜고 같은 틱의 두 번째 좌클릭은 유령이다`() {
        val t = Clicks.Tracker()
        assertFalse(t.left(me, 100))
        assertTrue(t.left(me, 100), "한 틱에 좌클릭은 한 번만 — 블록 치기와 손 흔들기가 둘 다 온다")
        assertFalse(t.left(me, 101))
    }

    @Test
    fun `남의 우클릭은 내 좌클릭을 유령으로 만들지 않는다`() {
        val t = Clicks.Tracker()
        t.right(other, 100)
        assertFalse(t.left(me, 100))
    }

    @Test
    fun `지우면 같은 틱이어도 진짜다`() {
        // 검증기는 한 틱에 여러 번 누른다 — 누르기 전에 지운다.
        val t = Clicks.Tracker()
        t.right(me, 100)
        t.forget(me)
        assertFalse(t.left(me, 100))
    }
}
