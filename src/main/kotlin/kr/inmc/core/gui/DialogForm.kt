package kr.inmc.core.gui

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.dialog.DialogResponseView
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import kr.inmc.core.util.Text
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.time.Duration

/**
 * Paper Dialog 의 함정을 한 곳에서 막는다. 숫자야구(`Dlg`)와 [DialogForm] 이 쓴다.
 */
object Dialogs {

    /** Paper 가 클릭 콜백의 자기 기록(`id` = 콜백 UUID)을 입력값과 같은 자리에 둔다. 같은 이름의 입력은 그것을 덮는다. */
    val RESERVED_INPUT_KEYS: Set<String> = setOf("id")

    /**
     * 입력 이름을 Minecraft 가 받는 것으로. 서버는 `StringTemplate.isValidVariableName`(글자·숫자·밑줄)로 검사하고,
     * 어기면 창을 만드는 순간 던진다 — 플레이어에게는 안 열리는 창으로만 보인다. 예약어(`id`)는 [reservedPrefix] 를 붙인다
     * — 그대로 두면 버튼이 조용히 아무것도 안 한다(`Failed to read field (id=…)`).
     */
    fun inputKey(raw: String, reservedPrefix: String = "in_"): String {
        val safe = buildString(raw.length) {
            for (c in raw) append(if (c.isLetterOrDigit() || c == '_') c else '_')
        }.ifEmpty { "_" }
        return if (safe in RESERVED_INPUT_KEYS) reservedPrefix + safe else safe
    }

    /**
     * 몇 번이고, 30분 동안. 기본값(한 번)이면 두 번 누른 사람의 두 번째 클릭이 조용히 죽고,
     * 오래 둔 창의 버튼은 서버가 무시하는 것처럼 보인다.
     */
    val CALLBACK_OPTIONS: ClickCallback.Options = ClickCallback.Options.builder()
        .uses(ClickCallback.UNLIMITED_USES)
        .lifetime(Duration.ofMinutes(30))
        .build()

    /** "1,000" · " 25 " · "-3" → 정수. 못 읽으면 null. */
    fun parseLong(raw: String?): Long? = raw?.replace(",", "")?.replace("_", "")?.trim()?.toLongOrNull()

    /** "12.5" · "1,000.5" → 소수. 못 읽으면 null. */
    fun parseDouble(raw: String?): Double? = raw?.replace(",", "")?.replace("_", "")?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }
}

/**
 * 값 몇 개를 받는 입력창 — 확인/취소. 채팅 입력 대신 쓴다(상점 편집기).
 *
 * - 콜백은 **메인 스레드(그 플레이어의 스케줄러)에서, 한 틱 뒤에** 돈다. 클라이언트가 창을 닫는 중에 보낸 화면은 버려진다.
 * - 숫자 칸은 글자 입력으로 받아 여기서 읽는다(슬라이더는 소수 float 이라 큰 금액이 뭉개진다). 못 읽거나 범위 밖이면 **친 값을
 *   그대로 둔 채** 빨간 줄과 함께 다시 연다 — 다 지우고 처음부터 치게 하지 않는다.
 * - Esc 는 취소와 같다(확인창의 "아니오" 동작).
 * - 창을 열기 전에 열린 상자 화면을 닫는다. [onCancel]·[onSubmit] 이 원래 화면을 다시 열면 된다.
 */
class DialogForm(private val title: String) {

    sealed interface Field {
        val key: String
        val label: String
    }

    data class TextField(override val key: String, override val label: String, val initial: String, val maxLength: Int, val multiline: Boolean) : Field
    data class LongField(override val key: String, override val label: String, val initial: Long?, val min: Long, val max: Long, val optional: Boolean) : Field
    data class DecimalField(override val key: String, override val label: String, val initial: Double?, val min: Double, val max: Double, val optional: Boolean) : Field
    data class ToggleField(override val key: String, override val label: String, val initial: Boolean) : Field
    data class ChoiceField(override val key: String, override val label: String, val options: List<Pair<String, String>>, val initial: String?) : Field

    private val lines = ArrayList<String>()
    private val fields = ArrayList<Field>()
    var submitLabel: String = "<green>확인</green>"
    var cancelLabel: String = "<gray>취소</gray>"

    fun line(text: String) = apply { lines += text }

    fun text(key: String, label: String, initial: String = "", maxLength: Int = 256, multiline: Boolean = false) =
        apply { fields += TextField(key, label, initial, maxLength, multiline) }

    /** 정수. [optional] 이면 비워 둘 수 있다(값은 null). */
    fun long(key: String, label: String, initial: Long?, min: Long = Long.MIN_VALUE, max: Long = Long.MAX_VALUE, optional: Boolean = false) =
        apply { fields += LongField(key, label, initial, min, max, optional) }

    fun decimal(key: String, label: String, initial: Double?, min: Double = -Double.MAX_VALUE, max: Double = Double.MAX_VALUE, optional: Boolean = false) =
        apply { fields += DecimalField(key, label, initial, min, max, optional) }

    fun toggle(key: String, label: String, initial: Boolean) = apply { fields += ToggleField(key, label, initial) }

    /** 하나 고르기. [options] 는 (id, 보이는 이름). */
    fun choice(key: String, label: String, options: List<Pair<String, String>>, initial: String?) =
        apply { if (options.isNotEmpty()) fields += ChoiceField(key, label, options, initial) }

    /** 받은 값. 숫자 칸은 이미 검사를 통과했다. */
    class Values internal constructor(private val texts: Map<String, String?>, private val toggles: Map<String, Boolean>) {
        fun text(key: String): String = texts[key].orEmpty()
        fun long(key: String): Long? = Dialogs.parseLong(texts[key])
        fun decimal(key: String): Double? = Dialogs.parseDouble(texts[key])
        fun bool(key: String): Boolean = toggles[key] ?: false
        fun choice(key: String): String? = texts[key]
    }

    fun show(plugin: Plugin, player: Player, onCancel: (Player) -> Unit = {}, onSubmit: (Player, Values) -> Unit) {
        show(plugin, player, emptyMap(), null, onCancel, onSubmit)
    }

    private fun show(
        plugin: Plugin,
        player: Player,
        typed: Map<String, String?>,
        error: String?,
        onCancel: (Player) -> Unit,
        onSubmit: (Player, Values) -> Unit,
    ) {
        val submit = button(plugin, submitLabel) { who, view ->
            val texts = HashMap<String, String?>()
            val toggles = HashMap<String, Boolean>()
            for (field in fields) {
                val key = Dialogs.inputKey(field.key)
                when (field) {
                    is ToggleField -> toggles[field.key] = view.getBoolean(key) ?: field.initial
                    else -> texts[field.key] = view.getText(key)
                }
            }
            val problem = validate(texts)
            if (problem != null) show(plugin, who, texts, problem, onCancel, onSubmit)
            else onSubmit(who, Values(texts, toggles))
        }
        val cancel = button(plugin, cancelLabel) { who, _ -> onCancel(who) }
        val body = buildList {
            if (error != null) add("<red>$error</red>")
            addAll(lines)
        }
        val dialog = Dialog.create { factory ->
            factory.empty()
                .base(
                    DialogBase.builder(Text.renderFlat(title))
                        .canCloseWithEscape(true)
                        // 멀티 서버다. 멈추면 싱글 월드가 멈추는 것과 같은 뜻이 된다.
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(body(body))
                        .inputs(fields.map { input(it, typed) })
                        .build(),
                )
                .type(DialogType.confirmation(submit, cancel))
        }
        player.scheduler.run(plugin, { _ ->
            if (!player.isOnline) return@run
            if (player.openInventory.topInventory.holder is Menu) player.closeInventory()
            player.showDialog(dialog)
        }, null)
    }

    /** 숫자 칸 검사. 문제가 있으면 그 문장, 없으면 null. */
    internal fun validate(texts: Map<String, String?>): String? {
        for (field in fields) {
            val raw = texts[field.key]?.trim().orEmpty()
            when (field) {
                is LongField -> {
                    if (raw.isEmpty()) { if (field.optional) continue else return "${Text.plain(field.label)}: 값을 넣어 주세요" }
                    val value = Dialogs.parseLong(raw) ?: return "${Text.plain(field.label)}: 정수를 넣어 주세요 ($raw)"
                    if (value < field.min || value > field.max) return "${Text.plain(field.label)}: ${field.min} ~ ${field.max} 사이로 넣어 주세요"
                }
                is DecimalField -> {
                    if (raw.isEmpty()) { if (field.optional) continue else return "${Text.plain(field.label)}: 값을 넣어 주세요" }
                    val value = Dialogs.parseDouble(raw) ?: return "${Text.plain(field.label)}: 숫자를 넣어 주세요 ($raw)"
                    if (value < field.min || value > field.max) return "${Text.plain(field.label)}: ${field.min} ~ ${field.max} 사이로 넣어 주세요"
                }
                else -> Unit
            }
        }
        return null
    }

    private fun input(field: Field, typed: Map<String, String?>): DialogInput {
        val key = Dialogs.inputKey(field.key)
        val label = Text.renderFlat(field.label)
        return when (field) {
            is TextField -> DialogInput.text(key, label)
                .maxLength(field.maxLength.coerceIn(1, 32767))
                .width(WIDTH)
                .initial(typed[field.key] ?: field.initial)
                .apply { if (field.multiline) multiline(io.papermc.paper.registry.data.dialog.input.TextDialogInput.MultilineOptions.create(8, 120)) }
                .build()
            is LongField -> DialogInput.text(key, label).maxLength(24).width(WIDTH)
                .initial(typed[field.key] ?: field.initial?.toString().orEmpty()).build()
            is DecimalField -> DialogInput.text(key, label).maxLength(24).width(WIDTH)
                .initial(typed[field.key] ?: field.initial?.let(::plainDecimal).orEmpty()).build()
            is ToggleField -> DialogInput.bool(key, label).initial(field.initial).build()
            is ChoiceField -> {
                val chosen = typed[field.key] ?: field.initial ?: field.options.first().first
                DialogInput.singleOption(
                    key, label,
                    field.options.map { (id, display) -> SingleOptionDialogInput.OptionEntry.create(id, Text.renderFlat(display), id == chosen) },
                ).width(WIDTH).build()
            }
        }
    }

    private fun body(lines: List<String>): List<DialogBody> {
        if (lines.isEmpty()) return emptyList()
        var joined: Component = Component.empty()
        lines.forEachIndexed { index, line ->
            if (index > 0) joined = joined.append(Component.newline())
            joined = joined.append(Text.renderFlat(line))
        }
        return listOf(DialogBody.plainMessage(joined, 320))
    }

    private fun button(plugin: Plugin, label: String, onClick: (Player, DialogResponseView) -> Unit): ActionButton =
        ActionButton.builder(Text.renderFlat(label)).width(150).action(
            DialogAction.customClick({ view, audience ->
                val player = audience as? Player ?: return@customClick
                // 콜백은 메인 스레드가 아닐 수 있다. 그리고 창이 닫히는 그 틱에 여는 화면은 클라이언트가 버린다.
                player.scheduler.runDelayed(plugin, { _ -> if (player.isOnline) onClick(player, view) }, null, 1L)
            }, Dialogs.CALLBACK_OPTIONS),
        ).build()

    private companion object {
        const val WIDTH = 240

        /** 1.0 → "1", 2.5 → "2.5". 입력창 첫 값이 "1.0" 이면 정수 칸처럼 안 보인다. */
        fun plainDecimal(value: Double): String =
            if (value == Math.floor(value) && !value.isInfinite() && kotlin.math.abs(value) < 1e15) value.toLong().toString() else value.toString()
    }
}
