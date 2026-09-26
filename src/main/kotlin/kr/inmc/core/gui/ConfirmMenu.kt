package kr.inmc.core.gui

import kr.inmc.core.util.Text
import org.bukkit.Material

/**
 * 되돌릴 수 없는 동작 앞에 세우는 두 버튼 확인창.
 *
 * Shift 클릭 관례 대신 이걸 쓰는 이유는, 관리자가 오후 내내 만든 정의를 목록에서 잘못 누른
 * 한 번으로 지울 수 있으면 안 되기 때문이다.
 *
 * 몬스터와 urb 가 같은 화면을 따로 갖고 있었고 슬롯과 시그니처만 달랐다. urb 의 배치
 * (11 확인 · 13 질문 · 15 취소)를 택했다 — 질문이 두 버튼 사이에 놓여 한 줄로 읽힌다.
 */
class ConfirmMenu(
    override val owner: Any?,
    private val question: String,
    private val detail: List<String> = emptyList(),
    title: String = "<dark_red>확인</dark_red>",
    private val confirmLabel: String = "<green>✔ 진행합니다</green>",
    private val cancelLabel: String = "<red>✖ 취소</red>",
    private val onConfirm: () -> Unit,
    private val onCancel: () -> Unit = {},
) : Menu(27, Text.renderFlat(title)) {

    override fun draw() {
        clear()
        fillEmpty(Icon.EDGE)

        set(13, Icon.of(Material.PAPER, question, detail))

        // 두 버튼 모두 클릭 핸들러에서 다시 열거나 닫는다. onClose 에서 하지 않는 것은,
        // 인벤토리가 닫히는 중에 다른 인벤토리를 여는 것이 Bukkit 에서 불안정하기 때문이다.
        // 그래서 Esc 는 아무것도 바꾸지 않고 창만 닫는다 — Esc 가 해야 할 일 그대로다.
        set(11, Icon.confirm(confirmLabel, listOf("<gray>되돌릴 수 없습니다.</gray>"))) { onConfirm() }

        set(15, Icon.cancel(cancelLabel)) { onCancel() }
    }
}
