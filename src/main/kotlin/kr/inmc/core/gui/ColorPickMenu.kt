package kr.inmc.core.gui

import kr.inmc.core.input.ChatPrompt
import kr.inmc.core.util.Text
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.LeatherArmorMeta

/**
 * 색 고르기 — 색표(타이틀포지 칭호 꾸미기의 색표를 늘린 것) + **RGB 를 한 칸씩** + `#RRGGBB` 직접 입력(2026-10-14, 사용자 요청
 * "타이틀포지의 색 고르는 창을 색만 정하는 곳에서 쓰고, RGB 로 세세하게"). 글자로 색 이름·hex 를 치던 곳을 대신한다.
 *
 * 맨 위 미리보기는 그 색으로 물들인 가죽 갑옷과 그 색 글자. 색표를 누르면 그 색으로 시작하고, R·G·B 칸으로 다듬은 뒤 [✔ 이 색으로].
 * R·G·B 칸: 좌클릭 +1 · 우클릭 -1 · Shift 로 ±16 · 숫자키 = 직접 입력(0~255).
 *
 * @param prompts 숫자·hex 직접 입력에 쓴다. null 이면 그 칸이 없다(클릭으로만).
 * @param current 지금 색(`#RRGGBB` · `<#RRGGBB>` · 색 이름). 못 알아보면 흰색에서 시작.
 * @param allowNone true 면 [색 없음] 칸 — 누르면 [onPick] 이 null.
 * @param onPick 고른 색(`#RRGGBB`, 대문자) — 화면을 어떻게 할지는 부르는 쪽이 정한다.
 */
class ColorPickMenu(
    override val owner: Any?,
    private val viewer: Player,
    title: String = "색 고르기",
    private val prompts: ChatPrompt? = null,
    current: String? = null,
    private val allowNone: Boolean = false,
    private val back: (() -> Unit)? = null,
    private val onPick: (String?) -> Unit,
) : Menu(54, Text.renderFlat("<dark_gray>$title</dark_gray>")) {

    private var red: Int
    private var green: Int
    private var blue: Int

    init {
        val start = parse(current) ?: 0xFFFFFF
        red = start shr 16 and 0xFF
        green = start shr 8 and 0xFF
        blue = start and 0xFF
    }

    fun show() = open(viewer)

    private val hex: String get() = "#%02X%02X%02X".format(red, green, blue)

    override fun draw() {
        clear()
        set(SLOT_PREVIEW, preview())
        for ((index, swatch) in PALETTE.withIndex()) {
            val chosen = swatch.hex.equals(hex, ignoreCase = true)
            val icon = Icon.of(swatch.material, "<color:" + swatch.hex + ">■ " + swatch.name + "</color>", listOf(
                "<dark_gray>" + swatch.hex + "</dark_gray>", "", "<yellow>▶ 클릭: 이 색으로 시작</yellow>",
            )).also { if (chosen) it.editMeta { meta -> meta.setEnchantmentGlintOverride(true) } }
            set(PALETTE_FIRST + index, icon) {
                val rgb = parse(swatch.hex)!!
                red = rgb shr 16 and 0xFF
                green = rgb shr 8 and 0xFF
                blue = rgb and 0xFF
                refresh()
            }
        }
        channel(SLOT_RED, Material.RED_DYE, "빨강(R)", red) { red = it }
        channel(SLOT_GREEN, Material.LIME_DYE, "초록(G)", green) { green = it }
        channel(SLOT_BLUE, Material.BLUE_DYE, "파랑(B)", blue) { blue = it }
        set(SLOT_DARKER, Icon.of(Material.BLACK_DYE, "<gray>어둡게</gray>", listOf("<gray>R·G·B 를 함께 10% 낮춥니다.</gray>", "", "<yellow>▶ 클릭</yellow>"))) {
            scale(0.9)
        }
        set(SLOT_LIGHTER, Icon.of(Material.WHITE_DYE, "<white>밝게</white>", listOf("<gray>흰색 쪽으로 10% 섞습니다.</gray>", "", "<yellow>▶ 클릭</yellow>"))) {
            red += ((255 - red) * 0.1).toInt().coerceAtLeast(1)
            green += ((255 - green) * 0.1).toInt().coerceAtLeast(1)
            blue += ((255 - blue) * 0.1).toInt().coerceAtLeast(1)
            clamp()
            refresh()
        }
        if (prompts != null) {
            set(SLOT_HEX, Icon.of(Material.NAME_TAG, "<yellow>직접 입력</yellow>", listOf(
                "<gray>지금: <white>" + hex + "</white></gray>", "<gray>예: <white>#FFAA00</white> · <white>255,170,0</white></gray>", "", "<yellow>▶ 클릭: 입력</yellow>",
            ))) { askHex() }
        }
        if (allowNone) {
            set(SLOT_NONE, Icon.of(Material.BARRIER, "<red>색 없음</red>", listOf("<gray>색을 지웁니다(기본 모습).</gray>"))) { onPick(null) }
        }
        set(SLOT_CONFIRM, Icon.of(Material.LIME_CONCRETE, "<green>✔ 이 색으로 <color:" + hex + ">■■■</color></green>", listOf("<gray>" + hex + "</gray>"))) { onPick(hex) }
        back?.let { go -> set(Paging.SLOT_BACK, Icon.back()) { go() } }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
        fillEmpty(Icon.FILLER)
    }

    private fun preview(): ItemStack {
        val stack = Icon.of(Material.LEATHER_CHESTPLATE, "<color:" + hex + ">가나다 ABC ■■■</color>", listOf(
            "<gray>" + hex + " · R <red>" + red + "</red> G <green>" + green + "</green> B <blue>" + blue + "</blue></gray>",
            "<gray>색표에서 고른 뒤 아래 R·G·B 로 다듬습니다.</gray>",
        ))
        stack.editMeta(LeatherArmorMeta::class.java) { it.setColor(Color.fromRGB(red, green, blue)) }
        return stack
    }

    /** R·G·B 한 칸 — 좌 +1 · 우 -1 · Shift ×16 · 숫자키 직접 입력. */
    private fun channel(slot: Int, material: Material, label: String, value: Int, change: (Int) -> Unit) {
        val bar = "█".repeat(value * 10 / 255) + "<dark_gray>" + "█".repeat(10 - value * 10 / 255) + "</dark_gray>"
        set(slot, Icon.of(material, "<yellow>" + label + ": <white>" + value + "</white></yellow>", listOf(
            "<white>" + bar + "</white>",
            "",
            "<yellow>▶ 좌클릭 +1 · 우클릭 -1 · Shift 로 ±16</yellow>",
        ) + (if (prompts != null) listOf("<dark_gray>숫자키(1~9): 직접 입력(0~255)</dark_gray>") else emptyList()))) { event ->
            if (prompts != null && Editors.isPrompt(event)) {
                Editors.promptInt(prompts, viewer, label, 0, 255, reopen = { open(viewer) }) { change(it) }
                return@set
            }
            change((value + step(event)).coerceIn(0, 255))
            refresh()
        }
    }

    private fun step(event: InventoryClickEvent): Int {
        val size = if (event.isShiftClick) 16 else 1
        return if (event.isRightClick) -size else size
    }

    private fun scale(ratio: Double) {
        red = (red * ratio).toInt()
        green = (green * ratio).toInt()
        blue = (blue * ratio).toInt()
        clamp()
        refresh()
    }

    private fun clamp() {
        red = red.coerceIn(0, 255)
        green = green.coerceIn(0, 255)
        blue = blue.coerceIn(0, 255)
    }

    private fun askHex() {
        val chat = prompts ?: return
        Editors.promptText(chat, viewer, "색", listOf("<gray><white>#RRGGBB</white> · <white>R,G,B</white> · 색 이름(<white>gold</white> …)</gray>"), reopen = { open(viewer) }) { raw ->
            val rgb = parse(raw)
            if (rgb == null) {
                viewer.sendMessage(Text.render("<red>알아볼 수 없는 색입니다: " + raw.replace("<", "\\<") + "</red>"))
                return@promptText
            }
            red = rgb shr 16 and 0xFF
            green = rgb shr 8 and 0xFF
            blue = rgb and 0xFF
        }
    }

    private class Swatch(val name: String, val hex: String, val material: Material)

    companion object {
        const val SLOT_PREVIEW = 4
        const val PALETTE_FIRST = 9
        const val SLOT_DARKER = 36
        const val SLOT_RED = 37
        const val SLOT_GREEN = 39
        const val SLOT_BLUE = 41
        const val SLOT_HEX = 43
        const val SLOT_LIGHTER = 44
        const val SLOT_NONE = 47
        const val SLOT_CONFIRM = 49

        /**
         * `#RRGGBB` · `<#RRGGBB>` · `RRGGBB` · `r,g,b` · 바닐라 색 이름(`gold` · `<gold>`)을 RGB 정수로. 모르면 null.
         * 서버 없이 도는 순수 계산이다(색 이름은 Adventure 표).
         */
        fun parse(raw: String?): Int? {
            val text = raw?.trim()?.removePrefix("<")?.removeSuffix(">")?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
            Regex("^#?([0-9a-f]{6})$").matchEntire(text)?.let { return it.groupValues[1].toInt(16) }
            Regex("^(\\d{1,3})\\s*[, ]\\s*(\\d{1,3})\\s*[, ]\\s*(\\d{1,3})$").matchEntire(text)?.let { match ->
                val (r, g, b) = match.destructured.toList().map { it.toInt() }
                if (listOf(r, g, b).any { it > 255 }) return null
                return (r shl 16) or (g shl 8) or b
            }
            return net.kyori.adventure.text.format.NamedTextColor.NAMES.value(text)?.value()
        }

        /** 색표 27칸 — 타이틀포지 칭호 꾸미기의 18색 + 검정 · 주황 · 갈색 · 연하늘 · 라벤더 · 진분홍 · 올리브 · 청자 · 크림. */
        private val PALETTE = listOf(
            Swatch("흰색", "#FFFFFF", Material.WHITE_CONCRETE), Swatch("회색", "#AAAAAA", Material.LIGHT_GRAY_CONCRETE),
            Swatch("짙은 회색", "#555555", Material.GRAY_CONCRETE), Swatch("검정", "#000000", Material.BLACK_CONCRETE),
            Swatch("빨강", "#FF5555", Material.RED_CONCRETE), Swatch("진홍", "#AA0000", Material.NETHER_WART_BLOCK),
            Swatch("진분홍", "#FF1493", Material.MAGENTA_TERRACOTTA), Swatch("분홍", "#FFB6C1", Material.PINK_CONCRETE),
            Swatch("살구", "#FFCBA4", Material.TERRACOTTA), Swatch("주황", "#FF8C00", Material.ORANGE_TERRACOTTA),
            Swatch("금색", "#FFAA00", Material.ORANGE_CONCRETE), Swatch("노랑", "#FFFF55", Material.YELLOW_CONCRETE),
            Swatch("크림", "#FFFDD0", Material.WHITE_TERRACOTTA), Swatch("갈색", "#8B5A2B", Material.BROWN_CONCRETE),
            Swatch("올리브", "#808000", Material.GREEN_TERRACOTTA), Swatch("연두", "#55FF55", Material.LIME_CONCRETE),
            Swatch("초록", "#00AA00", Material.GREEN_CONCRETE), Swatch("민트", "#98FFC8", Material.PRISMARINE_BRICKS),
            Swatch("청자", "#20B2AA", Material.WARPED_PLANKS), Swatch("하늘", "#55FFFF", Material.LIGHT_BLUE_CONCRETE),
            Swatch("연하늘", "#ADD8E6", Material.LIGHT_BLUE_TERRACOTTA), Swatch("청록", "#00AAAA", Material.CYAN_CONCRETE),
            Swatch("파랑", "#5555FF", Material.BLUE_CONCRETE), Swatch("남색", "#0000AA", Material.LAPIS_BLOCK),
            Swatch("라벤더", "#B39DDB", Material.PURPLE_TERRACOTTA), Swatch("분홍보라", "#FF55FF", Material.MAGENTA_CONCRETE),
            Swatch("보라", "#AA00AA", Material.PURPLE_CONCRETE),
        )
    }
}
