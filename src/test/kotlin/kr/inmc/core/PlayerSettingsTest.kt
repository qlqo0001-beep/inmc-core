package kr.inmc.core

import kr.inmc.core.integration.PlayerSettings
import org.bukkit.Material
import org.bukkit.entity.Player
import java.lang.reflect.Proxy
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerSettingsTest {

    private val values = HashMap<Pair<UUID, String>, Any?>()
    private val previous = PlayerSettings.store
    private val id = UUID.randomUUID()

    private val memory = object : PlayerSettings.Store {
        override fun read(playerId: UUID, key: String): Any? = values[playerId to key]
        override fun write(playerId: UUID, key: String, value: Any?) {
            require('.' !in key) { "저장 열쇠에 점: $key" }
            if (value == null) values.remove(playerId to key) else values[playerId to key] = value
        }
    }

    private val area = PlayerSettings.Setting("test.area-mining", "테스트", "광역 채굴", Material.IRON_PICKAXE, kind = PlayerSettings.Toggle(true))
    private val time = PlayerSettings.Setting(
        "test.time", "테스트", "개인 시간", Material.CLOCK,
        kind = PlayerSettings.Choice(listOf("server" to "서버 따름", "day" to "낮", "night" to "밤"), "server"),
    )
    private val vip = PlayerSettings.Setting("test.vip", "테스트", "VIP 전용", Material.DIAMOND, kind = PlayerSettings.Toggle(false), permission = "test.vip")

    /** 권한이 [granted] 인 가짜 플레이어. 알림 시험에 쓴다. */
    private fun player(granted: Boolean = true): Player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
        when (method.name) {
            "getUniqueId" -> id
            "hasPermission" -> granted
            "getName" -> "tester"
            else -> null
        }
    } as Player

    @BeforeTest
    fun setUp() {
        PlayerSettings.store = memory
        listOf(area, time, vip).forEach(PlayerSettings::register)
    }

    @AfterTest
    fun tearDown() {
        PlayerSettings.unregisterAll("테스트")
        PlayerSettings.unlisten("테스트")
        PlayerSettings.store = previous
    }

    @Test
    fun `정하지 않았으면 기본값, 정의가 없으면 부른 쪽의 기본값`() {
        assertTrue(PlayerSettings.enabled(id, area.key))
        assertEquals("server", PlayerSettings.choice(id, time.key))
        assertFalse(PlayerSettings.enabled(id, "nobody.registered", fallback = false))
        assertEquals("x", PlayerSettings.choice(id, "nobody.registered", fallback = "x"))
    }

    @Test
    fun `정한 값을 읽는다 — 저장 열쇠에 점이 없다`() {
        assertTrue(PlayerSettings.set(id, area.key, false))
        assertFalse(PlayerSettings.enabled(id, area.key))
        assertTrue(PlayerSettings.set(id, time.key, "night"))
        assertEquals("night", PlayerSettings.choice(id, time.key))
        assertEquals(setOf("test:area-mining", "test:time"), values.keys.map { it.second }.toSet())
    }

    @Test
    fun `기본값으로 되돌리면 저장소에서 지운다`() {
        PlayerSettings.set(id, area.key, false)
        PlayerSettings.set(id, area.key, true)
        assertNull(values[id to "test:area-mining"])
    }

    @Test
    fun `맞지 않는 값은 받지 않는다`() {
        assertFalse(PlayerSettings.set(id, time.key, "dawn"), "목록에 없는 고르기")
        assertFalse(PlayerSettings.set(id, area.key, "maybe"), "켜고 끄기에 글자")
        assertFalse(PlayerSettings.set(id, "nobody.registered", true), "정의 없는 열쇠")
        assertTrue(values.isEmpty())
        // 파일을 손으로 고쳐 목록에 없는 값이 들어 있으면 기본값.
        values[id to "test:time"] = "dawn"
        assertEquals("server", PlayerSettings.choice(id, time.key))
    }

    @Test
    fun `같은 열쇠는 교체하고 owner 로 한꺼번에 뺀다`() {
        PlayerSettings.register(PlayerSettings.Setting(area.key, "테스트", "바뀐 이름", Material.STONE, kind = PlayerSettings.Toggle(false)))
        assertEquals("바뀐 이름", PlayerSettings.get(area.key)?.label)
        assertFalse(PlayerSettings.enabled(id, area.key), "교체된 정의의 기본값")
        assertEquals(1, PlayerSettings.all().count { it.key == area.key })
        PlayerSettings.unregisterAll("테스트")
        assertTrue(PlayerSettings.all().none { it.owner == "테스트" })
    }

    @Test
    fun `권한이 필요한 설정은 권한이 없으면 늘 기본값이고 바꿀 수도 없다`() {
        values[id to "test:vip"] = true
        assertTrue(PlayerSettings.enabled(player(granted = true), vip.key))
        assertFalse(PlayerSettings.enabled(player(granted = false), vip.key))
        assertFalse(PlayerSettings.set(player(granted = false), vip.key, true))
    }

    @Test
    fun `플레이어로 바꾸면 듣는 쪽에 알린다`() {
        val heard = ArrayList<String>()
        PlayerSettings.listen("테스트") { _, key -> heard += key }
        assertTrue(PlayerSettings.set(player(), time.key, "day"))
        assertEquals(listOf(time.key), heard)
        // 받지 않은 값은 알리지 않는다.
        PlayerSettings.set(player(), time.key, "dawn")
        assertEquals(1, heard.size)
    }
}
