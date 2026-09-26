package kr.inmc.core.store

import kr.inmc.core.config.ConfigService
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 플레이어별 저장 항목을 네임스페이스 단위로 보관한다.
 *
 * 4세대에는 플러그인마다 `players/<uuid>.yml` 을 각자 열고 각자 저장했다. 같은 플레이어가
 * 세 플러그인을 쓰면 파일 세 개가 서로 다른 주기로 쓰이고, 서버가 내려갈 때 어느 것이
 * 끝까지 저장됐는지 알 수 없었다. 여기서는 한 파일 · 한 워커로 모은다.
 *
 * **읽기는 항상 메모리 캐시에서 나간다.** 가이드 6장의 "조회는 항상 캐시" 규칙 그대로다.
 * 디스크는 [load] 와 [flush] 경로에만 등장한다.
 */
class PlayerStore(
    private val io: ConfigService,
    private val root: File,
) {

    /** uuid → (네임스페이스 → 값 맵) */
    private val cache = ConcurrentHashMap<UUID, MutableMap<String, MutableMap<String, Any?>>>()

    /** 저장이 필요한 플레이어. 티커가 주기적으로 훑어 비운다. */
    private val dirty = ConcurrentHashMap.newKeySet<UUID>()

    /** [load] 가 끝나기를 기다리는 콜백. 전부 메인 스레드에서 돈다. */
    private val waiters = ConcurrentLinkedQueue<() -> Unit>()

    @Volatile
    var ready: Boolean = false
        private set

    /**
     * [load] 가 끝난 뒤 메인 스레드에서 [action] 을 실행한다. 이미 끝났으면 즉시.
     *
     * `paper-plugin.yml` 의 `load: BEFORE` 로는 이걸 대신할 수 없다. 그건 **플러그인 enable
     * 순서**만 정한다. [load] 의 콜백은 리전 스케줄러의 `run` 으로 걸리므로 첫 틱에야
     * 돌고, 첫 틱은 모든 플러그인의 onEnable 이 끝난 뒤다 — 즉 의존 플러그인의 onEnable 전
     * 구간에서 [ready] 는 false 다. 저장소 내용이 필요한 일은 반드시 여기에 넣는다.
     */
    fun whenReady(action: () -> Unit) {
        if (ready) {
            action()
            return
        }
        waiters.add(action)
        // 넣는 사이에 준비됐을 수 있다. 둘 다 메인 스레드라 실제로는 안 겹치지만,
        // 큐에 남아 영원히 안 도는 것보다 한 번 더 확인하는 쪽이 싸다.
        if (ready) drainWaiters()
    }

    private fun drainWaiters() {
        while (true) {
            val action = waiters.poll() ?: return
            action()
        }
    }

    // --- 조회 / 변경 : 전부 메인 스레드, 전부 메모리 ---------------------------

    /**
     * [namespace] 아래의 값을 읽는다. 네임스페이스는 플러그인이 소유권을 갖는 이름이며
     * (`monster` · `numbergame` · `urb`), 남의 네임스페이스를 읽는 것은 막지 않지만
     * 쓰는 것은 하지 않기로 한다 — 강제할 방법이 없으므로 규약으로 둔다.
     */
    fun get(playerId: UUID, namespace: String, key: String): Any? =
        cache[playerId]?.get(namespace)?.get(key)

    fun getInt(playerId: UUID, namespace: String, key: String, fallback: Int = 0): Int =
        (get(playerId, namespace, key) as? Number)?.toInt() ?: fallback

    fun getLong(playerId: UUID, namespace: String, key: String, fallback: Long = 0L): Long =
        (get(playerId, namespace, key) as? Number)?.toLong() ?: fallback

    fun getString(playerId: UUID, namespace: String, key: String): String? =
        get(playerId, namespace, key) as? String

    /**
     * [key] 에 점을 넣을 수 없다. `YamlConfiguration` 이 점을 경로로 해석해 계층을 만들고,
     * 그러면 저장은 되는데 같은 키로 되읽히지 않는다 — 로그에는 아무것도 남지 않고
     * "데이터가 사라졌다" 로만 보인다. 프로그래밍 실수이므로 조용히 고치지 않고 바로 터뜨린다.
     * (`PlayerStoreTest` 의 왕복 테스트가 이 동작을 못박아 두고 있다.)
     */
    fun set(playerId: UUID, namespace: String, key: String, value: Any?) {
        requireFlat("네임스페이스" to namespace, "저장 키" to key)
        val perPlayer = cache.getOrPut(playerId) { ConcurrentHashMap() }
        val bucket = perPlayer.getOrPut(namespace) { ConcurrentHashMap() }
        if (value == null) bucket.remove(key) else bucket[key] = value
        dirty.add(playerId)
    }

    /** 네임스페이스 통째로 읽기. 도메인 서비스가 자기 몫을 한 번에 가져갈 때 쓴다. */
    fun section(playerId: UUID, namespace: String): Map<String, Any?> =
        cache[playerId]?.get(namespace)?.toMap() ?: emptyMap()

    // --- 3단: 네임스페이스 → 대상 → 필드 ---------------------------------------
    //
    // `uuid → 대상id → 필드` 는 세 플러그인이 공유하는 모양이다. 몬스터의 트리거 진행도,
    // urb 의 박스별 개봉 기록, 숫자야구의 게임별 플레이 기록이 전부 이것이다.
    //
    // 디스크에는 `네임스페이스.대상.필드` 로 그대로 쓴다. 키에 구분자를 인코딩해 넣는 방식
    // (`monster.trigger-progress/광질/count`) 도 가능하지만, `trigger-progress.yml` 은
    // **운영자가 직접 열어보는 파일**이라 그건 퇴보다.
    //
    // 되읽을 때의 모호성은 규칙 하나로 사라진다: [set] 이 스칼라만 받으므로 **값은 절대
    // 섹션이 아니다.** 자식이 섹션이면 대상 묶음, 아니면 스칼라.
    //
    // **정확히 3단까지다.** 4단 setter 는 만들지 않는다 — `ContractTest` 가 그걸 지킨다.

    @Suppress("UNCHECKED_CAST")
    private fun subjectMap(playerId: UUID, namespace: String, subject: String): MutableMap<String, Any?>? =
        cache[playerId]?.get(namespace)?.get(subject) as? MutableMap<String, Any?>

    fun get(playerId: UUID, namespace: String, subject: String, key: String): Any? =
        subjectMap(playerId, namespace, subject)?.get(key)

    fun getInt(playerId: UUID, namespace: String, subject: String, key: String, fallback: Int = 0): Int =
        (get(playerId, namespace, subject, key) as? Number)?.toInt() ?: fallback

    fun getLong(playerId: UUID, namespace: String, subject: String, key: String, fallback: Long = 0L): Long =
        (get(playerId, namespace, subject, key) as? Number)?.toLong() ?: fallback

    fun getString(playerId: UUID, namespace: String, subject: String, key: String): String? =
        get(playerId, namespace, subject, key) as? String

    fun set(playerId: UUID, namespace: String, subject: String, key: String, value: Any?) {
        requireFlat("네임스페이스" to namespace, "대상" to subject, "저장 키" to key)
        val perPlayer = cache.getOrPut(playerId) { ConcurrentHashMap() }
        val bucket = perPlayer.getOrPut(namespace) { ConcurrentHashMap() }

        @Suppress("UNCHECKED_CAST")
        val existing = bucket[subject] as? MutableMap<String, Any?>
        if (value == null && existing == null) return

        val fields = existing ?: ConcurrentHashMap<String, Any?>().also { bucket[subject] = it }
        if (value == null) {
            fields.remove(key)
            // 마지막 필드가 빠지면 대상 자체를 지운다. 빈 섹션은 YAML 에 남지도 않으므로
            // 남겨두면 메모리에만 있는 유령이 된다.
            if (fields.isEmpty()) bucket.remove(subject)
        } else {
            fields[key] = value
        }
        dirty.add(playerId)
    }

    /** 이 플레이어가 [namespace] 아래 갖고 있는 대상 id 전부. */
    fun subjects(playerId: UUID, namespace: String): Set<String> =
        cache[playerId]?.get(namespace)
            ?.filterValues { it is Map<*, *> }
            ?.keys
            ?.toSet()
            ?: emptySet()

    /** 대상 하나의 필드 묶음. */
    fun subject(playerId: UUID, namespace: String, subject: String): Map<String, Any?> =
        subjectMap(playerId, namespace, subject)?.toMap() ?: emptyMap()

    fun clearSubject(playerId: UUID, namespace: String, subject: String) {
        requireFlat("네임스페이스" to namespace, "대상" to subject)
        cache[playerId]?.get(namespace)?.remove(subject)
        dirty.add(playerId)
    }

    fun clear(playerId: UUID, namespace: String) {
        // 가드가 없으면 clear(id, "a.b") 가 조용히 아무것도 안 한다 — set 은 그런 이름을
        // 애초에 못 만들었을 테니, 여기 온 것은 호출자가 틀렸다는 뜻이다.
        requireFlat("네임스페이스" to namespace)
        cache[playerId]?.remove(namespace)
        dirty.add(playerId)
    }

    fun knownPlayers(): Set<UUID> = cache.keys.toSet()

    // 플레이어를 통째로 지우는 공개 API 는 **일부러 두지 않는다.**
    //
    // 한 플러그인이 그걸 부르면 남의 네임스페이스(특히 core 의 `profile`)까지 같이 날아간다.
    // 각자는 [clear] 로 자기 네임스페이스만 비우면 되고, 그 결과 아무것도 안 남은 파일은
    // [flush] 가 알아서 지운다. 회수가 자동이라 "만료했는데 빈 파일만 쌓인다" 가 생길 수 없다.

    // --- 디스크 : 전부 워커 스레드 ---------------------------------------------

    /**
     * 부팅 시 1회.
     *
     * 끝나기 전에 들어온 쓰기는 **버리지 않는다.** 콜백이 첫 틱에야 도는 탓에 의존
     * 플러그인의 onEnable 전 구간이 이 창에 들어가기 때문이다 ([whenReady] 참조).
     * 그래서 두 가지를 지킨다.
     *
     * - 여기서는 [merge] 로 합치되 **디스크가 메모리에 진다.** 캐시에 이미 있는 값은
     *   부팅 후에 쓰인 것이므로 더 최신이다.
     * - [flush] 는 [ready] 전에는 아무것도 쓰지 않는다. 아직 안 읽은 파일 위에 부분 상태를
     *   덮어쓰면 그 다음 [load] 가 덮어쓴 것을 읽게 된다.
     */
    fun load(then: () -> Unit) {
        io.async({
            val loaded = HashMap<UUID, MutableMap<String, MutableMap<String, Any?>>>()
            val files = root.listFiles { f -> f.isFile && f.name.endsWith(".yml") } ?: emptyArray()
            for (file in files) {
                // 파일명이 UUID 가 아니면 우리 것이 아니다. 예외를 던지는 대신 넘어간다 —
                // 손으로 넣은 파일 하나 때문에 전 플러그인이 안 뜨는 일은 없어야 한다.
                val id = runCatching { UUID.fromString(file.nameWithoutExtension) }.getOrNull()
                    ?: continue
                loaded[id] = ConcurrentHashMap(deserialize(io.load(file)))
            }
            loaded
        }) { loaded ->
            for ((id, fromDisk) in loaded) {
                cache[id] = merge(cache[id], fromDisk)
            }
            ready = true
            drainWaiters()
            then()
        }
    }

    /**
     * dirty 표시된 플레이어만 쓴다.
     *
     * YAML 을 만드는 작업은 여기(메인 스레드)에서 끝내고 파일 쓰기만 워커로 넘긴다.
     * 가이드 함정 16번과 같은 이유다 — 직렬화까지 워커로 미루면 그 사이에 값이 바뀌어
     * 저장된 내용이 어느 시점의 것인지 알 수 없게 된다.
     */
    fun flush() {
        if (!ready || dirty.isEmpty()) return
        val batch = dirty.toList()
        dirty.removeAll(batch.toSet())

        // yaml 이 null 이면 "지워라" 는 뜻이다. 네임스페이스를 비운 결과 아무것도 안 남은
        // 플레이어의 파일은 빈 껍데기로 남기지 않고 회수한다.
        val payloads = ArrayList<Pair<File, YamlConfiguration?>>(batch.size)
        for (id in batch) {
            val perPlayer = cache[id] ?: continue
            val alive = perPlayer.filterValues { it.isNotEmpty() }
            if (alive.isEmpty()) {
                cache.remove(id)
                payloads += File(root, "$id.yml") to null
            } else {
                payloads += File(root, "$id.yml") to serialize(alive)
            }
        }

        io.asyncRun {
            for ((file, yaml) in payloads) {
                if (yaml != null) {
                    io.save(file, yaml)
                } else if (file.exists() && !file.delete()) {
                    io.logger.warning("빈 플레이어 파일을 지우지 못했습니다: ${file.name}")
                }
            }
        }
    }

    /**
     * onDisable 경로.
     *
     * dirty 인 것만 쓴다. 전원을 dirty 로 찍으면 안 바뀐 파일까지 전부 다시 쓰게 되고,
     * `ConfigService.shutdown` 의 10초 예산 안에서 수천 명이면 정작 바뀐 것이 잘린다.
     * "전부 쓰니까 안전하다"가 아니라 그 반대다.
     */
    fun flushAll() {
        flush()
    }

    companion object {

        /**
         * 점 금지 가드. Bukkit 없이 테스트할 수 있도록 인스턴스 밖에 둔다.
         *
         * `YamlConfiguration` 이 점을 경로로 해석해 계층을 만들기 때문에, 점이 든 이름으로
         * 저장하면 **저장은 되는데 같은 이름으로 되읽히지 않는다.** 로그에는 아무것도 남지
         * 않고 "데이터가 사라졌다" 로만 보이므로, 조용히 고치지 않고 바로 터뜨린다.
         *
         * 쓰기 경로에만 건다. 읽기에서 던지면 PAPI 플레이스홀더 렌더가 통째로 죽는다.
         */
        fun requireFlat(vararg parts: Pair<String, String>) {
            for ((label, value) in parts) {
                require('.' !in value) { "$label 에 점을 쓸 수 없습니다: $value" }
            }
        }

        /**
         * 디스크에서 읽은 것을 메모리에 합친다. **메모리가 이긴다.**
         *
         * 캐시에 이미 있는 값은 부팅 후에 쓰인 것이라 디스크보다 최신이다. 예전에는
         * `putAll` 로 플레이어 맵을 통째로 갈아치웠는데, 그러면 [load] 가 끝나기 전에 들어온
         * 쓰기가 조용히 사라졌다 (그리고 [flush] 도 `!ready` 라 안 썼으므로 두 번 사라졌다).
         *
         * 값이 맵이면 subject 묶음이므로 한 단계 더 내려가 합친다 — 통째로 이기면 디스크에만
         * 있던 형제 필드가 같이 날아간다.
         */
        fun merge(
            cached: Map<String, Map<String, Any?>>?,
            fromDisk: Map<String, Map<String, Any?>>,
        ): MutableMap<String, MutableMap<String, Any?>> {
            val out = ConcurrentHashMap<String, MutableMap<String, Any?>>()
            for ((namespace, diskBucket) in fromDisk) {
                out[namespace] = ConcurrentHashMap(diskBucket.filterValues { it != null })
            }
            if (cached == null) return out

            for ((namespace, memBucket) in cached) {
                val target = out.getOrPut(namespace) { ConcurrentHashMap() }
                for ((key, memValue) in memBucket) {
                    if (memValue == null) continue
                    val diskValue = target[key]
                    target[key] = if (memValue is Map<*, *> && diskValue is Map<*, *>) {
                        mergeSubject(diskValue, memValue)
                    } else {
                        memValue
                    }
                }
            }
            return out
        }

        /** subject 한 묶음. 위와 같은 규칙 — 메모리 필드가 디스크 필드를 덮는다. */
        private fun mergeSubject(fromDisk: Map<*, *>, cached: Map<*, *>): MutableMap<String, Any?> {
            val out = ConcurrentHashMap<String, Any?>()
            for ((k, v) in fromDisk) if (k is String && v != null) out[k] = v
            for ((k, v) in cached) if (k is String && v != null) out[k] = v
            return out
        }

        /**
         * 한 플레이어의 네임스페이스 묶음을 YAML 로. Bukkit 서버 없이 도는 순수 함수다.
         *
         * [deserialize] 와 짝을 이루며 왕복 테스트가 붙어 있다. 가이드가 이 종류의 테스트를
         * 따로 꼽는 이유가 있다 — 저장은 되는데 안 읽히면 증상이 "GUI 가 저장을 안 했다" 로
         * 보이고 로그에는 아무것도 남지 않는다.
         */
        fun serialize(perPlayer: Map<String, Map<String, Any?>>): YamlConfiguration {
            val yaml = YamlConfiguration()
            for ((namespace, bucket) in perPlayer) {
                for ((key, value) in bucket) {
                    if (value is Map<*, *>) {
                        // 대상 묶음. 필드를 하나씩 내려 쓴다 — 맵을 통째로 set 하면
                        // YamlConfiguration 이 MemorySection 이 아니라 LinkedHashMap 으로
                        // 저장해 되읽을 때 모양이 달라진다.
                        for ((field, fieldValue) in value) {
                            if (field is String && fieldValue != null) {
                                yaml.set("$namespace.$key.$field", fieldValue)
                            }
                        }
                    } else {
                        yaml.set("$namespace.$key", value)
                    }
                }
            }
            return yaml
        }

        /**
         * [serialize] 의 역방향. 알 수 없는 모양은 예외 대신 조용히 건너뛴다.
         *
         * 자식이 섹션이면 **대상 묶음**으로 한 단계 더 내려간다. 예전에는 살아 있는
         * `MemorySection` 을 값 맵에 그대로 밀어넣었고, 그게 다음 [serialize] 로 되돌아가
         * 모양이 조용히 어긋났다. [set] 이 스칼라만 받으므로 "값은 절대 섹션이 아니다" 가
         * 성립하고, 그래서 이 판별은 애매하지 않다.
         */
        fun deserialize(yaml: YamlConfiguration): Map<String, MutableMap<String, Any?>> {
            val out = HashMap<String, MutableMap<String, Any?>>()
            for (namespace in yaml.getKeys(false)) {
                val node = yaml.getConfigurationSection(namespace) ?: continue
                val bucket = ConcurrentHashMap<String, Any?>()
                for (key in node.getKeys(false)) {
                    val child = node.getConfigurationSection(key)
                    if (child != null) {
                        val fields = ConcurrentHashMap<String, Any?>()
                        for (field in child.getKeys(false)) child.get(field)?.let { fields[field] = it }
                        if (fields.isNotEmpty()) bucket[key] = fields
                    } else {
                        node.get(key)?.let { bucket[key] = it }
                    }
                }
                out[namespace] = bucket
            }
            return out
        }
    }
}
