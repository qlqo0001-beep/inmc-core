package kr.inmc.core.item

import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect

/**
 * 칸 배열·물약 효과를 바이트·글자로 (2026-10-15 — inmc-essentials 인벤 저장과 inmc-worlds 모드별 몸이 같이 쓴다. 전에는 essentials `InvData` 안에 있었다).
 * 아이템은 Paper `serializeItemsAsBytes`(DataVersion 포함 — 판이 올라가도 DataFixer 가 올린다). 빈 칸은 `ItemStack.empty()` 로 자리를 지킨다.
 */
object ItemCodec {

    @JvmStatic
    fun encode(items: Array<ItemStack?>): ByteArray =
        ItemStack.serializeItemsAsBytes(items.map { it ?: ItemStack.empty() })

    /** [size] 칸으로 되살린다 — 모자라면 빈 칸, 넘치면 버린다. 공기는 null. */
    @JvmStatic
    fun decode(bytes: ByteArray?, size: Int): Array<ItemStack?> {
        val read = if (bytes == null || bytes.isEmpty()) emptyArray() else ItemStack.deserializeItemsFromBytes(bytes)
        return Array(size) { i -> read.getOrNull(i)?.takeIf { !it.type.isAir && !it.isEmpty } }
    }

    /** `종류|남은 틱|세기|은은함|입자|아이콘` 을 `;` 로 잇는다. 모르는 종류는 뺀다. */
    @JvmStatic
    fun encodeEffects(effects: List<PotionEffect>): String = effects.joinToString(";") { e ->
        val key = Registry.EFFECT.getKey(e.type)?.toString() ?: return@joinToString ""
        listOf(key, e.duration, e.amplifier, e.isAmbient, e.hasParticles(), e.hasIcon()).joinToString("|")
    }.trim(';')

    @JvmStatic
    fun decodeEffects(text: String?): List<PotionEffect> =
        text.orEmpty().split(';').filter { it.isNotBlank() }.mapNotNull { part ->
            val f = part.split('|')
            val type = NamespacedKey.fromString(f[0])?.let { Registry.EFFECT.get(it) } ?: return@mapNotNull null
            runCatching { PotionEffect(type, f[1].toInt(), f[2].toInt(), f[3].toBoolean(), f[4].toBoolean(), f[5].toBoolean()) }.getOrNull()
        }
}
