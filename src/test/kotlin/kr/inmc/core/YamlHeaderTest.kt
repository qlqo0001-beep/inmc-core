package kr.inmc.core

import kr.inmc.core.store.commentedHeader
import org.bukkit.configuration.file.YamlConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class YamlHeaderTest {

    private fun roundTrip(header: String): YamlConfiguration {
        val saved = YamlConfiguration().apply { set("items.a", 1) }.saveToString()
        return YamlConfiguration().apply { loadFromString(commentedHeader(header) + saved) }
    }

    @Test
    fun `주석 없고 끝 줄바꿈도 없는 머리말이 첫 키를 삼키지 않는다`() {
        // 고치기 전: "…있습니다.items:" 한 줄 → 다음 부팅에 items 가 없었다
        val config = roundTrip("등록된 아이템. /인벤키퍼 등록 으로 손에 든 것을 그대로 넣을 수 있습니다.")
        assertNotNull(config.getConfigurationSection("items"))
        assertEquals(1, config.getInt("items.a"))
    }

    @Test
    fun `주석 없는 머리말이 파싱 오류를 내지 않는다`() {
        val config = roundTrip("낚을 수 있는 것 전부.\n")
        assertEquals(1, config.getInt("items.a"))
    }

    @Test
    fun `이미 주석인 머리말은 한 글자도 바뀌지 않는다`() {
        val header = "# inmc-urb 상자 설정\n# MiniMessage 형식\n\n"
        assertEquals(header, commentedHeader(header))
    }

    @Test
    fun `주석과 평문이 섞이면 평문 줄만 주석이 된다`() {
        assertEquals("# 가\n# 나\n\n# 다\n", commentedHeader("가\n# 나\n\n다"))
    }

    @Test
    fun `빈 머리말은 아무것도 붙이지 않는다`() {
        assertEquals("", commentedHeader(""))
    }
}
