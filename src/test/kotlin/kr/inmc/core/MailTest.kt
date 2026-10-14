package kr.inmc.core

import kr.inmc.core.integration.Mail
import kr.inmc.core.reward.Mailbox
import kr.inmc.core.reward.RewardHost
import kr.inmc.core.reward.RewardSettings
import kr.inmc.core.util.Placeholders
import org.bukkit.entity.Player
import java.lang.reflect.Proxy
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 우편 창구와, core 우편함이 공급처로 넘기는 길(넘기기·두 순서의 적재·거절). */
class MailTest {

    /** 메모리 공급처 — origin 별로 센다. [accept] 가 false 면 거절한다. 같은 사람·origin·key 는 한 통(계약). */
    private class Memory(var accept: Boolean = true) : Mail.Provider {
        val parcels = ArrayList<Triple<String, UUID, Mail.Parcel>>()
        private var next = 0

        override fun deposit(playerId: UUID, parcel: Mail.Parcel): Boolean {
            if (!accept) return false
            if (parcel.key != null && parcels.any { it.second == playerId && it.third.origin == parcel.origin && it.third.key == parcel.key }) return true
            parcels += Triple("p" + next++, playerId, parcel)
            return true
        }

        private fun of(playerId: UUID, origin: String?) = parcels.filter { it.second == playerId && (origin == null || it.third.origin == origin) }

        override fun count(playerId: UUID, origin: String?): Int = of(playerId, origin).size
        override fun entries(playerId: UUID, origin: String?): List<Pair<String, Mail.Parcel>> = of(playerId, origin).map { it.first to it.third }
        override fun claim(player: Player, origin: String?): Mailbox.ClaimResult = Mailbox.ClaimResult(0, 0, false)
        override fun discard(playerId: UUID, origin: String?): Int {
            val gone = of(playerId, origin)
            parcels.removeAll(gone.toSet())
            return gone.size
        }
    }

    private val registered = ArrayList<Mail.Provider>()
    private val boxes = ArrayList<Mailbox>()
    private val player = UUID.randomUUID()

    private fun <T : Mail.Provider> register(provider: T): T = provider.also {
        Mail.register(it)
        registered += it
    }

    /** 서버 없이 쓸 호스트 — 한도만 답한다. 플러그인·파일은 null(우편함이 그 길에서 던지지 않는지도 본다). */
    private fun mailbox(limit: Int = 54): Mailbox {
        val settings = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(RewardSettings::class.java)) { _, method, _ ->
            when (method.name) {
                "getMailboxLimit" -> limit
                "getMailboxExpireSeconds" -> 0L
                "getSeasonArchiveLimit" -> 0
                else -> false
            }
        } as RewardSettings
        // 숫자야구 Ph 처럼 player 에 별칭 셋을 건다.
        val host = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(RewardHost::class.java)) { _, method, args ->
            when (method.name) {
                "getRewardSettings" -> settings
                "placeholders" -> {
                    @Suppress("UNCHECKED_CAST")
                    val pairs = args!![0] as Array<Pair<String, String>>
                    val name = pairs.first { it.first == "player" }.second
                    Placeholders { raw -> listOf("{플레이어네임}", "{플레이어}", "{player}").fold(raw) { text, alias -> text.replace(alias, name) } }
                }
                else -> null
            }
        } as RewardHost
        return Mailbox(host).also {
            it.originOverride = "inmc-test"
            boxes += it
        }
    }

    private fun entry(source: String, money: Double = 100.0) = Mailbox.Entry(source = source, money = money)

    @AfterTest
    fun tearDown() {
        boxes.forEach { it.close() }
        registered.forEach(Mail::unregister)
    }

    // --- 창구 ---------------------------------------------------------------------------

    @Test
    fun `공급처가 없으면 보내기는 false 이고 읽기는 비어 있다`() {
        assertFalse(Mail.available)
        assertFalse(Mail.send(player, "inmc-test", "시험", money = 10.0))
        assertEquals(0, Mail.count(player))
        assertNull(Mail.claim(proxyPlayer(player)))
    }

    @Test
    fun `보내기는 보상으로 넣고 빈 우편은 넣지 않는다`() {
        val memory = register(Memory())
        assertTrue(Mail.send(player, "inmc-test", "시즌 1위", money = 500.0, moneyBy = mapOf("cash" to 3.0, "zero" to 0.0), commands = listOf("say hi")))
        assertFalse(Mail.send(player, "inmc-test", "빈 것"), "넣을 것이 없으면 false")
        val parcel = memory.parcels.single().third
        assertEquals(Mail.Kind.REWARD, parcel.kind)
        assertEquals("inmc-test", parcel.origin)
        assertEquals(mapOf("cash" to 3.0), parcel.moneyBy, "0 인 화폐는 빠진다")
        assertEquals(1, Mail.count(player, "inmc-test"))
        assertEquals(0, Mail.count(player, "다른-플러그인"))
    }

    @Test
    fun `옛 공급처가 내려가도 새 공급처는 남고 알림은 바뀔 때마다 온다`() {
        var calls = 0
        Mail.listen("test-watch") { calls++ }
        try {
            val old = register(Memory())
            val fresh = register(Memory())
            Mail.unregister(old)
            assertTrue(Mail.available, "옛것을 빼도 새것은 남는다")
            assertEquals(2, calls, "옛것 빼기는 꽂힌 것이 아니라 알리지 않는다")
            Mail.unregister(fresh)
            assertFalse(Mail.available)
            assertEquals(3, calls)
        } finally {
            Mail.unlisten("test-watch")
        }
    }

    @Test
    fun `알림 하나가 던져도 나머지는 받는다`() {
        var reached = false
        Mail.listen("test-a") { error("boom") }
        Mail.listen("test-b") { reached = true }
        try {
            register(Memory())
            assertTrue(reached)
        } finally {
            Mail.unlisten("test-a")
            Mail.unlisten("test-b")
        }
    }

    // --- core 우편함 위임 ------------------------------------------------------------------

    @Test
    fun `공급처가 없으면 제 우편함에 두고 오래된 것부터 한도로 지운다`() {
        val box = mailbox(limit = 2)
        box.accept(emptyMap())
        box.deposit(player, entry("1"))
        box.deposit(player, entry("2"))
        box.deposit(player, entry("3"))
        assertEquals(listOf("2", "3"), box.entriesOf(player).map { it.source })
    }

    @Test
    fun `공급처가 있으면 넣기·세기·읽기·버리기가 그리로 간다`() {
        val memory = register(Memory())
        val box = mailbox(limit = 1)
        box.accept(emptyMap())
        repeat(3) { box.deposit(player, entry("보상 $it")) }
        assertEquals(3, memory.parcels.size, "공급처는 한도로 지우지 않는다")
        assertTrue(memory.parcels.all { it.third.origin == "inmc-test" && it.third.kind == Mail.Kind.REWARD })
        assertEquals(3, box.countOf(player))
        assertEquals(listOf("보상 0", "보상 1", "보상 2"), box.entriesOf(player).map { it.source })
        assertEquals(100.0, box.entriesOf(player).first().money)
        assertEquals(3, box.discard(player))
        assertEquals(0, box.countOf(player))
    }

    @Test
    fun `다 읽은 뒤 공급처가 꽂히면 남은 것을 한 번 넘긴다`() {
        val box = mailbox()
        box.accept(mapOf(player to listOf(entry("파일 1"), entry("파일 2"))))
        assertEquals(2, box.countOf(player))

        val memory = register(Memory())
        assertEquals(listOf("파일 1", "파일 2"), memory.parcels.map { it.third.source })
        assertEquals(2, box.countOf(player), "넘긴 뒤에는 공급처 쪽만 센다 — 두 번 세지 않는다")

        // 같은 공급처가 다시 꽂혀도(리로드) 이미 넘긴 것은 다시 가지 않는다.
        Mail.register(memory)
        assertEquals(2, memory.parcels.size)
    }

    @Test
    fun `공급처가 읽기보다 먼저 꽂혀도 늦게 읽힌 것까지 넘어가고 두 번 가지 않는다`() {
        val box = mailbox()
        box.deposit(player, entry("읽기 전에 들어온 것"))
        val memory = register(Memory())
        assertEquals(listOf("읽기 전에 들어온 것"), memory.parcels.map { it.third.source })

        // 파일 읽기가 이제야 끝났다 — 파일에는 아직 쓰지 않았으므로(다 읽기 전에는 쓰지 않는다) 그 한 통은 없다.
        box.accept(mapOf(player to listOf(entry("파일"))))
        assertEquals(listOf("읽기 전에 들어온 것", "파일"), memory.parcels.map { it.third.source })
        assertEquals(2, box.countOf(player))
    }

    @Test
    fun `공급처가 거절하면 제 우편함에 남고 한도로 지우지 않으며 다음에 넘긴다`() {
        val memory = register(Memory(accept = false))
        val box = mailbox(limit = 1)
        box.accept(emptyMap())
        box.deposit(player, entry("1"))
        box.deposit(player, entry("2"))
        assertEquals(2, box.countOf(player), "공급처가 있을 때는 남은 것을 지우지 않는다")

        memory.accept = true
        Mail.register(memory)
        assertEquals(2, memory.parcels.size)
        assertEquals(2, box.countOf(player))
    }

    @Test
    fun `넘길 때 명령어의 호스트 별칭은 {player} 로 바뀐다`() {
        val memory = register(Memory())
        val box = mailbox()
        box.accept(emptyMap())
        box.deposit(player, Mailbox.Entry(source = "시즌", commands = mutableListOf("lp user {플레이어네임} permission set vip", "broadcast {플레이어}님 %server_name%")))
        assertEquals(
            listOf("lp user {player} permission set vip", "broadcast {player}님 %server_name%"),
            memory.parcels.single().third.commands,
        )
    }

    @Test
    fun `다시 넘겨도 공급처에는 한 통 — 열쇠는 id 와 시각`() {
        val memory = register(Memory())
        val same = entry("파일")
        mailbox().accept(mapOf(player to listOf(same)))
        // 파일에서 빠지기 전에 죽고 다시 켜졌다 — 같은 통을 또 읽어 넘긴다.
        mailbox().accept(mapOf(player to listOf(same)))
        assertEquals(1, memory.parcels.size)
        assertEquals(same.id + "@" + same.at, memory.parcels.single().third.key)
    }

    @Test
    fun `읽기 전에 들어와 남은 것은 읽은 것 뒤에 붙는다`() {
        val box = mailbox()
        box.deposit(player, entry("읽기 전"))
        box.accept(mapOf(player to listOf(entry("파일"))))
        assertEquals(listOf("파일", "읽기 전"), box.entriesOf(player).map { it.source })
    }

    @Test
    fun `공급처가 없을 때의 초기화는 다시 꽂히면 공급처 쪽에 걸리고 그 사이 들어온 것은 남는다`() {
        val memory = register(Memory())
        val box = mailbox()
        box.accept(emptyMap())
        box.deposit(player, entry("옛 보상"))
        Mail.unregister(memory)

        assertEquals(0, box.discard(player), "공급처 몫은 셀 수 없다")
        box.deposit(player, entry("초기화 뒤"))
        Mail.register(memory)
        assertEquals(listOf("초기화 뒤"), memory.parcels.map { it.third.source })
    }

    private fun proxyPlayer(id: UUID): Player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
        if (method.name == "getUniqueId") id else null
    } as Player
}
