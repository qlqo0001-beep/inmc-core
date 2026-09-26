package kr.inmc.core

import kr.inmc.core.integration.ExtraInventory
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExtraInventoryTest {

    private class Fake(override val name: String) : ExtraInventory.Provider {
        override fun items(player: Player): List<ItemStack?> = emptyList()
        override fun remove(player: Player, index: Int) = Unit
    }

    @AfterTest
    fun cleanUp() {
        for (provider in ExtraInventory.providers()) ExtraInventory.unregister(provider)
    }

    @Test
    fun `같은 이름으로 다시 꽂으면 교체된다 — 리로드가 공급처를 쌓지 않는다`() {
        val first = Fake("equipment")
        val second = Fake("equipment")
        ExtraInventory.register(first)
        ExtraInventory.register(second)
        assertEquals(listOf<ExtraInventory.Provider>(second), ExtraInventory.providers())
        ExtraInventory.register(Fake("other"))
        assertEquals(2, ExtraInventory.providers().size)
        ExtraInventory.unregister(second)
        assertTrue(ExtraInventory.providers().none { it.name == "equipment" })
    }
}
