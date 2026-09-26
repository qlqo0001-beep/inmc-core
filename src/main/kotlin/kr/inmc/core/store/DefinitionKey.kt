package kr.inmc.core.store

/**
 * 설정 파일에 **섹션 이름으로 들어가는** 식별자의 규칙.
 *
 * 점을 막는 것이 이 규칙의 전부라고 해도 된다. [org.bukkit.configuration.file.YamlConfiguration]
 * 은 점을 경로 구분자로 다루므로 `보스.검` 이라는 이름으로 저장하면 `보스` 섹션 아래 `검` 이
 * 만들어지고, 다시 읽을 때 그 항목은 **조용히 사라진다.** 오류도 경고도 나지 않는다.
 * (낚시의 `modifiers.yml` 에서 `fishing.vip` 권한 노드가 통째로 무시되던 것이 같은 원인이다.)
 *
 * 공백과 대문자를 막는 것은 그보다 가벼운 이유다 — 명령어 인자로 오가는 값이라 따옴표 없이
 * 적을 수 있어야 하고, 조회가 전부 `lowercase()` 를 거치므로 대소문자가 다른 두 이름이
 * 같은 항목을 가리키면 관리자가 혼란스럽다.
 */
object DefinitionKey {

    private val VALID = Regex("^[a-z0-9_가-힣-]{1,32}\$")

    /** 규칙에 맞는 이름인지. 비교는 소문자로 한다. */
    fun isValid(key: String?): Boolean = key != null && VALID.matches(key.lowercase())

    /** 설명용 한 줄. 이름을 물어보는 화면이 그대로 쓴다. */
    const val HINT: String = "소문자 영문·숫자·한글·_- 만, 최대 32자"
}
