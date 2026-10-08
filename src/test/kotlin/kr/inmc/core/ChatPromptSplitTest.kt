package kr.inmc.core

import kr.inmc.core.input.ChatPrompt
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatPromptSplitTest {

    @Test
    fun `첫 줄은 제목, 나머지는 설명 — 빈 줄은 뺀다`() {
        val (title, body) = ChatPrompt.split(listOf("<yellow>강화 성공 확률(%)을 입력하세요.</yellow>", "", "<gray>범위: 0 ~ 100</gray>"))
        assertEquals("<yellow>강화 성공 확률(%)을 입력하세요.</yellow>", title)
        assertEquals(listOf("<gray>범위: 0 ~ 100</gray>"), body)
    }

    @Test
    fun `줄이 없으면 제목은 입력`() {
        assertEquals("입력" to emptyList(), ChatPrompt.split(emptyList()))
        assertEquals("입력" to emptyList(), ChatPrompt.split(listOf("  ")))
    }
}
