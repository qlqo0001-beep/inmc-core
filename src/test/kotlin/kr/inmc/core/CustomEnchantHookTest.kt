package kr.inmc.core

import kr.inmc.core.integration.CustomEnchantHook
import org.bukkit.inventory.ItemStack
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CustomEnchantHookTest {

    private class Fake(private val values: Map<String, String>) : CustomEnchantHook.Provider {
        override fun all() = listOf(CustomEnchantHook.Info("lure", "미끼", 3, "SIMPLE", "낚싯대", emptyList()))
        override fun canApply(id: String, material: String) = id == "lure" && material == "FISHING_ROD"
        override fun levels(stack: ItemStack?) = emptyMap<String, Int>()
        override fun apply(stack: ItemStack, id: String, level: Int) = id == "lure"
        override fun remove(stack: ItemStack, id: String) = false
        override fun redraw(stack: ItemStack) = Unit
        override fun data(stack: ItemStack?) = values
    }

    private var registered: CustomEnchantHook.Provider? = null

    /** 싱글톤이다. 다른 테스트에 공급처를 남기지 않는다. */
    @AfterTest
    fun unplug() {
        registered?.let(CustomEnchantHook::unregister)
    }

    private fun plug(values: Map<String, String> = emptyMap()): CustomEnchantHook.Provider =
        Fake(values).also { CustomEnchantHook.register(it); registered = it }

    @Test
    fun `공급처가 없으면 전부 없음으로 답한다`() {
        assertFalse(CustomEnchantHook.isEnabled)
        assertTrue(CustomEnchantHook.all().isEmpty())
        assertTrue(CustomEnchantHook.data(null).isEmpty())
        assertFalse(CustomEnchantHook.canApply("lure", "FISHING_ROD"))
    }

    @Test
    fun `다시 꽂으면 바꾼다 - 둘이 남지 않는다`() {
        plug(mapOf("fishing.reel-power" to "1"))
        plug(mapOf("fishing.reel-power" to "7"))
        assertEquals("7", CustomEnchantHook.data(null)["fishing.reel-power"])
    }

    @Test
    fun `남이 꽂은 것은 빼지 않는다`() {
        val mine = plug()
        CustomEnchantHook.unregister(Fake(emptyMap()))
        assertTrue(CustomEnchantHook.isEnabled)
        CustomEnchantHook.unregister(mine)
        assertFalse(CustomEnchantHook.isEnabled)
    }

    @Test
    fun `합치기는 숫자를 더하고 글자는 나중 것이 이긴다`() {
        val merged = CustomEnchantHook.merge(
            mapOf("fishing.reel-power" to "2", "fishing.rod" to "1", "note" to "a"),
            mapOf("fishing.reel-power" to "1.5", "note" to "b", "fishing.size-bonus" to "10"),
        )
        assertEquals("3.5", merged["fishing.reel-power"])
        assertEquals("1", merged["fishing.rod"])
        assertEquals("b", merged["note"])
        assertEquals("10", merged["fishing.size-bonus"])
    }

    @Test
    fun `정수 합은 소수점 없이 적는다`() {
        assertEquals("5", CustomEnchantHook.merge(mapOf("x" to "2"), mapOf("x" to "3"))["x"])
    }
}
