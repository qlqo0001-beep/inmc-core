package kr.inmc.core.integration

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.logging.Level
import java.util.logging.Logger

/**
 * **모드 창구** (2026-10-15). 서버 하나를 **모드**(월드 묶음 — 반야생·건축·마인팜 …)로 나눠 모드마다 다른 서버처럼 돌린다.
 * 모드를 정하는 플러그인(inmc-worlds)이 [Provider] 로 꽂히고, 사람별 자료를 가진 플러그인은 누가 모드를 정하는지 모른 채 [scope] 로 열쇠를 나눈다.
 * [Mail] 과 같은 "공급처 하나" + [PlayerSettings] 같은 등록부([Feature]).
 *
 * ## 쓰는 쪽의 한 줄
 * 사람별 자료를 가진 플러그인은 기능을 [register] 로 올리고, 저장 열쇠에 [scope] 를 붙인다([key]).
 * - 공급처가 없거나 관리자가 그 기능을 "같이" 로 두면 [scope] 는 `""` — **열쇠·파일이 오늘과 똑같다.** 그래서 플러그인을 하나씩 바꿔도 안전하다.
 * - 아니면 모드 id(`[a-z0-9_-]`, [MODE_ID]) — 열쇠 뒤에 `@<모드>` 를 붙인다(PlayerStore 는 점만 금지라 `inmc_craft@build` 가 된다).
 * - 캐시는 `(uuid, scope)` 로 쥔다 — 관리자가 토글을 켠 채 바꿔도 다음 조회가 새 칸을 본다(재시작 없이). 열린 화면·건 효과는
 *   [kr.inmc.core.event.RealmChangeEvent]·[kr.inmc.core.event.RealmFeatureChangeEvent] 에서 다시 그린다.
 *
 * **스레드**: [scope]·[of]·[ofWorld]·[isShared]·[sameChat]·[sameTab] 은 아무 스레드에서나(공급처가 메모리 맵만 본다).
 * [spawn]·[setSpawn]·[canTravel]·[travel] 은 메인 스레드.
 * core 의 클래스는 모든 플러그인이 공유하므로(`join-classpath: true`) 이 객체가 곧 서버 전체에 하나다.
 */
object Realms {

    /** 모드 id 규칙 — 파일 이름·PlayerStore 네임스페이스·SQL 열에 그대로 들어간다. */
    @JvmField
    val MODE_ID = Regex("[a-z0-9_-]{1,32}")

    /** 열쇠와 모드를 잇는 글자. */
    const val SEPARATOR = '@'

    /**
     * 모드마다 따로 둘 수 있는 기능 하나 — 관리자가 inmc-worlds 화면에서 "모드마다 따로 / 같이" 를 고른다.
     *
     * @param key `<플러그인>.<이름>` — 예: `customitems.equipment`. 바꾸면 관리자가 고른 값과 끊긴다.
     * @param owner 화면에서 묶는 줄 제목 — 보통 플러그인의 한글 이름. [unregisterAll] 도 이것으로.
     * @param defaultShared 관리자가 고르기 전의 값. 기본은 따로(사용자 결정 2026-10-15 — 칭호·닉네임만 기본 같이).
     */
    class Feature(
        val key: String,
        val owner: String,
        val label: String,
        val icon: Material,
        val description: List<String> = emptyList(),
        val defaultShared: Boolean = false,
    )

    interface Provider {
        /** 이 사람의 지금 모드 — 빌린 월드(던전)에 있으면 들어온 모드, 오프라인이면 마지막 모드, 처음 보는 사람은 기본 모드. 아무 스레드. */
        fun of(playerId: UUID): String

        /** 이 월드의 모드. 빌린 월드면 null. 아무 스레드. */
        fun ofWorld(world: String): String?

        /** 보여 줄 이름(MiniMessage). */
        fun name(mode: String): String

        /** 모드 id 전부(보여 줄 순서). */
        fun all(): List<String>

        /** 관리자가 이 기능을 "같이" 로 두었는가(고르지 않았으면 [Feature.defaultShared]). 아무 스레드. */
        fun isShared(key: String): Boolean

        fun spawn(mode: String): Location?

        fun setSpawn(mode: String, location: Location): Boolean

        /** 둘이 같은 채팅 묶음인가. */
        fun sameChat(a: UUID, b: UUID): Boolean

        /** 둘이 같은 탭 묶음인가. */
        fun sameTab(a: UUID, b: UUID): Boolean

        /** [player] 가 [to] 로 갈 수 있는가 — 다른 모드면 false(관리자·빌린 월드 규칙은 공급처가). */
        fun canTravel(player: Player, to: Location): Boolean

        /** 모드를 넘을 수 있는 이동(포탈류·`/모드`) — 넘으면 공급처가 몸을 바꾼다. */
        fun travel(player: Player, to: Location): CompletableFuture<Boolean>
    }

    @Volatile
    private var provider: Provider? = null

    private val features = LinkedHashMap<String, Feature>()

    /** 꽂는다. 이미 있으면 바꾼다(리로드). */
    @JvmStatic
    fun register(provider: Provider) {
        this.provider = provider
    }

    /** 꽂은 그 공급처일 때만 뺀다 — 새로 켜진 공급처를 옛것이 내려가면서 지우지 않게. */
    @JvmStatic
    fun unregister(provider: Provider) {
        if (this.provider === provider) this.provider = null
    }

    @JvmStatic
    val available: Boolean get() = provider != null

    // --- 기능 등록부 ---------------------------------------------------------------------------

    /** 같은 열쇠는 교체한다(리로드). 공급처가 없어도 올린다 — 공급처가 나중에 켜져도 목록이 있다. */
    @JvmStatic
    fun register(feature: Feature) {
        synchronized(features) { features[feature.key] = feature }
    }

    @JvmStatic
    fun unregisterAll(owner: String) {
        synchronized(features) { features.values.removeIf { it.owner == owner } }
    }

    /** 기능 하나만 뺀다 — 한 플러그인 안의 여러 기능이 같은 묶음 이름을 쓸 때(essentials 처럼 기능마다 켜고 끈다). */
    @JvmStatic
    fun unregisterFeature(key: String) {
        synchronized(features) { features.remove(key) }
    }

    /** 올린 순서대로. */
    @JvmStatic
    fun features(): List<Feature> = synchronized(features) { features.values.toList() }

    @JvmStatic
    fun feature(key: String): Feature? = synchronized(features) { features[key] }

    // --- 쓰는 쪽 -------------------------------------------------------------------------------

    /**
     * 이 기능의 이 사람 몫을 가르는 글자 — 공급처가 없거나 "같이" 면 `""`, 아니면 모드 id. 아무 스레드.
     * 공급처가 던지면 **그대로 던진다** — `""` 로 물러나면 그 사이의 쓰기가 같이 쓰는 칸으로 가 모드 사이로 물건이 샌다.
     */
    @JvmStatic
    fun scope(key: String, playerId: UUID): String {
        val current = provider ?: return ""
        return if (current.isShared(key)) "" else current.of(playerId)
    }

    @JvmStatic
    fun scope(key: String, player: Player): String = scope(key, player.uniqueId)

    /** [base] 에 [scope] 를 붙인 열쇠 — `""` 면 [base] 그대로(오늘의 열쇠). */
    @JvmStatic
    fun key(base: String, scope: String): String = if (scope.isEmpty()) base else base + SEPARATOR + scope

    /** 관리자가 "같이" 로 두었는가. 공급처가 없으면 true(모드가 없으면 모두 같이). */
    @JvmStatic
    fun isShared(key: String): Boolean = provider?.let { guard("isShared") { it.isShared(key) } } ?: true

    /** 이 사람의 모드. 공급처가 없으면 null. */
    @JvmStatic
    fun of(playerId: UUID): String? = provider?.let { guard("of") { it.of(playerId) } }

    @JvmStatic
    fun of(player: Player): String? = of(player.uniqueId)

    /** 이 월드의 모드. 공급처가 없거나 빌린 월드면 null. */
    @JvmStatic
    fun ofWorld(world: World): String? = provider?.let { guard("ofWorld") { it.ofWorld(world.name) } }

    /** 보여 줄 이름(MiniMessage). 공급처가 없으면 id 그대로. */
    @JvmStatic
    fun name(mode: String): String = provider?.let { guard("name") { it.name(mode) } } ?: mode

    @JvmStatic
    fun all(): List<String> = provider?.let { guard("all") { it.all() } }.orEmpty()

    @JvmStatic
    fun spawn(mode: String): Location? = provider?.let { guard("spawn") { it.spawn(mode) } }

    /** 공급처가 없거나 받지 않으면 false — 부른 쪽이 제 자리(예: essentials `spawn.yml`)에 둔다. */
    @JvmStatic
    fun setSpawn(mode: String, location: Location): Boolean =
        provider?.let { guard("setSpawn") { it.setSpawn(mode, location) } } ?: false

    /** 같은 채팅 묶음인가. 공급처가 없으면 true. */
    @JvmStatic
    fun sameChat(a: Player, b: Player): Boolean =
        a.uniqueId == b.uniqueId || provider?.let { guard("sameChat") { it.sameChat(a.uniqueId, b.uniqueId) } } ?: true

    /** 같은 탭 묶음인가. 공급처가 없으면 true. */
    @JvmStatic
    fun sameTab(a: Player, b: Player): Boolean =
        a.uniqueId == b.uniqueId || provider?.let { guard("sameTab") { it.sameTab(a.uniqueId, b.uniqueId) } } ?: true

    /** [player] 가 [to] 로 갈 수 있는가. 공급처가 없으면 true. */
    @JvmStatic
    fun canTravel(player: Player, to: Location): Boolean =
        provider?.let { guard("canTravel") { it.canTravel(player, to) } } ?: true

    /** 모드를 넘을 수 있는 이동(포탈류). 공급처가 없으면 그냥 `teleportAsync`. */
    @JvmStatic
    fun travel(player: Player, to: Location): CompletableFuture<Boolean> {
        val current = provider ?: return player.teleportAsync(to)
        return guard("travel") { current.travel(player, to) } ?: CompletableFuture.completedFuture(false)
    }

    /** 공급처가 던져도 부른 쪽은 오늘처럼 돈다 — 로그만 남긴다. */
    private inline fun <T> guard(what: String, block: () -> T): T? = try {
        block()
    } catch (t: Throwable) {
        Logger.getLogger("inmc-core").log(Level.WARNING, "모드 공급처 실패 ($what)", t)
        null
    }
}
