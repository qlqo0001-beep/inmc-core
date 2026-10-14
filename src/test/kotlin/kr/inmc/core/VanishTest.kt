package kr.inmc.core

import kr.inmc.core.integration.Selections
import kr.inmc.core.integration.Vanish
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.metadata.MetadataValue
import org.bukkit.plugin.Plugin
import java.lang.reflect.Proxy
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 투명 창구의 서버 없이 볼 수 있는 부분 — 메타데이터 판정·보기 권한·알림. (Selections 의 "없으면 null" 도 여기서.) */
class VanishTest {

    private fun plugin(name: String, enabled: () -> Boolean = { true }): Plugin = Proxy.newProxyInstance(Plugin::class.java.classLoader, arrayOf(Plugin::class.java)) { proxy, method, args ->
        when (method.name) {
            "getName" -> name
            "isEnabled" -> enabled()
            "equals" -> proxy === args?.get(0)
            "hashCode" -> System.identityHashCode(proxy)
            else -> null
        }
    } as Plugin

    /** 메타데이터를 메모리에 쥐는 가짜 플레이어. */
    private fun player(permitted: Boolean = false, id: UUID = UUID.randomUUID(), online: Boolean = false): Player {
        val metadata = LinkedHashMap<String, MutableList<MetadataValue>>()
        return Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { proxy, method, args ->
            when (method.name) {
                "getUniqueId" -> id
                "getName" -> "tester"
                "hasPermission" -> permitted
                "isOnline" -> online
                "getMetadata" -> metadata[args!![0] as String]?.toList() ?: emptyList<MetadataValue>()
                "setMetadata" -> {
                    val value = args!![1] as MetadataValue
                    val list = metadata.getOrPut(args[0] as String) { ArrayList() }
                    list.removeIf { it.owningPlugin == value.owningPlugin }
                    list += value
                    null
                }
                "removeMetadata" -> {
                    metadata[args!![0] as String]?.removeIf { it.owningPlugin == args[1] }
                    null
                }
                "equals" -> proxy === args?.get(0)
                "hashCode" -> id.hashCode()
                else -> null
            }
        } as Player
    }

    @AfterTest
    fun tearDown() {
        Vanish.unregisterAll("test")
        Vanish.unregisterAll("test-b")
        Vanish.onMain = Vanish.DEFAULT_ON_MAIN
    }

    @Test
    fun `다른 스레드는 메인이 마지막으로 본 값으로 답한다`() {
        val id = UUID.randomUUID()
        val target = player(id = id, online = true)
        val cmi = plugin("CMI")
        target.setMetadata(Vanish.META, FixedMetadataValue(cmi, true)) // 메타데이터를 직접 건 다른 플러그인

        Vanish.onMain = { false }
        assertFalse(Vanish.isVanished(target), "메인이 아직 안 읽었으면 다른 스레드는 메타데이터를 읽지 않는다")

        Vanish.onMain = { true }
        assertTrue(Vanish.isVanished(target), "메인은 메타데이터를 읽는다")
        Vanish.onMain = { false }
        assertTrue(Vanish.isVanished(target), "메인이 읽은 값이 거울에 남는다")

        target.removeMetadata(Vanish.META, cmi)
        assertTrue(Vanish.isVanished(target), "메인이 다시 읽기 전까지는 옛 값")
        Vanish.onMain = { true }
        assertFalse(Vanish.isVanished(target))
        assertEquals(false, Vanish.mirrored(id))
        Vanish.forget(id) { null }
    }

    @Test
    fun `주기 읽기가 set 을 거치지 않은 투명을 거울에 들인다`() {
        val id = UUID.randomUUID()
        val target = player(id = id, online = true)
        target.setMetadata(Vanish.META, FixedMetadataValue(plugin("CMI"), true)) // /cmi vanish — 메타데이터만
        Vanish.onMain = { false }
        assertFalse(Vanish.isVanished(target), "아직 아무도 안 읽었다")
        Vanish.onMain = { true }
        Vanish.refresh(listOf(target))
        Vanish.onMain = { false }
        assertTrue(Vanish.isVanished(target), "주기 읽기 뒤에는 다른 스레드도 본다")
        Vanish.forget(id) { null }
    }

    @Test
    fun `다른 스레드의 set 은 접속 중인 사람만 거울에 적고, 넘기지 못하면 지운다`() {
        val ours = plugin("inmc-essentials")
        val goneId = UUID.randomUUID()
        val gone = player(id = goneId, online = false)
        Vanish.setOffMain(ours, gone, true) { true }
        assertNull(Vanish.mirrored(goneId), "나간 사람은 거울에 들이지 않는다")

        val id = UUID.randomUUID()
        val target = player(id = id, online = true)
        Vanish.setOffMain(ours, target, true) { true }
        assertEquals(true, Vanish.mirrored(id), "건 사람의 뜻을 먼저 적는다")
        Vanish.setOffMain(ours, target, false) { true }
        assertEquals(false, Vanish.mirrored(id))

        Vanish.setOffMain(ours, target, true) { false } // 스케줄러가 못 받음(엔티티가 물러남)
        assertNull(Vanish.mirrored(id), "넘기지 못했으면 남기지 않는다")
    }

    @Test
    fun `나간 사람의 옛 객체는 거울에 다시 들이지 않는다`() {
        val id = UUID.randomUUID()
        val gone = player(id = id, online = false)
        gone.setMetadata(Vanish.META, FixedMetadataValue(plugin("CMI"), true))
        assertTrue(Vanish.isVanished(gone), "메인에서의 답은 그대로")
        assertNull(Vanish.mirrored(id))
    }

    @Test
    fun `퇴장 한 틱 뒤 지우고, 그 사이 다시 들어왔으면 새 객체로 다시 적는다`() {
        val id = UUID.randomUUID()
        val before = player(id = id, online = true)
        before.setMetadata(Vanish.META, FixedMetadataValue(plugin("inmc-essentials"), true))
        assertTrue(Vanish.isVanished(before))
        assertEquals(true, Vanish.mirrored(id), "퇴장 MONITOR 까지는 남아 있다")

        Vanish.forget(id) { null }
        assertNull(Vanish.mirrored(id), "다시 안 들어왔으면 지운다")

        assertTrue(Vanish.isVanished(before))
        val again = player(id = id, online = true) // 한 틱 안에 다시 들어왔고 이번엔 투명이 아니다
        Vanish.forget(id) { again }
        assertEquals(false, Vanish.mirrored(id), "옛 참이 남지 않는다")
        Vanish.forget(id) { null }
    }

    @Test
    fun `어느 플러그인이든 참을 걸었으면 투명이다`() {
        val target = player()
        assertFalse(Vanish.isVanished(target))
        val cmi = plugin("CMI")
        val ours = plugin("inmc-essentials")
        target.setMetadata(Vanish.META, FixedMetadataValue(cmi, false))
        target.setMetadata(Vanish.META, FixedMetadataValue(ours, true))
        assertTrue(Vanish.isVanished(target), "하나라도 참이면 투명")
        target.removeMetadata(Vanish.META, ours)
        assertFalse(Vanish.isVanished(target), "남은 값이 거짓이면 투명이 아니다")
    }

    @Test
    fun `투명인 사람은 권한이 있거나 자기 자신일 때만 보인다`() {
        val id = UUID.randomUUID()
        val target = player(id = id)
        target.setMetadata(Vanish.META, FixedMetadataValue(plugin("inmc-essentials"), true))

        assertFalse(Vanish.canSee(null, target), "누가 보는지 모르면 숨긴다(디스코드·방송)")
        assertFalse(Vanish.canSee(player(permitted = false), target))
        assertTrue(Vanish.canSee(player(permitted = true), target))
        assertTrue(Vanish.canSee(player(id = id), target), "자기 자신은 본다")

        val visible = player()
        assertTrue(Vanish.canSee(null, visible), "투명이 아니면 누구에게나")
        assertTrue(Vanish.canSee(player() as CommandSender, visible))
    }

    @Test
    fun `알림은 주인마다 하나이고 던져도 나머지는 받는다`() {
        val got = ArrayList<Boolean>()
        Vanish.listen("test") { _, vanished -> got += vanished }
        Vanish.listen("test") { _, vanished -> got += !vanished } // 같은 주인은 교체
        Vanish.listen("test-b") { _, _ -> error("boom") }
        Vanish.changed(player(), true)
        assertEquals(listOf(false), got)

        Vanish.unregisterAll("test")
        Vanish.changed(player(), true)
        assertEquals(listOf(false), got, "뗀 뒤로는 오지 않는다")
    }

    @Test
    fun `꺼진 플러그인이 남긴 값은 세지 않는다`() {
        var on = true
        val target = player()
        target.setMetadata(Vanish.META, FixedMetadataValue(plugin("inmc-essentials") { on }, true))
        assertTrue(Vanish.isVanished(target))
        on = false
        assertFalse(Vanish.isVanished(target), "리로드로 버려진 옛 플러그인의 값")
    }

    @Test
    fun `알림은 마지막으로 알린 것과 다를 때만 — 되돌아간 것은 알리지 않는다`() {
        val got = ArrayList<Boolean>()
        Vanish.listen("test") { _, vanished -> got += vanished }
        val target = player()
        val ours = plugin("inmc-essentials")
        Vanish.notify(target)
        assertEquals(emptyList(), got, "처음부터 보이면 알릴 것이 없다")

        // 다른 스레드에서 걸고 곧바로 푼 뒤 넘긴 알림 둘이 돈다 — 둘 다 그때의 상태(보임)라 아무것도 알리지 않는다.
        target.setMetadata(Vanish.META, FixedMetadataValue(ours, true))
        target.removeMetadata(Vanish.META, ours)
        Vanish.notify(target)
        Vanish.notify(target)
        assertEquals(emptyList(), got)

        target.setMetadata(Vanish.META, FixedMetadataValue(ours, true))
        Vanish.notify(target)
        Vanish.notify(target)
        target.removeMetadata(Vanish.META, ours)
        Vanish.notify(target)
        assertEquals(listOf(true, false), got)
    }

    @Test
    fun `WorldEdit 이 없으면 선택은 null 이고 던지지 않는다`() {
        assertFalse(Selections.available)
        assertNull(Selections.of(player()))
    }
}
