package kr.inmc.core.integration

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.util.logging.Level
import java.util.logging.Logger

/**
 * **장소 이름 창구** (2026-10-15). 월드·생물군계를 보여 줄 한글 이름 — 공급처는 타이틀포지(`places.yml`), 쓰는 쪽은 inmc-worlds(월드 화면·만들기 마법사)와
 * inmc-essentials(`/바이옴찾기`). 전에는 essentials 가 타이틀포지 `biomeNameOf` 를 리플렉션으로 불렀다 — 쓰는 쪽이 둘이 되어 창구로 올렸다.
 *
 * 값은 **MiniMessage 원문**이다(타이틀포지가 그렇게 적는다). 글자로 견줘야 하는 쪽은 [plain] 으로 벗긴다.
 * 이름이 없으면 null — 부른 쪽이 월드는 id, 생물군계는 클라이언트 번역(`<lang:biome.minecraft.…>`)으로 물러난다.
 *
 * **스레드**: [world]·[biome] 은 아무 스레드에서나(공급처가 메모리 맵만 본다). [setWorld] 는 메인 스레드.
 */
object PlaceNames {

    interface Provider {
        /** 월드 이름(MiniMessage). 대소문자를 가리지 않는다. 없으면 null. */
        fun world(id: String): String?

        /** 생물군계 이름(MiniMessage). `minecraft:plains` 도 `plains` 도. 없으면 null. */
        fun biome(key: String): String?

        /** 월드 이름을 정한다. [mini] 가 null·빈칸이면 지운다. 공급처가 파일에 남긴다. */
        fun setWorld(id: String, mini: String?)
    }

    @Volatile
    private var provider: Provider? = null

    @JvmStatic
    fun register(provider: Provider) {
        this.provider = provider
    }

    /** 꽂은 그 공급처일 때만 뺀다. */
    @JvmStatic
    fun unregister(provider: Provider) {
        if (this.provider === provider) this.provider = null
    }

    @JvmStatic
    val available: Boolean get() = provider != null

    @JvmStatic
    fun world(id: String): String? = provider?.let { guard("world") { it.world(id) } }?.takeIf { it.isNotBlank() }

    @JvmStatic
    fun biome(key: String): String? = provider?.let { guard("biome") { it.biome(key) } }?.takeIf { it.isNotBlank() }

    /** 공급처가 없으면 false — 이름을 남길 곳이 없다. */
    @JvmStatic
    fun setWorld(id: String, mini: String?): Boolean {
        val current = provider ?: return false
        return guard("setWorld") { current.setWorld(id, mini); true } ?: false
    }

    /** MiniMessage 원문 → 평문(색·굵기 태그를 벗긴다). */
    @JvmStatic
    fun plain(mini: String): String =
        PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize(mini)).trim()

    private inline fun <T> guard(what: String, block: () -> T): T? = try {
        block()
    } catch (t: Throwable) {
        Logger.getLogger("inmc-core").log(Level.WARNING, "장소 이름 공급처 실패 ($what)", t)
        null
    }
}
