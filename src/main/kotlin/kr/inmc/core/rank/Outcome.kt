package kr.inmc.core.rank

/**
 * 한 판이 끝났을 때 랭킹·보상 계층에 넘기는 숫자들.
 *
 * 도메인이 무엇이든(숫자게임 한 판, 몬스터 처치, 박스 개봉) 랭킹이 필요로 하는 것은
 * 이 다섯 개뿐이라 core 로 올렸다. [record] 와 [score] 를 **항상 같이** 기록하는 것이
 * 중요하다 — 순위 기준을 나중에 바꿔도 아무도 초기화되지 않게 하기 위해서다.
 */
data class Outcome(
    /** [RankMode.BEST_RECORD] 의 1차 정렬 키. */
    val record: Long,
    /** 동점자 처리용. 항상 "작을수록 좋다" (걸린 밀리초). */
    val tiebreak: Long,
    /** [RankMode.CUMULATIVE_SCORE] 에 더할 점수. 음수일 수 있다. */
    val score: Long,
    /** 결과 화면에 뿌릴 줄들. */
    val summary: List<String>,
    /** 베팅류의 순 재화 이동. 그 외에는 0. */
    val netMoney: Double = 0.0,
)
