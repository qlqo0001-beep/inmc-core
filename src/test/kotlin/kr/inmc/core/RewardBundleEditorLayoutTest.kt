package kr.inmc.core

import kr.inmc.core.gui.Paging
import kr.inmc.core.gui.RewardBundleEditor
import kr.inmc.core.integration.CustomItemHook
import kr.inmc.core.item.ItemRef
import org.bukkit.inventory.ItemStack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 보상 편집 화면의 칸 배치(지뢰 2·12 — 겹치거나 범위 밖이면 조용히 안 그려진다)와 순수 계산. 커스텀아이템 id 목록도 여기서. */
class RewardBundleEditorLayoutTest {

    private fun slots(owner: Class<*>, prefix: String): Map<String, Int> =
        owner.declaredFields
            .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType && it.name.startsWith(prefix) }
            .associate { it.isAccessible = true; it.name to it.getInt(null) }

    @Test
    fun `목록 화면의 아래 줄 버튼은 겹치지 않고 마지막 줄 안에 있다`() {
        val buttons = slots(RewardBundleEditor::class.java, "SLOT_")
        assertEquals(9, buttons.size, "아래 줄 아홉 칸: $buttons")
        assertEquals(buttons.size, buttons.values.toSet().size, "겹치는 칸: $buttons")
        assertTrue(buttons.values.all { it in Paging.PER_PAGE until RewardBundleEditor.SIZE }, "범위 밖: $buttons")
    }

    @Test
    fun `상세 화면의 칸은 겹치지 않고 45칸 안에 있다`() {
        val tiles = slots(RewardBundleEditor::class.java, "D_")
        assertEquals(tiles.size, tiles.values.toSet().size, "겹치는 칸: $tiles")
        assertTrue(tiles.values.all { it in 0 until RewardBundleEditor.DETAIL_SIZE }, "범위 밖: $tiles")
    }

    @Test
    fun `등록 모드에서는 꽉 찬 쪽 뒤에 빈 쪽이 하나 더 있다`() {
        assertEquals(1, RewardBundleEditor.pageCount(0, addMode = true))
        assertEquals(1, RewardBundleEditor.pageCount(44, addMode = true))
        assertEquals(2, RewardBundleEditor.pageCount(45, addMode = true), "45개면 올릴 칸이 없다 — 다음 쪽")
        assertEquals(1, RewardBundleEditor.pageCount(45, addMode = false))
        assertEquals(1, RewardBundleEditor.pageCount(0, addMode = false))
    }

    @Test
    fun `명령어 글은 줄마다 하나이고 앞의 빗금과 빈 줄을 뺀다`() {
        assertEquals(listOf("give {player} diamond 1", "say hi"), RewardBundleEditor.commandsOf(" /give {player} diamond 1 \n\n  say hi\n"))
        assertEquals(emptyList(), RewardBundleEditor.commandsOf("  \n /  "))
    }

    @Test
    fun `커스텀아이템 id 목록은 목록을 주는 공급처만 모은다`() {
        val listing = object : CustomItemHook.Provider {
            override val namespace = "test-ids"
            override fun identify(stack: ItemStack): String? = null
            override fun create(ref: ItemRef.Namespaced): ItemStack? = null
            override fun ids(): Collection<String> = listOf("ruby", "sapphire")
        }
        val silent = object : CustomItemHook.Provider {
            override val namespace = "test-silent"
            override fun identify(stack: ItemStack): String? = null
            override fun create(ref: ItemRef.Namespaced): ItemStack? = null
        }
        CustomItemHook.register(listing)
        CustomItemHook.register(silent)
        try {
            val ids = CustomItemHook.ids()
            assertEquals(listOf("ruby", "sapphire"), ids["test-ids"]?.toList())
            assertTrue("test-silent" !in ids, "기본 메서드(빈 목록)는 빠진다")
        } finally {
            CustomItemHook.unregister("test-ids")
            CustomItemHook.unregister("test-silent")
        }
    }
}
