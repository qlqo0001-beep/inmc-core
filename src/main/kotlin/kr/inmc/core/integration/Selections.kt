package kr.inmc.core.integration

import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.logging.Level
import java.util.logging.Logger

/**
 * **WorldEdit/FAWE 선택 읽기** (2026-10-10) — 나무 도끼로 찍은 두 모서리를 상자 하나로. essentials 포탈·영역, urb 범위, 던전 방 저장이 쓴다.
 *
 * core 는 WorldEdit 에 컴파일 의존을 두지 않는다(가이드 5.8 — 없는 서버에서 `NoClassDefFoundError`). 길은 던전 `Schematics.selection` 과 같다:
 * `WorldEdit.getInstance().getSessionManager().getIfPresent(BukkitAdapter.adapt(player))` → `getSelectionWorld()` →
 * `getSelection(world)` → `Region.getMinimumPoint()/getMaximumPoint()` → `BlockVector3.x()/y()/z()`. 메서드는 그 플러그인 인스턴스마다 한 번 찾는다.
 *
 * 메인 스레드에서 부른다. 선택이 없거나 덜 됐거나(한 모서리만) WorldEdit 이 없거나 모양이 바뀌었으면 **null — 던지지 않는다.**
 */
object Selections {

    /** 고른 상자(양 끝 포함). [world] 는 선택한 월드 — 서 있는 월드와 다를 수 있다. */
    data class Selection(
        val world: World,
        val minX: Int,
        val minY: Int,
        val minZ: Int,
        val maxX: Int,
        val maxY: Int,
        val maxZ: Int,
    )

    /** FAWE 는 `provides: [WorldEdit]` 로 이 이름도 채우지만, 못 찾으면 제 이름으로 한 번 더. */
    private val PLUGIN_NAMES = listOf("WorldEdit", "FastAsyncWorldEdit")

    private class Handles(
        val getInstance: Method,
        val getSessionManager: Method,
        val adaptPlayer: Method,
        val sessionOf: Method,
        val getSelectionWorld: Method,
        val getSelection: Method,
        val adaptWorld: Method,
        val minimum: Method,
        val maximum: Method,
        val x: Method,
        val y: Method,
        val z: Method,
    )

    @Volatile
    private var cachedOwner: Plugin? = null

    /** [cachedOwner] 로 찾은 결과. 실패했으면 null 로 남겨 같은 인스턴스에서 다시 찾지 않는다. */
    @Volatile
    private var cachedHandles: Handles? = null

    private fun worldEdit(): Plugin? =
        PLUGIN_NAMES.firstNotNullOfOrNull { name -> Bukkit.getPluginManager().getPlugin(name)?.takeIf { it.isEnabled } }

    /** WorldEdit(또는 FAWE)이 켜져 있다. 선택이 있다는 뜻은 아니다. */
    @JvmStatic
    val available: Boolean get() = runCatching { worldEdit() != null }.getOrDefault(false)

    /** [player] 의 WorldEdit 선택. 없으면 null. */
    @JvmStatic
    fun of(player: Player): Selection? {
        val owner = runCatching { worldEdit() }.getOrNull() ?: return null
        val handles = handles(owner) ?: return null
        return try {
            val worldEdit = handles.getInstance.invoke(null) ?: return null
            val manager = handles.getSessionManager.invoke(worldEdit) ?: return null
            val actor = handles.adaptPlayer.invoke(null, player) ?: return null
            val session = handles.sessionOf.invoke(manager, actor) ?: return null
            val weWorld = handles.getSelectionWorld.invoke(session) ?: return null
            val region = handles.getSelection.invoke(session, weWorld) ?: return null
            val world = handles.adaptWorld.invoke(null, weWorld) as? World ?: return null
            val min = handles.minimum.invoke(region) ?: return null
            val max = handles.maximum.invoke(region) ?: return null
            Selection(
                world,
                handles.x.invoke(min) as Int, handles.y.invoke(min) as Int, handles.z.invoke(min) as Int,
                handles.x.invoke(max) as Int, handles.y.invoke(max) as Int, handles.z.invoke(max) as Int,
            )
        } catch (_: InvocationTargetException) {
            // 대개 IncompleteRegionException(한 모서리만 찍음) — 평범한 "선택 없음"이다.
            null
        } catch (t: Throwable) {
            Logger.getLogger("inmc-core").log(Level.FINE, "WorldEdit 선택 읽기 실패", t)
            null
        }
    }

    private fun handles(owner: Plugin): Handles? {
        if (cachedOwner === owner) return cachedHandles
        val found = try {
            lookup(owner)
        } catch (t: Throwable) {
            Logger.getLogger("inmc-core").warning("WorldEdit 선택 연동 실패 (버전 불일치일 수 있습니다): ${t.javaClass.simpleName} ${t.message}")
            null
        }
        cachedHandles = found
        cachedOwner = owner
        return found
    }

    /**
     * 메서드는 **선언된 공개 형**에서 찾는다(구현 클래스는 공개가 아닐 수 있어 `invoke` 가 IllegalAccess 를 낸다) — 반환형을 따라 내려간다.
     */
    private fun lookup(owner: Plugin): Handles {
        val name = owner.name
        val worldEditClass = PluginClasses.require(name, "com.sk89q.worldedit.WorldEdit")
        val adapter = PluginClasses.require(name, "com.sk89q.worldedit.bukkit.BukkitAdapter")
        val weWorldClass = PluginClasses.require(name, "com.sk89q.worldedit.world.World")

        val getInstance = worldEditClass.getMethod("getInstance")
        val getSessionManager = worldEditClass.getMethod("getSessionManager")
        val adaptPlayer = adapter.methods.first {
            it.name == "adapt" && it.parameterCount == 1 && it.parameterTypes[0] == Player::class.java
        }
        val managerClass = getSessionManager.returnType
        // getIfPresent 는 세션을 새로 만들지 않는다 — 처음 보는 사람에게 빈 세션을 만들 까닭이 없다.
        val sessionOf = (managerClass.methods.firstOrNull { it.name == "getIfPresent" && it.parameterCount == 1 }
            ?: managerClass.methods.first { it.name == "get" && it.parameterCount == 1 })
        val sessionClass = sessionOf.returnType
        val getSelectionWorld = sessionClass.getMethod("getSelectionWorld")
        val getSelection = sessionClass.getMethod("getSelection", weWorldClass)
        val adaptWorld = adapter.methods.first {
            it.name == "adapt" && it.parameterCount == 1 && it.parameterTypes[0] == weWorldClass && it.returnType == World::class.java
        }
        val regionClass = getSelection.returnType
        val minimum = regionClass.getMethod("getMinimumPoint")
        val maximum = regionClass.getMethod("getMaximumPoint")
        val vector = minimum.returnType
        fun axis(modern: String, legacy: String): Method =
            vector.methods.firstOrNull { it.name == modern && it.parameterCount == 0 }
                ?: vector.getMethod(legacy)
        return Handles(
            getInstance, getSessionManager, adaptPlayer, sessionOf, getSelectionWorld, getSelection, adaptWorld,
            minimum, maximum, axis("x", "getBlockX"), axis("y", "getBlockY"), axis("z", "getBlockZ"),
        )
    }
}
