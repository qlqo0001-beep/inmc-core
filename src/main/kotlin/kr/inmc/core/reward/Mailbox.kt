package kr.inmc.core.reward

import kr.inmc.core.integration.Mail
import kr.inmc.core.util.AtomicFiles
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Rewards waiting for a player who was not around to receive them.
 *
 * Season rewards are the reason this exists: a leaderboard closes on a schedule, and the person
 * who came first is frequently asleep. Rolling the reward at award time and parking the concrete
 * result here - rather than parking the *bundle* and re-rolling on claim - means two players who
 * placed the same get the same thing, and nobody can farm a re-roll by claiming at a better
 * moment.
 */
class Mailbox(private val ng: RewardHost) {

    /*
     * 우편 공급처(core [Mail], inmc-essentials)가 꽂혀 있으면 이 우편함은 **그리로 넘기는 얇은 층**이다(2026-10-10) — 사람은 우편함
     * 하나만 연다. 넘길 때 origin 은 호스트 플러그인 이름이라 숫자야구 화면은 제 몫만 센다.
     * - 꽂히는 순간([Mail.listen]) · 파일을 다 읽은 순간([load]) · 저장 차례([flush]) 에 제 메모리에 남은 것을 넘기고 비운다.
     *   [load] 는 비동기라 꽂힘보다 늦게 끝날 수 있다 — 그때 읽힌 것도 읽힌 직후 넘어간다. 넘긴 것은 지우므로 두 번 가지 않는다.
     * - 공급처가 받지 않으면(false) 여기 남는다 — 세기·받기·버리기는 늘 여기 + 공급처를 합친다(접속 알림만 예외 — [notifyOnJoin]). 다음 차례에 다시 넘긴다.
     * - 넘긴 것은 메모리에서 바로 빠지지만 **파일에서는 다음 저장 차례에** 빠진다 — 그 사이 공급처가 제 DB 에 쓴다. 그 사이 죽으면 다음
     *   부팅에 다시 넘기고, 공급처는 [Mail.Parcel.key](= 이 우편함의 id·시각)로 걸러 한 통으로 둔다(잃기보다 다시 넘기기).
     *   다 읽기 전에는 쓰지 않는다 — 읽기 전 상태로 파일을 덮지 않게.
     * - 명령어는 넘길 때 호스트의 별칭(`{플레이어}`·`{플레이어네임}` …)을 `{player}` 로 바꿔 넘긴다 — 공급처는 `{player}` 만 안다([Mail]).
     * - 공급처가 없을 때 [discard] 하면 그 사람을 적어 두었다가(파일에도) 다시 꽂히는 순간 공급처 쪽 몫을 버린다 — 넘긴 것이 초기화를 피하지 않게.
     */
    private val mailKey = "core-mailbox#" + UUID.randomUUID()

    /** 시험용 — 서버 없이 origin 을 정한다. 평소에는 호스트 플러그인 이름. */
    internal var originOverride: String? = null

    private val origin: String get() = originOverride ?: ng.plugin.name

    @Volatile
    private var loaded = false

    init {
        Mail.listen(mailKey) { onMailChanged() }
    }

    /**
     * 꽂힘·빠짐 알림. 메인에서만 넘긴다 — 다른 스레드에서 꽂혔으면 메인으로 넘긴다. 아니면 메인의 [claimLocal] 과 같은 통을 동시에
     * 집어 한쪽은 주고 한쪽은 공급처로 보내 두 번 받게 된다. 서버가 없으면(시험) 그 자리에서.
     */
    private fun onMailChanged() {
        val server: org.bukkit.Server? = Bukkit.getServer()
        if (server != null && !server.isPrimaryThread) {
            if (ng.plugin.isEnabled) Bukkit.getGlobalRegionScheduler().run(ng.plugin) { onMailChanged() }
            return
        }
        if (!Mail.available) return
        applyPendingDiscards()
        forwardToMail()
    }

    class Entry(
        val id: String = UUID.randomUUID().toString().substring(0, 8),
        val at: Long = System.currentTimeMillis(),
        /** Human readable origin, e.g. "숫자야구 3자리 · 시즌 4 · 1위". */
        val source: String = "",
        val items: MutableList<ByteArray> = mutableListOf(),
        var money: Double = 0.0,
        /** 이름 있는 화폐의 돈(id → 금액, 2026-10-08). 준 것은 지워 나간다. */
        val moneyBy: MutableMap<String, Double> = LinkedHashMap(),
        val commands: MutableList<String> = mutableListOf(),
    ) {
        fun isEmpty(): Boolean = items.isEmpty() && money <= 0.0 && moneyBy.isEmpty() && commands.isEmpty()

        fun describe(): String {
            val parts = mutableListOf<String>()
            if (items.isNotEmpty()) parts.add("아이템 " + items.size + "종")
            if (money > 0.0) parts.add(kr.inmc.core.util.Numbers.money(money) + "원")
            for ((currency, amount) in moneyBy) {
                parts.add(kr.inmc.core.economy.Currencies.get(currency)?.format(Math.round(amount)) ?: (kr.inmc.core.util.Numbers.money(amount) + " " + currency))
            }
            if (commands.isNotEmpty()) parts.add("특전 " + commands.size + "개")
            return if (parts.isEmpty()) "빈 보상" else parts.joinToString(" · ")
        }
    }

    private val boxes = ConcurrentHashMap<UUID, MutableList<Entry>>()

    @Volatile
    private var dirty = false

    fun countOf(playerId: UUID): Int = localCount(playerId) + (if (Mail.available) Mail.count(playerId, origin) else 0)

    /** 공급처로 넘기지 못해 여기 남아 있는 것만. 공급처가 없으면 전부가 여기다. */
    private fun localCount(playerId: UUID): Int = boxes[playerId]?.size ?: 0

    fun entriesOf(playerId: UUID): List<Entry> {
        val local = boxes[playerId]?.let { list -> synchronized(list) { list.toList() } }.orEmpty()
        if (!Mail.available) return local
        return local + Mail.entries(playerId, origin).map { (id, parcel) -> entryOf(id, parcel) }
    }

    fun isEmpty(playerId: UUID): Boolean = countOf(playerId) == 0

    /**
     * Discards every entry waiting for this player. Returns how many were dropped.
     *
     * Admin reset path — unclaimed achievement rewards vanish with the records.
     * Expired entries ([purgeExpired]) drop silently; this one is always deliberate,
     * so the caller reports the count.
     *
     * 공급처가 없으면 그쪽 몫은 세지 못한다 — 그 사람을 적어 두고(파일에도) 다시 꽂히는 순간 버린다([applyPendingDiscards]).
     */
    fun discard(playerId: UUID): Int {
        val removed = boxes.remove(playerId)?.size ?: 0
        if (removed > 0) dirty = true
        if (Mail.available) {
            if (pendingDiscards.remove(playerId)) dirty = true
            return removed + Mail.discard(playerId, origin)
        }
        if (pendingDiscards.add(playerId)) dirty = true
        return removed
    }

    /** 공급처가 없을 때 [discard] 한 사람들 — 꽂히면 공급처 쪽 몫을 버린다. 공급처가 없는 동안 들어온 보상은 여기 있어 휩쓸리지 않는다. */
    private val pendingDiscards: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    /** 꽂힌 뒤(메인). 넘기기([forwardToMail])보다 **먼저** — 공급처가 없는 동안 여기 쌓인 새 보상까지 버리지 않게. */
    private fun applyPendingDiscards() {
        if (pendingDiscards.isEmpty()) return
        val from = origin
        for (playerId in pendingDiscards.toList()) {
            Mail.discard(playerId, from)
            pendingDiscards.remove(playerId)
        }
        dirty = true
    }

    fun deposit(playerId: UUID, entry: Entry) {
        if (entry.isEmpty()) return
        // 공급처가 있으면 그리로 — 한도로 지우기는 없다(공급처는 보상을 지우지 않는다). 거절하면 아래로 떨어져 여기 남는다.
        if (Mail.available && Mail.deposit(playerId, parcelFor(entry))) return
        val list = boxes.computeIfAbsent(playerId) { java.util.Collections.synchronizedList(mutableListOf()) }
        synchronized(list) {
            list.add(entry)
            // Oldest first out, so the reward someone just won is never the one that is dropped.
            // 공급처가 있는데 거절해 여기 남는 것은 지우지 않는다 — 한도는 공급처가 없을 때만(공급처 쪽은 보상을 지우지 않는다).
            if (!Mail.available) while (list.size > ng.rewardSettings.mailboxLimit) list.removeAt(0)
        }
        dirty = true
    }

    /**
     * Hands everything over. Items that do not fit stay in the box, so claiming with three free
     * slots takes three stacks and leaves the rest rather than voiding them.
     *
     * Returns the number of entries fully claimed.
     */
    fun claimAll(player: Player): ClaimResult {
        val local = claimLocal(player)
        if (!Mail.available) return local
        val remote = Mail.claim(player, origin) ?: return local
        return ClaimResult(local.claimed + remote.claimed, local.leftBehind + remote.leftBehind, local.partial || remote.partial)
    }

    private fun claimLocal(player: Player): ClaimResult {
        val list = boxes[player.uniqueId] ?: return ClaimResult(0, 0, false)
        var claimed = 0
        var leftBehind = 0
        var anyPartial = false

        val snapshot = synchronized(list) { list.toList() }
        for (entry in snapshot) {
            val remaining = mutableListOf<ByteArray>()
            for (bytes in entry.items) {
                val stack = runCatching { ItemStack.deserializeBytes(bytes) }.getOrNull()
                if (stack == null) {
                    // Keeping it would make the entry unclaimable forever, so it goes - but it
                    // goes loudly, because otherwise a "받았습니다" with nothing in hand is
                    // impossible for an owner to investigate.
                    ng.plugin.logger.warning(
                        "우편함 아이템을 복원하지 못해 폐기했습니다: " + player.name +
                            " (" + entry.source + ")"
                    )
                    continue
                }
                val overflow = player.inventory.addItem(stack)
                if (overflow.isEmpty()) continue
                overflow.values.forEach { left ->
                    remaining.add(runCatching { left.serializeAsBytes() }.getOrNull() ?: return@forEach)
                }
            }

            if (remaining.isEmpty()) {
                // The items are already in hand, so the only thing that can still fail is the
                // money. If it does - Vault removed, provider rejecting the transaction - the
                // entry has to survive holding just the money, or the reward is destroyed with
                // a cheerful "받았습니다" and no trace of what was lost.
                var paid = true
                if (entry.money > 0.0) {
                    if (ng.economy.isEnabled && ng.economy.deposit(player, entry.money)) entry.money = 0.0 else paid = false
                }
                if (paid) {
                    // 이름 있는 화폐(2026-10-08) — 준 것은 지워 가며, 하나라도 막히면 나머지를 들고 남는다.
                    val rest = entry.moneyBy.entries.iterator()
                    while (rest.hasNext()) {
                        val (currency, amount) = rest.next()
                        if (amount <= 0.0 || (ng.economy.isEnabled && ng.economy.deposit(player, amount, currency))) rest.remove()
                        else { paid = false; break }
                    }
                }
                if (!paid) {
                    ng.plugin.logger.warning(
                        "우편함 금액을 지급하지 못해 보관합니다: " + player.name + " " + entry.describe()
                    )
                    entry.items.clear()
                    leftBehind++
                    // Deliberately not `anyPartial`: that flag drives the "인벤토리가 부족해서"
                    // message, and blaming the player's inventory for an economy failure would
                    // send them off to clear slots that were never the problem.
                    ng.tell(player, "mailbox-money-held")
                    continue
                }
                entry.commands.forEach { ng.rewards.runCommand(it, player) }
                synchronized(list) { list.remove(entry) }
                claimed++
            } else {
                // Partially taken: keep only what did not fit, and do not pay the money twice.
                entry.items.clear()
                entry.items.addAll(remaining)
                leftBehind++
                anyPartial = true
            }
        }

        if (synchronized(list) { list.isEmpty() }) boxes.remove(player.uniqueId)
        dirty = true
        return ClaimResult(claimed, leftBehind, anyPartial)
    }

    data class ClaimResult(val claimed: Int, val leftBehind: Int, val partial: Boolean)

    // --- persistence -----------------------------------------------------------

    fun load(then: () -> Unit = {}) {
        ng.io.async({
            val file = ng.io.file("data", "mailbox.yml")
            if (file.exists()) ng.io.load(file) else YamlConfiguration()
        }) { config ->
            val read = HashMap<UUID, MutableList<Entry>>()
            config.getConfigurationSection("players")?.let { players ->
                for (raw in players.getKeys(false)) {
                    val id = runCatching { UUID.fromString(raw) }.getOrNull() ?: continue
                    val section = players.getConfigurationSection(raw) ?: continue
                    val list = java.util.Collections.synchronizedList(mutableListOf<Entry>())
                    for (key in section.getKeys(false)) {
                        val entrySection = section.getConfigurationSection(key) ?: continue
                        val entry = Entry(
                            id = entrySection.getString("id") ?: key,
                            at = entrySection.getLong("at", 0L),
                            source = entrySection.getString("source").orEmpty(),
                            money = entrySection.getDouble("money", 0.0),
                            moneyBy = entrySection.getConfigurationSection("money-by")
                                ?.let { s -> s.getKeys(false).associateWithTo(LinkedHashMap()) { s.getDouble(it, 0.0) } } ?: LinkedHashMap(),
                            commands = entrySection.getStringList("commands").toMutableList(),
                        )
                        entrySection.getStringList("items").forEach { encoded ->
                            runCatching { Base64.getDecoder().decode(encoded) }.getOrNull()
                                ?.let { entry.items.add(it) }
                        }
                        if (!entry.isEmpty()) list.add(entry)
                    }
                    if (list.isNotEmpty()) read[id] = list
                }
            }
            val discards = config.getStringList("pending-discard").mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
            accept(read, discards)
            then()
        }
    }

    /**
     * 파일을 다 읽은 뒤(메인). 읽은 것을 앞에, **읽기 전에 들어와 아직 여기 있는 것을 뒤에** 붙인다(바꿔 끼우면 그것이 사라진다 — 파일에는
     * 없던 것이다, 다 읽기 전에는 쓰지 않으므로). 공급처가 이미 있으면 곧바로 넘긴다 — 꽂힘이 읽기보다 먼저 끝났어도 읽힌 것이 여기 갇히지
     * 않는다. 꽂힘이 먼저였다면 그때 넘긴 것은 메모리에서 이미 지워졌고 파일에는 없던 것이라 두 번 가지 않는다.
     */
    internal fun accept(read: Map<UUID, List<Entry>>, discards: Collection<UUID> = emptyList()) {
        val early = HashMap(boxes)
        boxes.clear()
        for ((id, list) in read) {
            if (list.isNotEmpty()) boxes[id] = java.util.Collections.synchronizedList(list.toMutableList())
        }
        var merged = false
        for ((id, list) in early) {
            val pending = synchronized(list) { list.toList() }
            if (pending.isEmpty()) continue
            val target = boxes.computeIfAbsent(id) { java.util.Collections.synchronizedList(mutableListOf()) }
            synchronized(target) { target.addAll(pending) }
            merged = true
        }
        pendingDiscards.addAll(discards)
        dirty = merged
        loaded = true
        if (Mail.available) {
            applyPendingDiscards()
            forwardToMail()
        }
    }

    /**
     * 메모리에 남은 것을 공급처로 넘긴다. 받은 것만 메모리에서 지운다. 파일에서는 **다음 저장 차례에** 빠진다(dirty) — 그 사이 공급처가
     * 제 DB 에 쓴다. 그 사이 죽으면 다음 부팅에 다시 넘기고 공급처가 [Mail.Parcel.key] 로 거른다(곧바로 비워 쓰면 공급처가 쓰기 전에 죽을 때
     * 두 곳 다에서 사라진다). 내려간 플러그인의 알림이 남아 있어도 던지지 않는다.
     */
    internal fun forwardToMail(): Int {
        if (boxes.isEmpty()) return 0
        val from = origin
        var moved = 0
        for ((playerId, list) in boxes) {
            for (entry in synchronized(list) { list.toList() }) {
                if (Mail.deposit(playerId, parcelFor(entry, from))) {
                    synchronized(list) { list.remove(entry) }
                    moved++
                }
            }
            if (synchronized(list) { list.isEmpty() }) boxes.remove(playerId, list)
        }
        if (moved > 0) {
            dirty = true
            runCatching { ng.plugin.logger.info("우편함의 " + moved + "통을 우편 공급처로 옮겼습니다") }
        }
        return moved
    }

    /** 이 우편함의 한 통 → 공급처의 우편. 명령어의 호스트 별칭은 `{player}` 로(공급처는 그것만 안다 — [Mail]). */
    private fun parcelFor(entry: Entry, from: String = origin): Mail.Parcel =
        parcelOf(entry, from, if (entry.commands.isEmpty()) null else ng.placeholders("player" to Mail.PLAYER_TOKEN))

    fun flush() {
        // 다 읽기 전에 쓰면 읽기 전 상태(빈 우편함)로 파일을 덮는다.
        if (!loaded) return
        if (dirty) {
            dirty = false
            val text = serialize()
            ng.io.asyncRun { write(text) }
        }
        // 쓴 **뒤에** 넘긴다 — 지금 넘긴 것은 다음 차례에 파일에서 빠진다(forwardToMail).
        if (Mail.available && boxes.isNotEmpty()) forwardToMail()
    }

    /**
     * 끌 때. 우편 알림도 뗀다(내려간 플러그인의 우편함이 남아 불리지 않게). 쓰기는 **io 워커 줄의 맨 뒤에 넣고 기다린다** — 메인에서 바로
     * 쓰면 앞서 줄 선 옛 스냅샷([flush])이 뒤에 쓰여 마지막 상태를 덮는다. 워커가 이미 내려갔으면 여기서 쓴다.
     */
    fun flushBlocking() {
        Mail.unlisten(mailKey)
        if (!loaded) return
        val text = serialize()
        dirty = false
        val done = java.util.concurrent.CountDownLatch(1)
        var failure: Throwable? = null
        val queued = runCatching {
            ng.io.asyncRun {
                try {
                    write(text)
                } catch (t: Throwable) {
                    failure = t
                } finally {
                    done.countDown()
                }
            }
        }.isSuccess
        if (!queued) {
            runCatching { write(text) }.onFailure { failure = it }
        } else if (!done.await(BLOCKING_WAIT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)) {
            // 줄에 든 채로 남는다 — 워커를 닫을 때(ConfigService.shutdown) 옛 스냅샷들 다음에 쓰인다. 여기서 또 쓰면 그 옛것이 뒤에 덮는다.
            ng.plugin.logger.warning("우편함 저장이 " + BLOCKING_WAIT_SECONDS + "초 안에 끝나지 않았습니다 — io 워커를 닫을 때 마저 씁니다")
            return
        }
        failure?.let { ng.plugin.logger.severe("우편함 저장 실패: " + it.message) }
    }

    /** 시험용 — 알림을 뗀다. */
    internal fun close() {
        Mail.unlisten(mailKey)
    }

    private fun write(text: String) {
        // 통째로 바꿔치기 — 쓰다 죽으면 잘린 파일이 남아 다음 부팅에 우편함이 통째로 사라진다(지뢰 10).
        AtomicFiles.write(ng.io.file("data", "mailbox.yml"), text)
    }

    private fun serialize(): String {
        val config = YamlConfiguration()
        for ((id, list) in boxes) {
            val snapshot = synchronized(list) { list.toList() }
            snapshot.forEachIndexed { index, entry ->
                val path = "players." + id + "." + index
                config.set(path + ".id", entry.id)
                config.set(path + ".at", entry.at)
                config.set(path + ".source", entry.source)
                config.set(path + ".money", entry.money)
                if (entry.moneyBy.isNotEmpty()) config.set(path + ".money-by", entry.moneyBy.toMap())
                config.set(path + ".commands", entry.commands)
                config.set(path + ".items", entry.items.map { Base64.getEncoder().encodeToString(it) })
            }
        }
        if (pendingDiscards.isNotEmpty()) config.set("pending-discard", pendingDiscards.map { it.toString() })
        return config.saveToString()
    }

    /**
     * Ticker hook: nudges anyone who logged in with unclaimed rewards.
     *
     * 우편 공급처(inmc-essentials)가 꽂혀 있으면 넘긴 몫은 그쪽 접속 알림(`/우편`)이 이미 세므로 **여기 남은 것만** 센다 — 전에는
     * 제 몫 + 공급처 몫을 합쳐 세어 같은 소포를 두 번 알렸다(2026-10-14). 남은 것은 공급처가 거절해 아직 못 넘긴 것뿐이라 평소에는 0 이다.
     * 공급처가 없으면 전부가 여기라 전과 같다 — [countOf] 가 아니라 [localCount] 하나로 두 경우를 다 덮는다.
     */
    fun notifyOnJoin(player: Player) {
        val count = localCount(player.uniqueId)
        if (count <= 0) return
        ng.tell(player, "mailbox-waiting", ng.placeholders("count" to count.toString()))
    }

    fun onlineHolders(): List<Player> =
        boxes.keys.mapNotNull { Bukkit.getPlayer(it) }

    /**
     * Drops entries nobody has come back for.
     *
     * Without this the file only ever grows: a player who stops playing keeps their unclaimed
     * season rewards forever, and every season adds more. Returns how many were discarded so the
     * caller can log a sweep that actually removed something.
     */
    fun purgeExpired(now: Long): Int {
        val maxAge = ng.rewardSettings.mailboxExpireSeconds
        if (maxAge <= 0L) return 0
        val cutoff = now - maxAge * 1000L

        var removed = 0
        for ((playerId, list) in boxes) {
            synchronized(list) {
                val before = list.size
                list.removeIf { it.at in 1..cutoff }
                removed += before - list.size
            }
            if (synchronized(list) { list.isEmpty() }) boxes.remove(playerId)
        }
        if (removed > 0) dirty = true
        return removed
    }
    internal companion object {
        /** 끌 때 io 워커의 쓰기를 기다리는 한도. */
        private const val BLOCKING_WAIT_SECONDS = 10L

        /**
         * 이 우편함의 한 통 → 공급처의 우편. 보상이라 만료가 없다(공급처는 보상을 지우지 않는다). [Mail.Parcel.key] 는 id·시각 —
         * 다시 넘겨도 공급처가 한 통으로 둔다. [player] 가 있으면 명령어에 걸어 호스트의 별칭을 `{player}` 로 바꾼다.
         */
        fun parcelOf(entry: Entry, origin: String, player: kr.inmc.core.util.Placeholders? = null): Mail.Parcel = Mail.Parcel(
            kind = Mail.Kind.REWARD,
            origin = origin,
            source = entry.source,
            items = entry.items.toList(),
            money = entry.money,
            moneyBy = LinkedHashMap(entry.moneyBy),
            commands = if (player == null) entry.commands.toList() else entry.commands.map { player.apply(it) },
            at = entry.at,
            expiresAt = null,
            key = entry.id + "@" + entry.at,
        )

        /** 공급처의 우편 → 화면이 그리는 [Entry](숫자야구 우편함 화면·PAPI 가 읽는다). 고쳐도 공급처에는 닿지 않는다. */
        fun entryOf(id: String, parcel: Mail.Parcel): Entry = Entry(
            id = id,
            at = parcel.at,
            source = parcel.source,
            items = parcel.items.toMutableList(),
            money = parcel.money,
            moneyBy = LinkedHashMap(parcel.moneyBy),
            commands = parcel.commands.toMutableList(),
        )
    }
}
