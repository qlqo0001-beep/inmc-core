package kr.inmc.core

import kr.inmc.core.integration.Realms
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 모드 창구 — 공급처가 없으면 오늘의 열쇠, 토글·기본값, 열쇠 규칙, 공급처가 던질 때. */
class RealmsTest {

    /** 메모리 공급처 — 사람마다 모드, 기능마다 "같이". */
    private class Memory : Realms.Provider {
        val modes = HashMap<UUID, String>()
        val shared = HashMap<String, Boolean>()
        var fail = false

        override fun of(playerId: UUID): String {
            if (fail) error("공급처 고장")
            return modes[playerId] ?: "survival"
        }

        override fun ofWorld(world: String): String? = if (world == "inmc_dungeon") null else "survival"
        override fun name(mode: String): String = mode
        override fun all(): List<String> = listOf("survival", "build")
        override fun isShared(key: String): Boolean = shared[key] ?: (Realms.feature(key)?.defaultShared ?: false)
        override fun spawn(mode: String): Location? = null
        override fun setSpawn(mode: String, location: Location): Boolean = false
        override fun sameChat(a: UUID, b: UUID): Boolean = of(a) == of(b)
        override fun sameTab(a: UUID, b: UUID): Boolean = of(a) == of(b)
        override fun canTravel(player: Player, to: Location): Boolean = true
        override fun travel(player: Player, to: Location): CompletableFuture<Boolean> = CompletableFuture.completedFuture(true)
    }

    private val player = UUID.randomUUID()
    private val registered = ArrayList<Realms.Provider>()

    private fun <T : Realms.Provider> register(provider: T): T = provider.also {
        Realms.register(it)
        registered += it
    }

    @AfterTest
    fun clean() {
        registered.forEach { Realms.unregister(it) }
        Realms.unregisterAll(OWNER)
    }

    @Test
    fun `공급처가 없으면 오늘의 열쇠 그대로`() {
        assertFalse(Realms.available)
        assertEquals("", Realms.scope("customitems.equipment", player))
        assertEquals("inmc_craft", Realms.key("inmc_craft", Realms.scope("customitems.craft", player)))
        assertTrue(Realms.isShared("customitems.equipment"))
        assertNull(Realms.of(player))
        assertTrue(Realms.all().isEmpty())
    }

    @Test
    fun `따로면 모드 id 를 붙이고 같이면 붙이지 않는다`() {
        val memory = register(Memory())
        memory.modes[player] = "build"
        assertEquals("build", Realms.scope("customitems.equipment", player))
        assertEquals("inmc_craft@build", Realms.key("inmc_craft", "build"))

        memory.shared["customitems.equipment"] = true
        assertEquals("", Realms.scope("customitems.equipment", player))
    }

    @Test
    fun `고르지 않은 기능은 등록한 기본값을 따른다`() {
        val memory = register(Memory())
        memory.modes[player] = "build"
        Realms.register(Realms.Feature("titleforge.titles", OWNER, "칭호", Material.NAME_TAG, defaultShared = true))
        Realms.register(Realms.Feature("customitems.equipment", OWNER, "장착 칸", Material.AMETHYST_SHARD))
        assertEquals("", Realms.scope("titleforge.titles", player))
        assertEquals("build", Realms.scope("customitems.equipment", player))
        // 등록하지 않은 열쇠도 기본은 따로다.
        assertEquals("build", Realms.scope("nobody.registered", player))
    }

    @Test
    fun `같은 열쇠는 교체하고 owner 로 한꺼번에 뺀다`() {
        Realms.register(Realms.Feature("a.b", OWNER, "하나", Material.STONE))
        Realms.register(Realms.Feature("a.b", OWNER, "둘", Material.STONE))
        assertEquals(listOf("둘"), Realms.features().filter { it.owner == OWNER }.map { it.label })
        Realms.unregisterAll(OWNER)
        assertNull(Realms.feature("a.b"))
    }

    @Test
    fun `공급처가 던지면 scope 는 같이 칸으로 물러나지 않고 던진다`() {
        val memory = register(Memory())
        memory.fail = true
        assertFailsWith<IllegalStateException> { Realms.scope("customitems.equipment", player) }
        // 알림성 조회는 물러난다.
        assertNull(Realms.of(player))
    }

    @Test
    fun `옛 공급처가 내려가며 새 공급처를 지우지 않는다`() {
        val old = register(Memory())
        val fresh = register(Memory())
        Realms.unregister(old)
        assertTrue(Realms.available)
        Realms.unregister(fresh)
        assertFalse(Realms.available)
    }

    @Test
    fun `모드 id 규칙`() {
        assertTrue(Realms.MODE_ID.matches("survival"))
        assertTrue(Realms.MODE_ID.matches("mine-farm_2"))
        assertFalse(Realms.MODE_ID.matches("반야생"))
        assertFalse(Realms.MODE_ID.matches("a.b"))
        assertFalse(Realms.MODE_ID.matches(""))
    }

    private companion object {
        const val OWNER = "시험"
    }
}
