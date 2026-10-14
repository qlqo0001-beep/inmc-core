package kr.inmc.core.integration

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Level
import java.util.logging.Logger

/**
 * **투명 창구** (2026-10-10). 투명 상태의 주인은 inmc-essentials 의 vanish 기능이고, 사람을 이름으로 보여 주는 곳(목록·명령 제안·접속 알림·
 * 디스코드·순위 …)은 전부 여기를 물어 투명인 사람을 숨긴다 — 투명이 새는 곳을 막는 자리.
 *
 * - 상태는 플레이어 메타데이터 [META](`vanished`) — CMI·SuperVanish·PremiumVanish 와 같은 관례라 그 플러그인들이 투명하게 한 사람도 숨는다.
 *   여러 플러그인이 각자 값을 걸 수 있어 **하나라도 참이면** 투명이다.
 * - [set] 은 메타데이터와 알림만 한다. 실제로 안 보이게 하기(`hidePlayer`)는 vanish 기능이 한다.
 * - 메타데이터는 저장되지 않는다 — 다시 접속하면 vanish 기능이 다시 건다.
 * - **스레드** — Bukkit 메타데이터는 스레드에 안전하지 않다. 메인 스레드는 메타데이터를 읽고(직접 건 CMI 등도 보인다) 그 답을 거울
 *   [mirror] 에 적는다. 다른 스레드(디스코드·비동기 채팅 …)는 거울로만 답한다. 거울은 [set]·알림·메인의 읽기가 고치고, 퇴장 한 틱 뒤
 *   지운다([Listener] — 퇴장 MONITOR 까지는 메타데이터도 거울도 그대로라 TitleForge 가 읽는다).
 *   core 가 켜질 때 접속자 전원을 한 번 읽고 그 뒤 **[REFRESH_TICKS] 틱마다 전원을 다시 읽는다**([Listener.start]) — [set] 을 거치지 않고
 *   메타데이터만 건 플러그인(`/cmi vanish`)의 투명도 다른 스레드가 늦어도 1초 안에 본다(메타데이터만 읽으니 권한·PAPI 는 묻지 않는다).
 */
object Vanish {

    /** 메타데이터 열쇠 — 관례(CMI·SuperVanish 와 같다). 바꾸지 않는다. */
    const val META = "vanished"

    /** 투명인 사람을 보는 권한(core `paper-plugin.yml` 에 선언, 기본 op). */
    const val SEE_PERMISSION = "inmc.vanish.see"

    private class Watcher(val owner: String, val callback: (Player, Boolean) -> Unit)

    private val watchers = CopyOnWriteArrayList<Watcher>()

    /**
     * 어느 플러그인이든 `vanished` 를 참으로 걸었으면 투명. **꺼진 플러그인의 값은 세지 않는다** — Bukkit 은 플러그인 하나를 끌 때 그
     * 메타데이터를 지우지 않고, 다시 켜진 같은 플러그인은 새 객체라 [set] 으로도 옛 값을 못 지운다(리로드 뒤 계속 숨는다).
     */
    @JvmStatic
    fun isVanished(player: Player): Boolean {
        if (!onMain()) return mirror[player.uniqueId] ?: false
        val now = runCatching { player.getMetadata(META).any { it.owningPlugin?.isEnabled != false && it.asBoolean() } }.getOrDefault(false)
        // 나간 사람의 옛 객체로 물어도 거울에 다시 들이지 않는다(지운 뒤 새는 것을 막는다).
        if (runCatching { player.isOnline }.getOrDefault(false)) mirror[player.uniqueId] = now
        return now
    }

    /** 다른 스레드가 물을 때의 답 — 메인에서 마지막으로 본 투명 여부. 없으면 보이는 것. */
    private val mirror = ConcurrentHashMap<UUID, Boolean>()

    /** 지금 메인 스레드인가. 서버가 없으면(단위 테스트) 메인으로 친다. 테스트가 바꾼다. */
    internal val DEFAULT_ON_MAIN: () -> Boolean = { runCatching { Bukkit.isPrimaryThread() }.getOrDefault(true) }

    internal var onMain: () -> Boolean = DEFAULT_ON_MAIN

    /** 거울에 적힌 값(테스트용). */
    internal fun mirrored(id: UUID): Boolean? = mirror[id]

    /**
     * 퇴장 한 틱 뒤 — 다시 들어오지 않았으면 거울에서 지우고, 그 사이 다시 들어왔으면 새 객체로 다시 읽어 적는다(메인에서 부른다).
     * [online] 은 테스트가 바꾼다.
     */
    internal fun forget(id: UUID, online: (UUID) -> Player? = Bukkit::getPlayer) {
        val back = online(id)
        if (back == null) mirror.remove(id) else isVanished(back)
    }

    /** 거울을 다시 읽는 주기(틱). */
    const val REFRESH_TICKS = 20L

    /** [players] 전원의 메타데이터를 읽어 거울을 맞춘다(메인에서 부른다 — 켤 때 한 번, 그 뒤 [REFRESH_TICKS] 마다). */
    internal fun refresh(players: Collection<Player> = Bukkit.getOnlinePlayers()) {
        for (player in players) isVanished(player)
    }

    /**
     * [viewer] 가 [target] 을 보는가. 투명이 아니면 누구나. 투명이면 [SEE_PERMISSION] 이 있거나 자기 자신일 때만.
     * [viewer] 가 null 이면(누가 보는지 모르는 곳 — 디스코드·전체 방송) 투명인 사람은 안 보인다. 콘솔은 권한이 있어 다 본다.
     */
    @JvmStatic
    fun canSee(viewer: CommandSender?, target: Player): Boolean {
        if (!isVanished(target)) return true
        if (viewer == null) return false
        if (viewer is Player && viewer.uniqueId == target.uniqueId) return true
        return viewer.hasPermission(SEE_PERMISSION)
    }

    /** [viewer] 에게 보이는 접속자. */
    @JvmStatic
    fun visibleOnline(viewer: CommandSender?): List<Player> =
        Bukkit.getOnlinePlayers().filter { canSee(viewer, it) }

    /** [viewer] 에게 보이는 접속자 이름 — Brigadier 제안용. */
    @JvmStatic
    fun visibleNames(viewer: CommandSender?): List<String> = visibleOnline(viewer).map { it.name }

    /**
     * [plugin] 의 몫으로 투명을 걸거나 푼다. 다른 플러그인이 건 값은 건드리지 않는다. 그 결과 투명 여부가 **마지막으로 알린 것과
     * 달라졌을 때만** [listen] 한 곳에 알린다 — 메인 스레드에서(다른 스레드에서 불렀으면 그 플레이어의 스케줄러로 넘겨 그때의 상태로).
     * 넘긴 사이 되돌아갔으면 알리지 않는다 — 같은 값을 두 번, 짝 없는 "풀림"을 보내지 않는다.
     */
    @JvmStatic
    @Suppress("DEPRECATION") // Paper 가 메타데이터 API 를 낡았다고 표시했지만, 다른 투명 플러그인과 맞출 관례가 이것뿐이다.
    fun set(plugin: Plugin, player: Player, vanished: Boolean) {
        if (vanished) player.setMetadata(META, FixedMetadataValue(plugin, true)) else player.removeMetadata(META, plugin)
        if (onMain()) notify(player)
        else setOffMain(plugin, player, vanished) { player.scheduler.run(plugin, { _ -> notify(player) }, null) != null }
    }

    /**
     * 다른 스레드의 [set] — 여기서는 메타데이터를 못 읽는다. 접속 중인 사람이면 건 사람의 뜻을 먼저 거울에 적고, 넘긴 알림이 메인에서
     * 모두의 값을 읽어 고친다. 나간 사람(또는 [schedule] 이 못 받은 — 엔티티가 물러난 사람)은 거울에 들이지 않는다 — 퇴장 뒤 지운 자리에
     * 영영 남는 값을 만들지 않게. [schedule] 은 넘기기에 성공했는가(테스트가 바꾼다).
     */
    internal fun setOffMain(plugin: Plugin, player: Player, vanished: Boolean, schedule: () -> Boolean) {
        val id = player.uniqueId
        if (!runCatching { player.isOnline }.getOrDefault(false)) return
        mirror[id] = vanished
        if (!schedule()) mirror.remove(id)
    }

    /** 플레이어마다 마지막으로 알린 상태(없으면 보이는 것). 플레이어 객체로 쥔다 — 다시 접속하면 새 객체라 처음부터 센다. */
    private val lastSent: MutableMap<Player, Boolean> = java.util.Collections.synchronizedMap(java.util.WeakHashMap())

    /** 지금 상태가 마지막으로 알린 것과 다를 때만 알린다. 메인에서 읽으므로 거울도 여기서 고쳐진다. */
    internal fun notify(player: Player) {
        val now = isVanished(player)
        synchronized(lastSent) {
            if ((lastSent[player] ?: false) == now) return
            lastSent[player] = now
        }
        changed(player, now)
    }

    /** 투명 여부가 바뀔 때(접속 알림·목록·디스코드 상태를 고칠 곳). 같은 [owner] 는 교체. */
    @JvmStatic
    fun listen(owner: String, callback: (Player, Boolean) -> Unit) {
        watchers.removeAll { it.owner == owner }
        watchers += Watcher(owner, callback)
    }

    /** 내려갈 때 — 이 [owner] 의 알림을 뗀다. */
    @JvmStatic
    fun unregisterAll(owner: String) {
        watchers.removeAll { it.owner == owner }
    }

    /** 한 곳이 던져도 나머지는 받는다. */
    internal fun changed(player: Player, vanished: Boolean) {
        for (watcher in watchers) {
            try {
                watcher.callback(player, vanished)
            } catch (t: Throwable) {
                Logger.getLogger("inmc-core").log(Level.WARNING, "투명 알림 실패 (${watcher.owner})", t)
            }
        }
    }

    /**
     * core 가 등록한다([kr.inmc.core.CorePlugin]). 접속 MONITOR 에서 한 번 읽어 거울을 채우고(메타데이터를 직접 건 다른 플러그인도),
     * 퇴장은 **한 틱 뒤** 거울에서 지운다 — 퇴장 MONITOR 까지는 그대로 읽혀야 한다(TitleForge 의 퇴장 문구).
     * [start] 가 켤 때 전원을 한 번 읽고(리로드로 이미 접속해 있던 사람) [REFRESH_TICKS] 마다 다시 읽는다.
     */
    internal class Listener(private val plugin: Plugin) : org.bukkit.event.Listener {
        /** 켤 때 한 번 — 지금 접속자로 거울을 채우고 주기 읽기를 건다(플러그인이 꺼지면 작업도 같이 멈춘다). */
        fun start() {
            refresh()
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, { refresh() }, REFRESH_TICKS, REFRESH_TICKS)
        }

        @EventHandler(priority = EventPriority.MONITOR)
        fun onJoin(event: PlayerJoinEvent) {
            isVanished(event.player)
        }

        @EventHandler(priority = EventPriority.MONITOR)
        fun onQuit(event: PlayerQuitEvent) {
            val id = event.player.uniqueId
            Bukkit.getScheduler().runTaskLater(plugin, Runnable { forget(id) }, 1L)
        }
    }
}
