package kr.inmc.core

import kr.inmc.core.integration.ItemRoles
import kr.inmc.core.item.ItemRef
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ItemRolesTest {

    private class FakeStore : ItemRoles.Store {
        val assigned = HashMap<Pair<String, String>, Map<String, String>>()
        override fun holders(role: String) = assigned.filterKeys { it.second == role }.map { (key, values) -> ItemRoles.Holder(ItemRef.Namespaced("inmc", key.first), values) }
        override fun rolesOf(ref: ItemRef.Namespaced) = assigned.filterKeys { it.first == ref.id }.mapKeys { it.key.second }
        override fun assign(ref: ItemRef.Namespaced, role: String, values: Map<String, String>?): Boolean {
            if (values == null) assigned.remove(ref.id to role) else assigned[ref.id to role] = values
            return true
        }
        override fun adopt(stack: ItemStack, idHint: String): ItemRef.Namespaced? = null
        override fun openEditor(player: Player, ref: ItemRef.Namespaced, role: String?) = false
    }

    @AfterTest
    fun clean() {
        ItemRoles.unregisterAll("테스트")
        ItemRoles.unregisterAll("테스트2")
    }

    private fun role(key: String, owner: String = "테스트") = ItemRoles.Role(key, owner, key, Material.PAPER,
        fields = listOf(ItemRoles.Number("minutes", "분", 1.0, 60.0, default = "30"), ItemRoles.Toggle("on", "켜기")))

    @Test
    fun `같은 key 로 다시 내놓으면 바꾼다`() {
        ItemRoles.register(role("test.a"))
        ItemRoles.register(role("test.a"))
        assertEquals(1, ItemRoles.roles().count { it.key == "test.a" })
        assertEquals(mapOf("minutes" to "30", "on" to "false"), ItemRoles.role("test.a")!!.defaults())
    }

    @Test
    fun `내려가면 역할과 알림이 같이 빠진다`() {
        ItemRoles.register(role("test.b"))
        var heard = 0
        ItemRoles.listen("테스트") { heard++ }
        ItemRoles.unregisterAll("테스트")
        ItemRoles.changed(null)
        assertTrue(ItemRoles.role("test.b") == null)
        assertEquals(0, heard)
    }

    @Test
    fun `정의하는 쪽이 꽂히고 빠질 때 알리고, 없으면 아무 역할도 없다`() {
        val heard = ArrayList<String?>()
        ItemRoles.listen("테스트") { heard += it }
        val store = FakeStore()
        assertFalse(ItemRoles.active)
        assertEquals(emptyList(), ItemRoles.holders("test.c"))
        ItemRoles.attach(store)
        try {
            assertTrue(ItemRoles.active)
            ItemRoles.assign(ItemRef.Namespaced("inmc", "보호권"), "test.c", mapOf("minutes" to "10"))
            assertEquals(listOf(ItemRoles.Holder(ItemRef.Namespaced("inmc", "보호권"), mapOf("minutes" to "10"))), ItemRoles.holders("test.c"))
        } finally {
            ItemRoles.detach(store)
        }
        assertFalse(ItemRoles.active)
        assertEquals(listOf<String?>(null, null), heard)
    }

    @Test
    fun `알림 하나가 던져도 나머지는 받는다`() {
        var heard = false
        ItemRoles.listen("테스트") { error("터짐") }
        ItemRoles.listen("테스트2") { heard = true }
        ItemRoles.changed("x")
        assertTrue(heard)
    }
}
