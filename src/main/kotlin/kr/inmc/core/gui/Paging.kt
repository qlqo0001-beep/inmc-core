package kr.inmc.core.gui

/**
 * 목록을 페이지로 나누는 계산.
 *
 * **Bukkit 을 모른다.** 이건 의도다 — 숫자야구는 인벤토리 메뉴가 아니라 Paper Dialog 로
 * 목록을 그리는데(`dialog/Screens.kt`, `dialog/admin/AdminScreens.kt`) 거기서도 똑같은 계산을
 * 한다. 앞으로 만들 업적·던전도 어느 쪽을 쓸지 모른다. 그래서 산술만 여기 두고, 인벤토리
 * 쪽 편의는 [Menu.paginate] 가 이 위에 얇게 얹는다.
 *
 * 4세대에는 이 계산이 **24곳**에 복사돼 있었고, 그러면서 버튼 위치가 제각각이 됐다 —
 * 몬스터는 46/47, urb 는 45 나 48. 같은 서버에서 플러그인을 오가면 뒤로가기 버튼이 자리를
 * 옮겼다. 슬롯 상수를 여기 둔 것은 그걸 다시 갈라지지 않게 하기 위해서다.
 */
object Paging {

    /** 6줄 인벤토리에서 마지막 줄을 버튼에 내주고 남는 칸. */
    const val PER_PAGE = 45

    const val SLOT_BACK = 45
    const val SLOT_PREV = 46
    const val SLOT_NEXT = 47
    const val SLOT_CLOSE = 53

    /** 항목이 없어도 1페이지다 — 빈 목록에서 "0/0 쪽"이 뜨지 않게. */
    fun pageCount(total: Int, perPage: Int = PER_PAGE): Int =
        if (perPage <= 0) 1 else maxOf(1, (total + perPage - 1) / perPage)

    /** 범위 밖 페이지를 가장 가까운 유효 페이지로. 항목이 지워져 페이지가 줄었을 때 쓴다. */
    fun clamp(page: Int, total: Int, perPage: Int = PER_PAGE): Int =
        page.coerceIn(0, pageCount(total, perPage) - 1)

    fun startIndex(page: Int, perPage: Int = PER_PAGE): Int = page * perPage

    /** [page] 가 범위 밖이면 먼저 [clamp] 한다. 빈 목록이면 빈 목록. */
    fun <T> slice(items: List<T>, page: Int, perPage: Int = PER_PAGE): List<T> {
        if (items.isEmpty()) return emptyList()
        val safe = clamp(page, items.size, perPage)
        return items.drop(startIndex(safe, perPage)).take(perPage)
    }
}
