package kr.inmc.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ApiVersionCheckTest {

    @Test
    fun `판 문자열에서 숫자만 — build 와 접미사는 뺀다`() {
        assertEquals(listOf(26, 1, 2), ApiVersionCheck.numbers("26.1.2.build.69-stable"))
        assertEquals(listOf(26, 2), ApiVersionCheck.numbers("26.2"))
        assertEquals(listOf(1, 21, 8), ApiVersionCheck.numbers("1.21.8-R0.1-SNAPSHOT"))
    }

    @Test
    fun `서버가 오래됐을 때만 경고한다`() {
        // 테섭 그대로: Leaf 26.2 build 46 이 품은 26.1.2 < 컴파일 26.2
        assertNotNull(ApiVersionCheck.warning("26.2", "26.1.2.build.69-stable"))
        assertNull(ApiVersionCheck.warning("26.2", "26.2.build.46-stable"))
        assertNull(ApiVersionCheck.warning("26.2", "26.3.build.1"))
        assertNull(ApiVersionCheck.warning("26.1", "26.1.2.build.69-stable"), "26.1 로 컴파일했으면 26.1.2 는 충분하다")
    }

    @Test
    fun `하나라도 모르면 조용하다`() {
        assertNull(ApiVersionCheck.warning(null, "26.1.2.build.69-stable"))
        assertNull(ApiVersionCheck.warning("26.2", null))
        assertNull(ApiVersionCheck.warning("\${paper}", "26.1.2"), "치환이 안 된 자리표시는 판이 아니다")
    }
}
