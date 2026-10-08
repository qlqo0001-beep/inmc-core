package kr.inmc.core.input

import kr.inmc.core.InmcHost
import kr.inmc.core.gui.DialogForm
import kr.inmc.core.util.Numbers
import kr.inmc.core.util.Text
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 관리 화면이 값 하나를 받는 창구 — 이름·확률·메시지·명령어.
 *
 * **2026-10-08 부터 입력창(Paper Dialog)을 연다.** 전에는 화면을 닫고 채팅 한 줄을 기다렸다(60초 뒤 조용히 끝남, "취소" 로 중단).
 * 상점·메뉴·드랍·던전은 이미 [DialogForm] 이었고 나머지 여덟 플러그인의 약 210곳이 이 클래스를 지나므로, 여기를 바꾸면 전부가
 * 한 번에 입력창이 된다 — 부르는 쪽의 서명·콜백 규칙(메인 스레드에서 돈다·[onCancel] 이 화면을 다시 연다)은 그대로다.
 *
 * 입력창은 첫 줄을 제목으로, 나머지 줄을 설명으로 쓴다(`promptLines`). 숫자는 [DialogForm] 이 범위까지 검사해 틀리면 친 값을 둔 채
 * 다시 연다. 빈 값으로 확인하면 취소로 친다. Esc = 취소.
 *
 * 채팅 경로는 한 판 남겨 둔다 — JVM 인자 `-Dinmc.prompts=chat` 이면 옛 방식(플러그인의 채팅 리스너·`isWaiting`·메시지 키 넷이 아직 있다).
 */
class ChatPrompt(private val host: InmcHost) {

    private class Pending(
        val expiresAt: Long,
        val onInput: (String) -> Unit,
        val onCancel: () -> Unit,
    )

    private val pending = ConcurrentHashMap<UUID, Pending>()

    /** 채팅 경로에서만 참이다 — 입력창은 채팅을 쓰지 않는다. */
    fun isWaiting(playerId: UUID): Boolean = pending.containsKey(playerId)

    /**
     * [player] 에게 글자 하나를 묻는다. [onInput]·[onCancel] 은 늘 메인 스레드(그 플레이어의 스케줄러)에서 돌므로 바로 화면을 열어도 된다.
     */
    fun request(
        player: Player,
        promptLines: List<String>,
        timeoutSeconds: Long = 60L,
        onCancel: () -> Unit = {},
        onInput: (String) -> Unit,
    ) {
        if (!useDialogs) return requestInChat(player, promptLines, timeoutSeconds, onCancel, onInput)
        form(promptLines)
            .text(VALUE, LABEL_TEXT, maxLength = 256)
            .show(host.plugin, player, onCancel = { onCancel() }) { _, values ->
                val text = values.text(VALUE).trim()
                if (text.isEmpty()) onCancel() else onInput(text)
            }
    }

    /** 소수 하나. 범위 밖이면 입력창이 다시 묻는다(채팅 때는 범위로 잘랐다). */
    fun requestDouble(
        player: Player,
        promptLines: List<String>,
        min: Double,
        max: Double,
        onCancel: () -> Unit = {},
        onValue: (Double) -> Unit,
    ) {
        if (!useDialogs) return requestDoubleInChat(player, promptLines, min, max, onCancel, onValue)
        form(promptLines)
            .decimal(VALUE, LABEL_NUMBER, initial = null, min = min, max = max)
            .show(host.plugin, player, onCancel = { onCancel() }) { _, values ->
                val value = values.decimal(VALUE) ?: return@show onCancel()
                onValue(Numbers.round2(value.coerceIn(min, max)))
            }
    }

    fun requestInt(
        player: Player,
        promptLines: List<String>,
        min: Int,
        max: Int,
        onCancel: () -> Unit = {},
        onValue: (Int) -> Unit,
    ) {
        if (!useDialogs) return requestIntInChat(player, promptLines, min, max, onCancel, onValue)
        form(promptLines)
            .long(VALUE, LABEL_NUMBER, initial = null, min = min.toLong(), max = max.toLong())
            .show(host.plugin, player, onCancel = { onCancel() }) { _, values ->
                val value = values.long(VALUE) ?: return@show onCancel()
                onValue(value.coerceIn(min.toLong(), max.toLong()).toInt())
            }
    }

    private fun form(promptLines: List<String>): DialogForm {
        val (title, body) = split(promptLines)
        return DialogForm(title).apply { body.forEach { line(it) } }
    }

    // --- 채팅 경로(옛 방식, -Dinmc.prompts=chat) -------------------------------------------

    private fun requestInChat(player: Player, promptLines: List<String>, timeoutSeconds: Long, onCancel: () -> Unit, onInput: (String) -> Unit) {
        player.closeInventory()
        pending[player.uniqueId] = Pending(
            expiresAt = System.currentTimeMillis() + timeoutSeconds * 1000L,
            onInput = onInput,
            onCancel = onCancel,
        )
        promptLines.forEach { player.sendMessage(Text.render(it, null, player)) }
        host.tell(player, "prompt-enter")
    }

    private fun requestDoubleInChat(player: Player, promptLines: List<String>, min: Double, max: Double, onCancel: () -> Unit, onValue: (Double) -> Unit) {
        requestInChat(player, promptLines, 60L, onCancel) { input ->
            val value = input.replace(",", "").trim().toDoubleOrNull()
            if (value == null) {
                host.tell(player, "prompt-invalid-number")
                requestDoubleInChat(player, promptLines, min, max, onCancel, onValue)
            } else {
                onValue(Numbers.round2(value.coerceIn(min, max)))
            }
        }
    }

    private fun requestIntInChat(player: Player, promptLines: List<String>, min: Int, max: Int, onCancel: () -> Unit, onValue: (Int) -> Unit) {
        requestInChat(player, promptLines, 60L, onCancel) { input ->
            val value = input.replace(",", "").trim().toIntOrNull()
            if (value == null) {
                host.tell(player, "prompt-invalid-number")
                requestIntInChat(player, promptLines, min, max, onCancel, onValue)
            } else {
                onValue(value.coerceIn(min, max))
            }
        }
    }

    /**
     * 비동기 채팅 리스너가 부른다. 채팅 한 줄을 입력으로 먹었으면 true(사건을 취소할 것). 입력창 경로에서는 늘 false.
     */
    fun submit(player: Player, message: String): Boolean {
        val entry = pending.remove(player.uniqueId) ?: return false
        val text = message.trim()

        // Chat arrives off the main thread, so the plugin may already be going down. Still
        // report the line as consumed: the prompt is dead either way, and answering "not mine"
        // would let the typed text through to public chat - which for a webhook prompt means
        // broadcasting the URL.
        if (!host.plugin.isEnabled) return true

        // 이 플레이어를 소유한 스레드로 넘긴다 (Folia 에서는 그 플레이어의 리전, 일반 Paper
        // 에서는 메인). 답을 받기 전에 접속을 끊으면 아무것도 돌지 않는데, 그게 맞다 —
        // 프롬프트는 위에서 이미 pending 에서 빠졌다.
        player.scheduler.run(host.plugin, {
            if (text.equals("취소", ignoreCase = true) || text.equals("cancel", ignoreCase = true)) {
                host.tell(player, "prompt-cancelled")
                entry.onCancel()
            } else {
                entry.onInput(text)
            }
        }, null)
        return true
    }

    fun cancel(playerId: UUID) {
        pending.remove(playerId)
    }

    /** Ticker hook: expires abandoned chat prompts. */
    fun tick(now: Long) {
        if (pending.isEmpty()) return
        val expired = pending.entries.filter { it.value.expiresAt <= now }
        for ((playerId, entry) in expired) {
            pending.remove(playerId)
            Bukkit.getPlayer(playerId)?.let { host.tell(it, "prompt-timeout") }
            entry.onCancel()
        }
    }

    companion object {
        /** 입력창 칸 이름. `id` 는 Paper 의 예약어라 쓰지 않는다. */
        const val VALUE = "value"
        private const val LABEL_TEXT = "입력"
        private const val LABEL_NUMBER = "값"

        /** `-Dinmc.prompts=chat` 이면 옛 채팅 방식. */
        @JvmStatic
        var useDialogs: Boolean = !System.getProperty("inmc.prompts").equals("chat", ignoreCase = true)

        /** 첫 줄 = 제목, 나머지 = 설명. 비어 있으면 "입력". */
        fun split(promptLines: List<String>): Pair<String, List<String>> {
            val title = promptLines.firstOrNull()?.takeIf { it.isNotBlank() } ?: "입력"
            return title to promptLines.drop(1).filter { it.isNotBlank() }
        }
    }
}
