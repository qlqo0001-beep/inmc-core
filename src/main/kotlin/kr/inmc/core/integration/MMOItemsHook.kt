package kr.inmc.core.integration

import kr.inmc.core.item.ItemRef
import org.bukkit.Bukkit
import org.bukkit.inventory.ItemStack
import java.lang.reflect.Method
import java.util.logging.Logger

/**
 * MMOItems / MythicLib access, entirely by reflection - the plugin never compiles against
 * them, so a server without either still loads this class fine.
 *
 * Identification and creation are both routed through MMOItems' own API rather than
 * reading raw NBT, because the tag layout has moved between MythicLib versions.
 */
class MMOItemsHook(private val logger: Logger) {

    private var enabled = false

    // MMOItems.plugin
    private var pluginInstance: Any? = null

    // MMOItems.plugin.getItem(String type, String id) -> ItemStack
    private var getItem: Method? = null

    // new LiveMMOItem(ItemStack) -> MMOItem, then getType().getId() / getId()
    private var liveCtor: java.lang.reflect.Constructor<*>? = null
    private var mmoGetType: Method? = null
    private var mmoGetId: Method? = null
    private var typeGetId: Method? = null

    val isEnabled: Boolean get() = enabled

    fun setup() {
        // 미설치는 여기서 알리지 않는다 — 플러그인마다 한 줄씩 열여섯 번 찍혔다. core 가 한 번 본다(`IntegrationReport`).
        if (!Bukkit.getPluginManager().isPluginEnabled("MMOItems")) return
        try {
            val mmoItemsClass = PluginClasses.require("MMOItems", "net.Indyuce.mmoitems.MMOItems")
            pluginInstance = mmoItemsClass.getField("plugin").get(null)
            getItem = mmoItemsClass.getMethod("getItem", String::class.java, String::class.java)

            val liveClass = PluginClasses.require("MMOItems", "net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem")
            liveCtor = liveClass.getConstructor(ItemStack::class.java)

            val mmoItemClass = PluginClasses.require("MMOItems", "net.Indyuce.mmoitems.api.item.mmoitem.MMOItem")
            mmoGetType = mmoItemClass.getMethod("getType")
            mmoGetId = mmoItemClass.getMethod("getId")
            typeGetId = mmoGetType!!.returnType.getMethod("getId")

            enabled = true
            logger.info("MMOItems 연동 활성화")
        } catch (t: Throwable) {
            enabled = false
            logger.warning("MMOItems 연동 실패 (버전 불일치일 수 있습니다): ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /** Returns a reference when [stack] is a MMOItems item, null for everything else. */
    fun identify(stack: ItemStack): ItemRef.MMOItems? {
        if (!enabled) return null
        return try {
            val mmoItem = liveCtor!!.newInstance(stack)
            val type = mmoGetType!!.invoke(mmoItem) ?: return null
            val typeId = typeGetId!!.invoke(type) as? String ?: return null
            val id = mmoGetId!!.invoke(mmoItem) as? String ?: return null
            if (typeId.isBlank() || id.isBlank()) return null
            ItemRef.MMOItems(typeId.uppercase(), id.uppercase())
        } catch (_: Throwable) {
            // Not an MMOItems item - LiveMMOItem throws on plain vanilla stacks.
            null
        }
    }

    fun create(ref: ItemRef.MMOItems): ItemStack? {
        if (!enabled) return null
        return try {
            getItem!!.invoke(pluginInstance, ref.type, ref.id) as? ItemStack
        } catch (t: Throwable) {
            logger.warning("MMOItems 아이템 생성 실패 (${ref.serialize()}): ${t.message}")
            null
        }
    }
}
