package kr.inmc.core.store

import java.util.UUID

/**
 * 모든 INMC 플러그인이 같이 쓰는 플레이어 신원.
 *
 * 4세대에는 숫자야구가 `plays.yml` 의 `names:` 로, urb 가 `stats.yml` 의
 * `players.<uuid>.name` 으로 **각자 손으로** uuid→이름 캐시를 들고 있었고 몬스터는 아예
 * 없었다. 같은 값을 두 벌 관리하면 반드시 한쪽이 뒤처지고, 실제로 숫자야구 쪽은 이름을
 * 갱신하면서 dirty 를 세우지 않아 크래시하면 잃는 상태였다.
 *
 * 이름이 필요한 이유는 어느 플러그인이나 같다 — 랭킹·우편함·로그에 **오프라인 플레이어의
 * 이름**을 보여줘야 하는데, UUID 만으로는 못 한다.
 *
 * 네임스페이스 소유권은 core 에 있다. 플러그인은 [nameOf] 로 읽기만 한다.
 */
object Profile {

    const val NAMESPACE = "profile"

    const val NAME = "name"

    /** 마지막으로 본 시각(epoch ms). 임포트 병합에서 어느 쪽이 최신인지 가르는 값이다. */
    const val SEEN = "seen"

    fun nameOf(store: PlayerStore, playerId: UUID): String? =
        store.getString(playerId, NAMESPACE, NAME)

    fun seenAt(store: PlayerStore, playerId: UUID): Long =
        store.getLong(playerId, NAMESPACE, SEEN)

    /**
     * 지금 본 것으로 기록한다. 접속·퇴장 양쪽에서 부른다.
     *
     * 접속은 항상 이긴다 — [merge] 규칙이 큰 `seen` 을 택하고, 실시간 시각은 어떤 과거
     * 기록보다도 크기 때문이다.
     */
    fun touch(store: PlayerStore, playerId: UUID, name: String, at: Long) {
        store.set(playerId, NAMESPACE, NAME, name)
        store.set(playerId, NAMESPACE, SEEN, at)
    }

    /**
     * 임포터용 병합. **`seen` 이 큰 쪽이 이긴다.**
     *
     * 임포터가 둘(숫자야구·urb)이라 실행 순서가 결과를 바꾸면 안 된다. 숫자야구의 `names:`
     * 에는 타임스탬프가 아예 없어 `seen = 0` 으로 들어오는데, 그건 어떤 실제 기록에도 지므로
     * 정확히 원하는 결과다.
     *
     * 순수 함수다. 저장소에 쓰지 않고 "무엇을 써야 하는지"만 돌려준다.
     */
    fun merge(
        existingName: String?,
        existingSeen: Long,
        incomingName: String?,
        incomingSeen: Long,
    ): Pair<String?, Long>? {
        if (incomingName == null) return null
        if (existingName != null && existingSeen >= incomingSeen) return null
        return incomingName to maxOf(existingSeen, incomingSeen)
    }
}
