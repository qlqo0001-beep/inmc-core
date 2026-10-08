package kr.inmc.core.integration

import kr.inmc.core.CorePlugin
import org.bukkit.Material
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * **플레이어 개인 설정 창구** (2026-10-02). 플러그인이 자기 설정(광역 채굴 끄기·배낭 자동 수납 끄기 …)을 [register] 로 올리고,
 * 설정 화면을 그리는 쪽(inmc-menu)이 [all] 을 읽어 한 화면에 모아 보여 준다 — [ItemRoles] 와 같은 허브 방식. 올리는 플러그인은 화면을 모르고,
 * 화면은 그 설정의 뜻을 모른다.
 *
 * - 값은 core [kr.inmc.core.store.PlayerStore] 의 네임스페이스 [NAMESPACE] 에 둔다. 열쇠의 `.` 는 `:` 로 바꿔 적는다 — YAML 이 점을 경로로
 *   읽어 항목이 사라진다(지뢰 1). 읽기는 메모리 캐시라 **블록을 캘 때마다 물어도 된다.**
 * - 정하지 않았으면 정의의 기본값, **정의가 없으면 부른 쪽이 준 `fallback`** — 설정을 올린 플러그인이 꺼져 있거나 늦게 켜져도 안전하다.
 * - `permission` 이 있는 설정은 그 권한이 없는 사람에게 **늘 기본값**이다(VIP 전용 개인 시간 같은 것 — 권한을 잃으면 저절로 돌아간다).
 * - 저장소가 아직 안 읽혔으면(core 의 첫 틱 전) 기본값이 나온다.
 */
object PlayerSettings {

    sealed interface Kind

    /** 켜고 끄기. */
    data class Toggle(val default: Boolean) : Kind

    /** 하나 고르기. [options] 는 (id, 보이는 이름) — 디스크에는 id 를 적는다. */
    data class Choice(val options: List<Pair<String, String>>, val default: String) : Kind

    class Setting(
        /** `<플러그인>.<이름>` — 예: `enchants.area-mining`. 바꾸면 저장된 값과 끊긴다. */
        val key: String,
        /** 화면에서 묶는 줄 제목 — 보통 플러그인의 한글 이름. [unregisterAll] 도 이것으로. */
        val owner: String,
        val label: String,
        val icon: Material,
        val description: List<String> = emptyList(),
        val kind: Kind,
        /** 이 권한이 있어야 바꿀 수 있고, 없으면 늘 기본값. */
        val permission: String? = null,
    ) {
        fun defaultText(): String = when (kind) {
            is Toggle -> kind.default.toString()
            is Choice -> kind.default
        }
    }

    /** 값을 읽고 쓰는 곳 — 평소엔 core 의 PlayerStore, 테스트는 메모리. */
    interface Store {
        fun read(playerId: UUID, key: String): Any?
        fun write(playerId: UUID, key: String, value: Any?)
    }

    const val NAMESPACE = "settings"

    /** 공용 — 희귀 드랍·당첨 공지 받기(core 가 올린다. 공지하는 플러그인이 여럿이라 어느 한 플러그인의 것이 아니다). */
    const val RARE_ANNOUNCE = "core.rare-announce"

    /** 메뉴 버튼 클릭 소리(core `Menu.handleClick`, 2026-10-08). 기본 켬. */
    const val UI_CLICK = "core.ui-click"

    private val settings = LinkedHashMap<String, Setting>()
    private val listeners = ConcurrentHashMap<String, (Player, String) -> Unit>()

    @Volatile
    internal var store: Store = CoreStore

    private object CoreStore : Store {
        private fun players() = runCatching { CorePlugin.get().players }.getOrNull()

        override fun read(playerId: UUID, key: String): Any? = players()?.get(playerId, NAMESPACE, key)

        override fun write(playerId: UUID, key: String, value: Any?) {
            players()?.set(playerId, NAMESPACE, key, value)
        }
    }

    /** 같은 열쇠는 교체한다(리로드). */
    @JvmStatic
    fun register(setting: Setting) {
        synchronized(settings) { settings[setting.key] = setting }
    }

    @JvmStatic
    fun unregisterAll(owner: String) {
        synchronized(settings) { settings.values.removeIf { it.owner == owner } }
    }

    /** 올린 순서대로. */
    @JvmStatic
    fun all(): List<Setting> = synchronized(settings) { settings.values.toList() }

    @JvmStatic
    fun get(key: String): Setting? = synchronized(settings) { settings[key] }

    // --- 읽기 -----------------------------------------------------------------------------

    /** 켜져 있는가. 정하지 않았으면 기본값, 정의가 없으면 [fallback]. */
    @JvmStatic
    fun enabled(playerId: UUID, key: String, fallback: Boolean = true): Boolean {
        val setting = get(key)
        val default = (setting?.kind as? Toggle)?.default ?: fallback
        return when (val raw = store.read(playerId, storeKey(key))) {
            is Boolean -> raw
            is String -> raw.toBooleanStrictOrNull() ?: default
            else -> default
        }
    }

    /** 권한까지 본다 — 권한이 필요한데 없으면 기본값. */
    @JvmStatic
    fun enabled(player: Player, key: String, fallback: Boolean = true): Boolean {
        val setting = get(key)
        if (setting != null && !allowed(player, setting)) return (setting.kind as? Toggle)?.default ?: fallback
        return enabled(player.uniqueId, key, fallback)
    }

    /** 고른 것의 id. 정하지 않았거나 목록에 없는 값이면 기본값, 정의가 없으면 [fallback]. */
    @JvmStatic
    fun choice(playerId: UUID, key: String, fallback: String = ""): String {
        val setting = get(key)
        val kind = setting?.kind as? Choice
        val raw = store.read(playerId, storeKey(key))?.toString()
        if (kind == null) return raw ?: fallback
        return raw?.takeIf { value -> kind.options.any { it.first == value } } ?: kind.default
    }

    @JvmStatic
    fun choice(player: Player, key: String, fallback: String = ""): String {
        val setting = get(key)
        if (setting != null && !allowed(player, setting)) return (setting.kind as? Choice)?.default ?: fallback
        return choice(player.uniqueId, key, fallback)
    }

    @JvmStatic
    fun allowed(player: Player, setting: Setting): Boolean = setting.permission?.let(player::hasPermission) ?: true

    // --- 쓰기 -----------------------------------------------------------------------------

    /**
     * 값을 정한다. 켜고 끄기는 Boolean, 고르기는 목록에 있는 id. 맞지 않으면 false(아무것도 안 바뀐다). 정의가 없는 열쇠도 false.
     * 정하면 [listen] 한 플러그인들에 알린다.
     */
    @JvmStatic
    fun set(player: Player, key: String, value: Any): Boolean {
        val setting = get(key) ?: return false
        if (!allowed(player, setting)) return false
        if (!write(player.uniqueId, setting, value)) return false
        for (listener in listeners.values) runCatching { listener(player, key) }
        return true
    }

    /** UUID 로 바로 — 알림이 없다(접속 중이 아닐 수 있다). */
    @JvmStatic
    fun set(playerId: UUID, key: String, value: Any): Boolean {
        val setting = get(key) ?: return false
        return write(playerId, setting, value)
    }

    private fun write(playerId: UUID, setting: Setting, value: Any): Boolean {
        val stored: Any = when (val kind = setting.kind) {
            is Toggle -> value as? Boolean ?: (value as? String)?.toBooleanStrictOrNull() ?: return false
            is Choice -> value.toString().takeIf { v -> kind.options.any { it.first == v } } ?: return false
        }
        // 기본값과 같으면 지운다 — 파일이 정하지 않은 값으로 채워지지 않게, 기본값을 바꾸면 따라가게.
        store.write(playerId, storeKey(setting.key), if (stored.toString() == setting.defaultText()) null else stored)
        return true
    }

    /** 값이 바뀔 때 — 개인 시간처럼 바로 반영해야 하는 것. 같은 owner 는 교체. */
    @JvmStatic
    fun listen(owner: String, callback: (Player, String) -> Unit) {
        listeners[owner] = callback
    }

    @JvmStatic
    fun unlisten(owner: String) {
        listeners.remove(owner)
    }

    /** 저장소의 열쇠 — 점을 쌍점으로(지뢰 1). */
    internal fun storeKey(key: String): String = key.replace('.', ':')
}
