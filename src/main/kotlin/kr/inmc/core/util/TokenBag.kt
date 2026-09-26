package kr.inmc.core.util

/**
 * 메시지 한 번 렌더링에 쓰이는 토큰 주머니의 공통부.
 *
 * 세 플러그인의 `Ph` 가 값 맵·`raw`·`put`·`apply`·`copy` 를 **글자 단위로 같게** 갖고 있었다.
 * 서로 다른 것은 토큰 이름과 별칭표뿐이고, 그건 도메인 그 자체라 공통화 대상이 아니다 —
 * 몬스터는 `{몹}` `{수식어}`, urb 는 `{박스}` `{등급}`, 숫자야구는 `{게임}` `{시도}` 를 쓴다.
 *
 * [S] 는 자기 타입이다. `Ph.of().player(x).item(y)` 처럼 이어 쓰는 코드가 30곳쯤 있어서,
 * [put] 과 [raw] 가 베이스 타입이 아니라 각 플러그인의 `Ph` 를 돌려줘야 한다.
 */
abstract class TokenBag<S : TokenBag<S>> : Placeholders {

    protected val values = LinkedHashMap<String, String>()

    /**
     * 토큰 → 관리자가 설정 파일에 실제로 적는 표기들.
     *
     * 한글과 영문을 같이 받는다. 관리자는 `{플레이어}` 를 쓰고 예제 설정은 `{player}` 를
     * 쓰는 식이라, 어느 쪽으로 적어도 같은 값이 들어가야 한다.
     */
    protected abstract val aliases: Map<String, List<String>>

    @Suppress("UNCHECKED_CAST")
    protected fun put(token: String, value: String): S {
        values[token] = value
        return this as S
    }

    /** 별칭표에 없는 토큰을 직접 넣는다. 이름 그대로만 치환된다. */
    fun raw(token: String, value: String): S = put(token, value)

    /** 하위 클래스의 `copy()` 가 새 인스턴스를 만들어 넘기면 값을 복사해 돌려준다. */
    protected fun copyValuesInto(target: S): S {
        target.values.putAll(values)
        return target
    }

    /**
     * 토큰을 값으로 바꾼다.
     *
     * **치환된 값 안에 다른 토큰의 별칭이 들어 있으면 그것도 바뀐다.** 값들을 순서대로 훑기
     * 때문이다. 몬스터 이름에 `{플레이어}` 가 들어 있는 경우 같은 것 — 실제로 일어나기
     * 어렵지만 동작은 그렇고, 바꾸면 기존 메시지가 달라지므로 그대로 둔다.
     */
    override fun apply(raw: String): String {
        if (raw.isEmpty() || values.isEmpty()) return raw
        var out = raw
        for ((token, value) in values) {
            for (alias in aliases[token] ?: listOf(token)) {
                if (out.contains(alias)) out = out.replace(alias, value)
            }
        }
        return out
    }
}
