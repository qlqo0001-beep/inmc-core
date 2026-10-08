package kr.inmc.core.integration

import kr.inmc.core.item.ItemRef
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.lang.reflect.Method
import java.util.logging.Logger

/**
 * Namespaced custom-item plugins (ItemsAdder, Nexo/Oraxen), reached by reflection only.
 *
 * The previous plugin compiled against the ItemsAdder API, which made the jar refuse to
 * load classes when ItemsAdder was absent. Everything here degrades to "no reference,
 * use the snapshot" instead.
 */
class CustomItemHook(private val logger: Logger) {

    /** Reflective adapters for third-party plugins. Rebuilt by [setup]. */
    private val providers = mutableListOf<Provider>()

    /**
     * Everything that can answer, ours first.
     *
     * First-party providers come from a **shared** list ([Companion.shared]) because each
     * plugin owns its own `CustomItemHook` instance. A plugin that defines items has no way
     * to reach five other instances, but core's classes are shared by every plugin
     * (`join-classpath: true`), so a companion-level list genuinely is one list.
     *
     * Ours are asked first: when both our plugin and ItemsAdder claim a stack, the one the
     * admin defined here is the better guess.
     */
    private fun all(): List<Provider> = if (shared.isEmpty()) providers else shared + providers

    val isEnabled: Boolean get() = all().isNotEmpty()

    fun setup() {
        providers.clear()
        tryItemsAdder()
        tryOraxenLike("Nexo", "com.nexomc.nexo.api.NexoItems")
        tryOraxenLike("Oraxen", "io.th0rgal.oraxen.api.OraxenItems")
        tryEcoItems()
        setupItemsAdderBlocks()
        // "없음" 은 여기서 알리지 않는다 — 우리 공급처(inmc-customitems)는 대개 이 플러그인보다 **나중에** 켜져서,
        // 켤 때 찍으면 열 플러그인이 거짓 "없음" 을 남겼다. core 가 모든 플러그인이 켜진 뒤 한 번 본다(`IntegrationReport`).
    }

    fun identify(stack: ItemStack): ItemRef.Namespaced? = identifyAll(stack).firstOrNull()

    /**
     * Every namespaced plugin that claims this stack, not just the first.
     *
     * One item can genuinely belong to two of them at once - an ItemsAdder item used as the
     * base of another plugin's template keeps both sets of tags - and the first match is only
     * a guess at which one the admin meant. Callers that have to pick one still take the head
     * of this list; the reward editor offers the whole list instead.
     */
    fun identifyAll(stack: ItemStack): List<ItemRef.Namespaced> {
        val sources = all()
        if (sources.isEmpty()) return emptyList()
        val found = LinkedHashSet<ItemRef.Namespaced>()
        for (provider in sources) {
            val id = provider.identify(stack) ?: continue
            val parts = id.split(':', limit = 2)
            found += if (parts.size == 2) {
                ItemRef.Namespaced(parts[0].lowercase(), parts[1])
            } else {
                ItemRef.Namespaced(provider.namespace, id)
            }
        }
        return found.toList()
    }

    fun create(ref: ItemRef.Namespaced): ItemStack? {
        for (provider in all()) {
            provider.create(ref)?.let { return it }
        }
        return null
    }

    /**
     * Extra values the defining plugin stored on this item.
     *
     * **This is the seam that lets one plugin define an item and another read its own numbers
     * off it.** A fishing rod defined in the custom-item plugin carries `fishing.reel-power`;
     * the fishing plugin reads it here without either side knowing the other exists.
     *
     * Empty when nothing claims the reference - the caller falls back to whatever it had,
     * rather than treating the item as broken.
     */
    fun data(ref: ItemRef.Namespaced): Map<String, String> {
        for (provider in all()) {
            val values = provider.data(ref)
            if (values.isNotEmpty()) return values
        }
        return emptyMap()
    }

    private fun tryItemsAdder() {
        if (!PluginClasses.isPresent("ItemsAdder")) return
        try {
            val customStack = PluginClasses.require("ItemsAdder", "dev.lone.itemsadder.api.CustomStack")
            val byItemStack = customStack.getMethod("byItemStack", ItemStack::class.java)
            val getInstance = customStack.getMethod("getInstance", String::class.java)
            val getNamespacedId = customStack.getMethod("getNamespacedID")
            val getItemStack = customStack.getMethod("getItemStack")

            providers += object : Provider {
                override val namespace = "itemsadder"

                override fun identify(stack: ItemStack): String? = try {
                    byItemStack.invoke(null, stack)?.let { getNamespacedId.invoke(it) as? String }
                } catch (_: Throwable) {
                    null
                }

                override fun create(ref: ItemRef.Namespaced): ItemStack? = try {
                    getInstance.invoke(null, ref.serialize())?.let { getItemStack.invoke(it) as? ItemStack }
                } catch (_: Throwable) {
                    null
                }
            }
            logger.info("ItemsAdder 연동 활성화")
        } catch (t: Throwable) {
            logger.warning("ItemsAdder 연동 실패: ${t.message}")
        }
    }

    /** Nexo and Oraxen share the same `idFromItem` / `itemFromId` API shape. */
    private fun tryOraxenLike(pluginName: String, className: String) {
        if (!Bukkit.getPluginManager().isPluginEnabled(pluginName)) return
        try {
            val api = PluginClasses.require(pluginName, className)
            val idFrom: Method = api.methods.first {
                (it.name == "idFromItem" || it.name == "getIdByItem") &&
                    it.parameterCount == 1 && it.parameterTypes[0] == ItemStack::class.java
            }
            val builderFrom: Method = api.methods.first {
                (it.name == "itemFromId" || it.name == "getItemById") && it.parameterCount == 1
            }
            val ns = pluginName.lowercase()

            providers += object : Provider {
                override val namespace = ns

                override fun identify(stack: ItemStack): String? = try {
                    (idFrom.invoke(null, stack) as? String)?.let { "$ns:$it" }
                } catch (_: Throwable) {
                    null
                }

                override fun create(ref: ItemRef.Namespaced): ItemStack? {
                    if (ref.namespace != ns) return null
                    return try {
                        val builder = builderFrom.invoke(null, ref.id) ?: return null
                        val build = builder.javaClass.methods.firstOrNull {
                            it.name == "build" && it.parameterCount == 0
                        } ?: return builder as? ItemStack
                        build.invoke(builder) as? ItemStack
                    } catch (_: Throwable) {
                        null
                    }
                }
            }
            logger.info("$pluginName 연동 활성화")
        } catch (t: Throwable) {
            logger.warning("$pluginName 연동 실패: ${t.message}")
        }
    }

    /**
     * EcoItems (the `eco` platform).
     *
     * Identification reads the `ecoitems:item` persistent-data key with plain Bukkit API - no
     * reflection needed and no coupling to eco's internals. Creation does need the API, so it
     * is resolved reflectively and tolerates either a Kotlin `object` singleton or a static
     * accessor, since eco has shipped both shapes.
     */
    private fun tryEcoItems() {
        if (!Bukkit.getPluginManager().isPluginEnabled("EcoItems")) return
        try {
            val itemsClass = PluginClasses.require("EcoItems", "com.willfp.ecoitems.items.EcoItems")
            val singleton = runCatching { itemsClass.getField("INSTANCE").get(null) }.getOrNull()
            val getByID = itemsClass.methods.first {
                (it.name == "getByID" || it.name == "getById") &&
                    it.parameterCount == 1 && it.parameterTypes[0] == String::class.java
            }
            val idKey = NamespacedKey.fromString("ecoitems:item")

            providers += object : Provider {
                override val namespace = "ecoitems"

                override fun identify(stack: ItemStack): String? {
                    if (idKey == null) return null
                    val meta = stack.itemMeta ?: return null
                    val id = meta.persistentDataContainer
                        .get(idKey, PersistentDataType.STRING)
                        ?.takeIf { it.isNotBlank() }
                        ?: return null
                    return "$namespace:$id"
                }

                override fun create(ref: ItemRef.Namespaced): ItemStack? {
                    if (ref.namespace != namespace) return null
                    return try {
                        val item = getByID.invoke(singleton, ref.id) ?: return null
                        val accessor = item.javaClass.methods.firstOrNull {
                            (it.name == "getItemStack" || it.name == "getItem") && it.parameterCount == 0
                        } ?: return null
                        (accessor.invoke(item) as? ItemStack)?.clone()
                    } catch (_: Throwable) {
                        null
                    }
                }
            }
            logger.info("EcoItems 연동 활성화")
        } catch (t: Throwable) {
            logger.warning("EcoItems 연동 실패: ${t.message}")
        }
    }

    // --- custom blocks ---------------------------------------------------------

    /**
     * Identifies an item that places a custom block, as `namespace to id`.
     *
     * A box can be configured to look like any block, including one owned by ItemsAdder, so the
     * admin registers it by holding the block item - the same gesture as the capsule item.
     * First-party providers ([Provider.isBlock], our custom-item plugin) are asked before ItemsAdder.
     */
    fun identifyBlock(stack: ItemStack): Pair<String, String>? {
        for (provider in shared) {
            val raw = provider.identify(stack) ?: continue
            val parts = raw.split(':', limit = 2)
            val ref = if (parts.size == 2) ItemRef.Namespaced(parts[0].lowercase(), parts[1]) else ItemRef.Namespaced(provider.namespace, raw)
            if (provider.isBlock(ref)) return ref.namespace to ref.id
        }
        val itemsAdder = itemsAdderBlocks ?: return null
        val id = identify(stack) ?: return null
        // Only report it as a block when ItemsAdder actually knows a block by that id.
        return if (itemsAdder.isBlock(id.serialize())) id.namespace to id.id else null
    }

    fun placeCustomBlock(block: org.bukkit.block.Block, namespace: String, id: String): Boolean {
        val ref = ItemRef.Namespaced(namespace, id)
        shared.firstOrNull { it.namespace.equals(namespace, ignoreCase = true) && it.isBlock(ref) }?.let { return it.placeBlock(block, ref) }
        val itemsAdder = itemsAdderBlocks ?: return false
        return itemsAdder.place("$namespace:$id", block)
    }

    /** Clears whatever a custom block keeps beside the block itself (a display entity, a chunk mark) before the caller restores the block. */
    fun removeCustomBlock(block: org.bukkit.block.Block): Boolean {
        if (shared.any { it.removeBlock(block) }) return true
        val itemsAdder = itemsAdderBlocks ?: return false
        return itemsAdder.remove(block)
    }

    private var itemsAdderBlocks: BlockProvider? = null

    private interface BlockProvider {
        fun isBlock(namespacedId: String): Boolean
        fun place(namespacedId: String, block: org.bukkit.block.Block): Boolean
        fun remove(block: org.bukkit.block.Block): Boolean
    }

    /** ItemsAdder's CustomBlock API, resolved reflectively alongside CustomStack. */
    private fun setupItemsAdderBlocks() {
        itemsAdderBlocks = null
        if (!PluginClasses.isPresent("ItemsAdder")) return
        try {
            val customBlock = PluginClasses.require("ItemsAdder", "dev.lone.itemsadder.api.CustomBlock")
            val getInstance = customBlock.getMethod("getInstance", String::class.java)
            val place = customBlock.methods.firstOrNull {
                it.name == "place" && it.parameterCount == 1 &&
                    it.parameterTypes[0] == org.bukkit.Location::class.java
            }
            val remove = customBlock.methods.firstOrNull {
                it.name == "remove" && it.parameterCount == 1 &&
                    it.parameterTypes[0] == org.bukkit.Location::class.java
            }

            itemsAdderBlocks = object : BlockProvider {
                override fun isBlock(namespacedId: String): Boolean = try {
                    getInstance.invoke(null, namespacedId) != null
                } catch (_: Throwable) {
                    false
                }

                override fun place(namespacedId: String, block: org.bukkit.block.Block): Boolean = try {
                    val instance = getInstance.invoke(null, namespacedId)
                    if (instance == null || place == null) false
                    else {
                        place.invoke(instance, block.location)
                        true
                    }
                } catch (t: Throwable) {
                    logger.warning("ItemsAdder 블록 배치 실패 ($namespacedId): ${t.message}")
                    false
                }

                override fun remove(block: org.bukkit.block.Block): Boolean = try {
                    remove?.invoke(null, block.location)
                    true
                } catch (_: Throwable) {
                    false
                }
            }
            logger.info("ItemsAdder 커스텀 블록 연동 활성화")
        } catch (t: Throwable) {
            logger.warning("ItemsAdder 커스텀 블록 연동 실패: ${t.message}")
        }
    }

    /**
     * One namespaced source of custom items.
     *
     * Public so a first-party plugin can plug itself in with [CustomItemHook.register] instead
     * of being discovered by reflection. The bundled ItemsAdder/Nexo/Oraxen/EcoItems adapters
     * implement the same interface, so nothing downstream treats "ours" differently.
     */
    interface Provider {
        val namespace: String
        fun identify(stack: ItemStack): String?
        fun create(ref: ItemRef.Namespaced): ItemStack?

        /**
         * Extra values stored on this definition. See [CustomItemHook.data].
         *
         * Empty for adapters wrapping a third-party plugin - those have no such notion, and
         * guessing at their stat systems would be worse than saying nothing.
         */
        fun data(ref: ItemRef.Namespaced): Map<String, String> = emptyMap()

        /**
         * This stack is ours and its tooltip changed underneath us (e.g. an enchant was applied) - redraw the
         * **whole** lore, pulling enchant lines from [CustomEnchantHook.lines]. Returns false when the stack is not
         * ours, so the caller draws its own lines instead. Must not call [CustomEnchantHook.redraw] (that would loop).
         */
        fun redraw(stack: ItemStack): Boolean = false

        /**
         * The set bonuses [player] has right now that carry **another plugin's effects** ([SetEffects.effects]).
         * The item plugin counts the pieces; the enchant engine runs the effects. Called on every combat event,
         * so it must be cheap (answer from a cache).
         */
        fun setEffects(player: Player): List<SetEffects> = emptyList()

        /** [ref] can be placed as a block ([placeBlock]). A box can then be configured to look like it (urb). */
        fun isBlock(ref: ItemRef.Namespaced): Boolean = false

        /** Places [ref] at [block]. False when it is not one of ours or could not be placed - the caller falls back to vanilla. */
        fun placeBlock(block: org.bukkit.block.Block, ref: ItemRef.Namespaced): Boolean = false

        /**
         * [block] held one of ours: clear what we keep beside the block (a display entity, a chunk mark). The block itself is left for
         * the caller to restore. False when nothing there was ours.
         */
        fun removeBlock(block: org.bukkit.block.Block): Boolean = false
    }

    /**
     * One active set bonus that carries effects for another plugin.
     *
     * @param set the set id, [name] its display name (MiniMessage), [pieces] the bonus step (2 = "2 pieces") that is active.
     * @param effects the enchant plugin's set grammar as a YAML tree (`events` · `equipped` · `unequipped` ·
     *   `disabled-worlds`). The item plugin does not know what it means and passes it through as written.
     *   **The same step keeps the same map instance until it is edited**, so the receiver may cache by identity.
     */
    data class SetEffects(val set: String, val name: String, val pieces: Int, val effects: Map<String, Any?>)

    companion object {

        /**
         * First-party providers, shared by **every** plugin's hook instance.
         *
         * Each plugin constructs its own [CustomItemHook], so an instance list cannot work
         * here - the plugin that defines items would have to find five other instances.
         * core's classes are shared across plugins (`join-classpath: true`), so this
         * companion-level list is one list for the whole server.
         *
         * Copy-on-write because it is read on every item lookup and written twice per server
         * lifetime.
         */
        private val shared = java.util.concurrent.CopyOnWriteArrayList<Provider>()

        /**
         * Plugs a first-party provider in. Call from the providing plugin's `onEnable`.
         *
         * Re-registering the same namespace **replaces** the old one. A plugin that reloads by
         * re-registering must not end up with two of itself answering, because [identifyAll]
         * would then report the same item twice.
         */
        fun register(provider: Provider) {
            shared.removeAll { it.namespace.equals(provider.namespace, ignoreCase = true) }
            shared += provider
        }

        /** 우리 공급처(inmc-customitems 등)가 하나라도 꽂혔나 — 켤 때 보고용. */
        fun hasFirstParty(): Boolean = shared.isNotEmpty()

        /** Undoes [register]. The providing plugin calls this on disable. */
        fun unregister(namespace: String) {
            shared.removeAll { it.namespace.equals(namespace, ignoreCase = true) }
        }

        /** Asks the providers whether one of them owns [stack]'s whole lore and redrew it. See [Provider.redraw]. */
        fun redraw(stack: ItemStack): Boolean = shared.any { it.redraw(stack) }

        /** Set bonuses with effects for [player], from every first-party provider. See [Provider.setEffects]. */
        fun setEffects(player: Player): List<SetEffects> = when (shared.size) {
            0 -> emptyList()
            1 -> shared[0].setEffects(player)
            else -> shared.flatMap { it.setEffects(player) }
        }

        /** Registered first-party namespaces. For diagnostics and tests. */
        fun registered(): List<String> = shared.map { it.namespace }
    }
}
