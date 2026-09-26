package kr.inmc.core.item

import kr.inmc.core.integration.CustomItemHook
import org.bukkit.Material
import org.bukkit.Registry
import org.bukkit.block.Block
import org.bukkit.block.data.BlockData
import org.bukkit.inventory.ItemStack
import java.util.logging.Logger

/**
 * The block a spawned box appears as.
 *
 * Two things forced this to become its own type rather than a bare [Material]:
 *
 *  1. The old picker listed every material and called `Material.isBlock()` on each one. That
 *     goes through `CraftMagicNumbers.fromLegacy` and triggers `CraftLegacy`'s static
 *     initialiser, which runs Mojang's whole legacy DataFixer - on a live server it froze the
 *     main thread for over ten seconds and tripped the watchdog. Nothing here ever calls
 *     `isBlock()`; the registry answers the same question for a single material instantly.
 *  2. Custom-block plugins (ItemsAdder and friends) need a namespaced id, which a Material
 *     cannot express at all.
 */
sealed interface BlockRef {

    fun serialize(): String

    /** Material shown in menus - a custom block still has a vanilla base to draw. */
    val iconMaterial: Material

    data class Vanilla(val material: Material) : BlockRef {
        override fun serialize(): String = "minecraft:" + material.key().value()
        override val iconMaterial: Material get() = material
    }

    /** A custom block owned by another plugin, placed through its API. */
    data class Custom(val namespace: String, val id: String, val fallback: Material) : BlockRef {
        override fun serialize(): String = "$namespace:$id"
        override val iconMaterial: Material get() = fallback
    }

    companion object {
        val DEFAULT: BlockRef = Vanilla(Material.CHEST)

        /**
         * True when [material] can actually stand in the world.
         *
         * Uses the block registry rather than `Material.isBlock()` - same answer, none of the
         * legacy-conversion cost described above.
         */
        fun isPlaceable(material: Material): Boolean =
            !material.isAir && runCatching { Registry.BLOCK.get(material.key()) != null }.getOrDefault(false)

        fun parse(raw: String?, logger: Logger? = null): BlockRef {
            if (raw.isNullOrBlank()) return DEFAULT
            val text = raw.trim()
            val parts = text.split(':', limit = 2)

            if (parts.size < 2 || parts[0].equals("minecraft", ignoreCase = true)) {
                val name = if (parts.size < 2) parts[0] else parts[1]
                val material = Material.matchMaterial(name)
                    ?: Material.matchMaterial("minecraft:" + name.lowercase())
                if (material == null || !isPlaceable(material)) {
                    logger?.warning("상자 블록을 인식할 수 없습니다: $text (기본값 CHEST 사용)")
                    return DEFAULT
                }
                return Vanilla(material)
            }

            // namespace:id - resolved by whichever custom-block plugin claims it
            return Custom(parts[0].lowercase(), parts[1], Material.CHEST)
        }

        /**
         * Derives a reference from an item the admin is holding. Returns null when the item
         * cannot be placed as a block, so the GUI can refuse it with a clear reason.
         */
        fun fromItem(stack: ItemStack, customItems: CustomItemHook): BlockRef? {
            customItems.identifyBlock(stack)?.let { (namespace, id) ->
                return Custom(namespace, id, stack.type)
            }
            if (!isPlaceable(stack.type)) return null
            return Vanilla(stack.type)
        }
    }
}

/** Places [ref] into the world, falling back to vanilla when a custom plugin cannot help. */
class BlockPlacer(private val customItems: CustomItemHook, private val logger: Logger) {

    /** @return the [BlockData] actually written, for the restore snapshot to compare against. */
    fun place(block: Block, ref: BlockRef): Boolean {
        return when (ref) {
            is BlockRef.Vanilla -> {
                // applyPhysics = false: no neighbour updates, so nothing pops off or flows in.
                block.setBlockData(ref.material.createBlockData(), false)
                true
            }

            is BlockRef.Custom -> {
                if (customItems.placeCustomBlock(block, ref.namespace, ref.id)) return true
                logger.warning("커스텀 블록 ${ref.serialize()} 을(를) 배치할 수 없어 ${ref.fallback.name} 으로 대체합니다")
                block.setBlockData(ref.fallback.createBlockData(), false)
                true
            }
        }
    }

    /** Removes any custom-block bookkeeping before the vanilla block is restored. */
    fun clear(block: Block, ref: BlockRef) {
        if (ref is BlockRef.Custom) customItems.removeCustomBlock(block)
    }
}
