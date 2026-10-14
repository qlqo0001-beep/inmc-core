package kr.inmc.core.util

import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import org.bukkit.NamespacedKey
import org.bukkit.potion.PotionEffectType

/**
 * 물약 효과 — 고르는 화면과 **한국어 이름**(2026-10-14, 사용자 요청 "물약 효과도 고르는 식으로 · 한국 이름으로").
 * 이름은 게임의 번역이다: 아이템·채팅에는 `<lang:effect.minecraft.speed>`([lang] — 클라이언트가 제 언어로 그린다), 서버가 글자로 써야 하면 [name]
 * (inmc-discord 가 받아 둔 한국어 표 [VanillaNames], 없으면 id).
 *
 * ⚠ 서버가 있어야 한다(레지스트리) — 고르는 화면·뿌리는 자리에서만 부른다.
 */
object PotionEffects {

    /** 서버에 있는 효과 전부 — 한국어 이름 순(표가 없으면 id 순). */
    fun all(): List<PotionEffectType> =
        runCatching { RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT).toList() }.getOrDefault(emptyList())
            .sortedBy { name(it) }

    /** `speed` · `SPEED` · `minecraft:speed` 를 다 받는다. 모르면 null. */
    fun of(raw: String?): PotionEffectType? {
        val text = raw?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        val key = NamespacedKey.fromString(if (':' in text) text else "minecraft:" + text.replace('.', '_')) ?: return null
        return runCatching { RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT).get(key) }.getOrNull()
    }

    /** 설정에 적는 id — 바닐라면 이름공간을 뺀 것(`speed`). */
    fun id(type: PotionEffectType): String = type.key.let { if (it.namespace == NamespacedKey.MINECRAFT) it.key else it.asString() }

    /** 클라이언트가 제 언어로 그리는 이름(MiniMessage). */
    fun lang(type: PotionEffectType): String = "<lang:" + type.translationKey() + ">"

    /** 적힌 id 의 이름(MiniMessage) — 모르는 효과면 적힌 그대로. */
    fun lang(raw: String?): String = of(raw)?.let(::lang) ?: raw.orEmpty()

    /** 서버가 아는 한국어 이름(글자). 표가 없으면 id. */
    fun name(type: PotionEffectType): String = VanillaNames.ofKey(type.translationKey()) ?: id(type)
}
