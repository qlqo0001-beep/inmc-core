package kr.inmc.core.event

import java.util.concurrent.ConcurrentHashMap

/**
 * "이 신호에는 어떤 값이 올 수 있는가" 를 **발행하는 쪽이 스스로 알려주는** 자리.
 *
 * ## 이게 없으면 생기는 일
 *
 * 업적 관리자가 편집 화면에서 물고기 id 를 **손으로 타이핑한다.** 오타가 나면 그 업적은
 * 오류 없이 **영영 완료되지 않는다.** 로그에도 아무것도 안 남고, 만든 사람은 조건이
 * 안 먹는다고 느낄 뿐 이유를 모른다. 이 설계에서 가장 날 법한 버그가 그것이다.
 *
 * 낚시가 여기에 자기 물고기 목록을 등록해 두면 업적 편집기가 **진짜 이름으로 된 목록**을
 * 그린다. 컴파일 의존은 양쪽 다 0이다 — 낚시는 업적을 모르고, 업적은 낚시를 모른다.
 *
 * ## companion 에 두는 이유
 *
 * 플러그인마다 자기 인스턴스를 갖는 구조면 다섯 개의 다른 인스턴스에 꽂을 방법이 없다.
 * core 의 클래스는 플러그인들이 공유하므로(`join-classpath: true`) 여기 목록이 **진짜
 * 하나의 목록**이 된다. [kr.inmc.core.integration.CustomItemHook] 이 같은 방식이다.
 *
 * ## 수명주기
 *
 * 열쇠는 `(source, type)` 이고 **재등록은 교체다.** 토큰을 돌려주는 방식도 있지만 호출자가
 * 그걸 보관해야 하고 잊으면 그대로 샌다. 교체 방식이면 리로드가 **저절로** 안전해진다.
 * 내려갈 때는 [unregisterAll] 한 줄이면 된다 — [Entry.subjects] 같은 람다는 그 플러그인의
 * 객체와 **클래스로더를 붙들고 있으므로** 빼지 않으면 샌다.
 */
object SignalCatalog {

    /**
     * 한 신호가 무엇을 싣는지.
     *
     * [subjects] 와 [subjectsOf] 가 **람다인 것이 중요하다.** 등록 시점에 목록을 떠서
     * 넘기면 관리자가 GUI 로 물고기를 새로 만들어도 업적 편집기에는 안 나온다. 그리고
     * `load: BEFORE` 는 `onEnable` 순서만 정할 뿐이라 등록 시점에는 정의가 아직 비어 있을
     * 수도 있다 — **열 때마다 읽는다.**
     */
    class Entry(
        val source: String,
        val type: String,
        /** 화면에 뭐라고 쓸지. 예: `물고기` · `상자`. */
        val subjectLabel: String,
        /** `id to 표시이름` 목록. 매번 불린다. */
        val subjects: () -> List<Pair<String, String>>,
        /** `키 to 표시이름`. 조건의 추가 일치 항목으로 고를 수 있는 것들. */
        val dataKeys: List<Pair<String, String>>,
        /** 한 줄 설명. 편집 화면에 뜬다. */
        val description: String,
    ) {
        val key: String get() = source + "/" + type
    }

    private val entries = ConcurrentHashMap<String, Entry>()

    /** 같은 `(source, type)` 이 이미 있으면 **갈아끼운다.** */
    @JvmStatic
    fun register(
        source: String,
        type: String,
        subjectLabel: String,
        subjects: () -> List<Pair<String, String>>,
        dataKeys: List<Pair<String, String>> = emptyList(),
        description: String = "",
    ) {
        val entry = Entry(
            source = source.lowercase(),
            type = type.lowercase(),
            subjectLabel = subjectLabel,
            subjects = subjects,
            dataKeys = dataKeys,
            description = description,
        )
        entries[entry.key] = entry
    }

    @JvmStatic
    fun unregister(source: String, type: String) {
        entries.remove(source.lowercase() + "/" + type.lowercase())
    }

    /** 내려갈 때. 한 줄로 그 플러그인 것을 전부 뺀다. */
    @JvmStatic
    fun unregisterAll(source: String) {
        val prefix = source.lowercase() + "/"
        entries.keys.removeIf { it.startsWith(prefix) }
    }

    @JvmStatic
    fun all(): List<Entry> = entries.values.sortedBy { it.key }

    @JvmStatic
    fun sources(): List<String> = entries.values.map { it.source }.distinct().sorted()

    @JvmStatic
    fun typesOf(source: String): List<Entry> =
        entries.values.filter { it.source == source.lowercase() }.sortedBy { it.type }

    @JvmStatic
    fun get(source: String, type: String): Entry? =
        entries[source.lowercase() + "/" + type.lowercase()]

    /**
     * 이 값을 아는 플러그인이 있는가.
     *
     * 편집 화면이 **모르는 값을 빨갛게** 보여주는 데 쓴다. 조용히 받아들이면 그 업적이
     * 영영 완료되지 않는 것을 아무도 모른다. 등록된 항목이 없으면(그 플러그인이 안 켜졌으면)
     * **판단하지 않는다** — 모른다고 빨갛게 칠하면 낚시를 잠시 끈 서버에서 멀쩡한 설정이
     * 전부 오류처럼 보인다.
     */
    @JvmStatic
    fun knowsSubject(source: String, type: String, subject: String): Boolean? {
        val entry = get(source, type) ?: return null
        return entry.subjects().any { it.first.equals(subject, ignoreCase = true) }
    }

    /** 테스트용. 운영 중에는 부르지 않는다. */
    @JvmStatic
    fun clear() {
        entries.clear()
    }
}
