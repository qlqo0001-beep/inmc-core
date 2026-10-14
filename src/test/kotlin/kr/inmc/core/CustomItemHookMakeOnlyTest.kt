package kr.inmc.core

import kr.inmc.core.integration.CustomItemHook
import kr.inmc.core.item.ItemRef
import org.bukkit.inventory.ItemStack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 만들기 전용 공급처([CustomItemHook.Provider.makeOnly])는 "꽂혔나" 에 세지 않고, 제안 목록에는 나오며, 네임스페이스로 가려낼 수 있다. */
class CustomItemHookMakeOnlyTest {

    private class Fake(override val namespace: String, private val only: Boolean, private val list: List<String>) : CustomItemHook.Provider {
        override val makeOnly: Boolean get() = only
        override fun identify(stack: ItemStack): String? = null
        override fun create(ref: ItemRef.Namespaced): ItemStack? = null
        override fun ids(): Collection<String> = list
    }

    @Test
    fun `만들기 전용만 꽂혀 있으면 알아보는 공급처는 없는 것이다`() {
        CustomItemHook.register(Fake("TestFish", true, listOf("cod", "cod:trophy")))
        try {
            assertFalse(CustomItemHook.hasFirstParty())
            assertEquals(setOf("testfish"), CustomItemHook.makeOnlyNamespaces())
            assertEquals(listOf("cod", "cod:trophy"), CustomItemHook.ids()["TestFish"]?.toList())
            CustomItemHook.register(Fake("testitems", false, listOf("sword")))
            assertTrue(CustomItemHook.hasFirstParty())
            assertEquals(setOf("testfish"), CustomItemHook.makeOnlyNamespaces())
        } finally {
            CustomItemHook.unregister("testfish")
            CustomItemHook.unregister("testitems")
        }
        assertTrue(CustomItemHook.makeOnlyNamespaces().isEmpty())
    }

    @Test
    fun `기본은 만들기 전용이 아니다`() {
        val plain = object : CustomItemHook.Provider {
            override val namespace = "plain"
            override fun identify(stack: ItemStack): String? = null
            override fun create(ref: ItemRef.Namespaced): ItemStack? = null
        }
        assertFalse(plain.makeOnly)
    }
}
