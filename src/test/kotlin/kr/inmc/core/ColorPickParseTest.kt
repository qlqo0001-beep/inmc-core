package kr.inmc.core

import kr.inmc.core.gui.ColorPickMenu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 색 고르기의 직접 입력이 받는 모양(2026-10-14). */
class ColorPickParseTest {

    @Test
    fun `hex · 태그 · RGB · 색 이름을 다 받는다`() {
        assertEquals(0xFFAA00, ColorPickMenu.parse("#FFAA00"))
        assertEquals(0xFFAA00, ColorPickMenu.parse("<#ffaa00>"))
        assertEquals(0xFFAA00, ColorPickMenu.parse("ffaa00"))
        assertEquals(0xFFAA00, ColorPickMenu.parse("255,170,0"))
        assertEquals(0xFFAA00, ColorPickMenu.parse("255 170 0"))
        assertEquals(0xFFAA00, ColorPickMenu.parse("gold"))
        assertEquals(0xFFAA00, ColorPickMenu.parse("<GOLD>"))
    }

    @Test
    fun `모르는 것은 null`() {
        assertNull(ColorPickMenu.parse("금색"))
        assertNull(ColorPickMenu.parse("256,0,0"))
        assertNull(ColorPickMenu.parse("#FFF"))
        assertNull(ColorPickMenu.parse(""))
    }
}
