package kr.inmc.core

import kr.inmc.core.gui.DialogForm
import kr.inmc.core.gui.Dialogs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DialogFormTest {

    private fun valid(key: String) = key.isNotEmpty() && key.all { it.isLetterOrDigit() || it == '_' }

    @Test
    fun `입력 이름은 서버가 받는 글자로 바뀐다`() {
        assertEquals("buy_price", Dialogs.inputKey("buy-price"))
        assertEquals("fee_money", Dialogs.inputKey("fee.money"))
        assertEquals("already_fine", Dialogs.inputKey("already_fine"))
        for (raw in listOf("a-b", "x.y.z", "", "구매가", "id")) assert(valid(Dialogs.inputKey(raw))) { raw }
    }

    @Test
    fun `예약어 id 는 접두사가 붙는다 — 그대로면 버튼이 조용히 죽는다`() {
        assertEquals("in_id", Dialogs.inputKey("id"))
        assertEquals("ng_id", Dialogs.inputKey("id", "ng_"))
    }

    @Test
    fun `숫자는 쉼표를 허용해 읽는다`() {
        assertEquals(1000L, Dialogs.parseLong("1,000"))
        assertEquals(25L, Dialogs.parseLong(" 25 "))
        assertNull(Dialogs.parseLong("12.5"))
        assertEquals(1000.5, Dialogs.parseDouble("1,000.5"))
        assertNull(Dialogs.parseDouble("NaN"))
    }

    @Test
    fun `숫자 칸 검사 — 비었거나 범위 밖이면 이유를 돌려준다`() {
        val form = DialogForm("t")
            .long("buy", "구매가", 10, min = 0, max = 100)
            .long("sell", "판매가", null, min = 0, optional = true)
            .decimal("pct", "퍼센트", 5.0, min = 0.0, max = 100.0)
        assertNull(form.validate(mapOf("buy" to "50", "sell" to "", "pct" to "12.5")))
        assertNotNull(form.validate(mapOf("buy" to "", "sell" to "", "pct" to "1")))
        assertNotNull(form.validate(mapOf("buy" to "101", "sell" to "", "pct" to "1")))
        assertNotNull(form.validate(mapOf("buy" to "abc", "sell" to "", "pct" to "1")))
        assertNotNull(form.validate(mapOf("buy" to "1", "sell" to "-1", "pct" to "1")))
        assertNotNull(form.validate(mapOf("buy" to "1", "sell" to "", "pct" to "200")))
    }
}
