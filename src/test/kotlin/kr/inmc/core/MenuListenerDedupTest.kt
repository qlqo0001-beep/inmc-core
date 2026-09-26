package kr.inmc.core

import kr.inmc.core.listener.MenuListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MenuListenerDedupTest {

    @Test
    fun `일곱 플러그인의 리스너가 같은 이벤트를 받아도 한 번만 처리한다`() {
        val click = Any()
        val handled = (1..7).count { MenuListener.firstDelivery(click) }
        assertEquals(1, handled)
    }

    @Test
    fun `클릭 처리 안에서 창을 닫아도 같은 클릭을 다시 처리하지 않는다`() {
        // 첫 리스너가 클릭을 처리하며 창을 닫으면 닫기 이벤트가 그 자리에서 끼어든다.
        // 그 뒤 남은 여섯 리스너가 같은 클릭을 받는다.
        val click = Any()
        val close = Any()
        assertTrue(MenuListener.firstDelivery(click))
        assertTrue(MenuListener.firstDelivery(close), "끼어든 닫기는 새 이벤트다")
        repeat(6) { assertFalse(MenuListener.firstDelivery(click)) }
    }

    @Test
    fun `다음 클릭은 다시 처리된다`() {
        val first = Any()
        val second = Any()
        assertTrue(MenuListener.firstDelivery(first))
        assertFalse(MenuListener.firstDelivery(first))
        assertTrue(MenuListener.firstDelivery(second))
        assertFalse(MenuListener.firstDelivery(second))
    }
}
