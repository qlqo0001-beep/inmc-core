package kr.inmc.core.gui

import kr.inmc.core.InmcHost
import kr.inmc.core.economy.Currencies
import kr.inmc.core.item.ItemRef
import kr.inmc.core.item.ItemResolver
import kr.inmc.core.item.StorageMode
import kr.inmc.core.reward.GiveMode
import kr.inmc.core.reward.RewardBundle
import kr.inmc.core.reward.RewardEntry
import kr.inmc.core.util.Numbers
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin

/**
 * core [RewardBundle] 편집 화면 (2026-10-10) — urb `RewardListMenu`·`RewardDetailMenu` 의 쓰는 법을 core 보상 위로 올렸다.
 * 킷·플레이타임 보상·투표 보상(essentials)과 업적 보상이 같은 화면을 쓴다(일곱 번째 보상 편집기를 만들지 않게).
 *
 * - **등록**: 위 다섯 줄의 빈 칸에 아이템을 올리고(끌기·Shift+클릭·숫자키) ✔ 확인. 보상에는 **사본**([ItemResolver.capture])이 들어가고
 *   올린 아이템은 **그대로 돌려준다** — 화면이 진짜 아이템을 가져가지도, 만들어 주지도 않는다. 확인 없이 닫거나 쪽·모드를 바꿔도 돌려준다.
 * - **제거 모드**: 보상 아이콘을 눌러 빼낸 뒤 ✔ 확인. 아이콘은 보여 주기용 사본이라 플레이어 손에 가지 않는다.
 * - **상세**(보상 클릭): 확률(지급 방식이 추첨이면 가중치)·수량·참조/스냅샷·참조 플러그인·돈과 화폐([CurrencyPick])·명령어·공지([showAnnounce] 가
 *   참일 때만 — 업적처럼 공지를 다른 곳이 맡는 플러그인은 숨긴다)·아이템 지급 여부.
 * - 지급 방식([GiveMode])은 좌/우클릭으로 돌리고, N개 추첨이면 Shift 로 개수를 바꾼다.
 *
 * 바뀔 때마다 [save] 를 부른다(번들은 그 자리에서 고쳐진다 — 부른 쪽은 dirty 표시만 하면 된다). 값 입력은 [DialogForm].
 *
 * @param owner 그 플러그인의 로케이터([InmcHost]) — 리로드 청소(`holder.owner === 로케이터`)·오류 보고([Menu.owner])가 이것으로 화면 주인을
 *   가리고, 입력창도 그 플러그인으로 연다. **플러그인 객체가 아니라 로케이터 그 자체**를 넘긴다 — 다른 것을 넘기면 리로드가 이 화면을 닫지 못해
 *   버려진 번들을 계속 고치게 된다.
 * @param back ◀ 뒤로. null 이면 버튼이 없다.
 */
open class RewardBundleEditor(
    final override val owner: InmcHost,
    protected val viewer: Player,
    protected val title: String,
    protected val bundle: RewardBundle,
    protected val resolver: ItemResolver,
    protected val save: (RewardBundle) -> Unit,
    protected val back: (() -> Unit)?,
) : Menu(SIZE, Text.renderFlat(title)) {

    private var page = 0
    private var removeMode = false

    /** 지금 그려진 보상 칸. 이 밖의 위쪽 칸에 놓인 아이템이 "올린 것"이다. */
    private val slotToEntry = HashMap<Int, RewardEntry>()

    /** 제거 모드에서 빼낸 보상 — 확인을 누르면 지운다. 쪽·지급 방식을 바꿔도 남는다(제거 모드를 끄거나 확정할 때만 비운다). */
    private val stagedRemovals: MutableSet<RewardEntry> = java.util.Collections.newSetFromMap(java.util.IdentityHashMap())

    /** 명령어 입력창의 설명 줄. 플레이어 이름 자리 표시는 쓰는 플러그인마다 달라(`RewardHost.placeholders`) 그쪽이 바꿔 적는다. */
    protected open val commandHints: List<String> = listOf(
        "<gray>한 줄에 하나, / 없이. 콘솔이 실행합니다.</gray>",
        "<dark_gray>플레이어 이름 자리 표시는 이 보상을 쓰는 플러그인의 규칙을 따릅니다.</dark_gray>",
    )

    /** 보상마다의 "받으면 서버 공지" 를 보이는가. 공지를 다른 곳이 전담하는 플러그인(업적)은 false — 상세의 전환 칸과 ★ 줄이 사라진다(값은 그대로 둔다). */
    protected open val showAnnounce: Boolean = true

    fun show() = open(viewer)

    private val plugin: Plugin
        get() = owner.plugin

    override fun draw() {
        // 그리기 전에 올려 둔 것을 먼저 돌려준다 — clear() 가 칸을 비우면 그 아이템은 사라진다.
        returnStaged()
        clear()
        slotToEntry.clear()
        // 지운 보상(상세의 삭제)은 빼 둔 목록에서도 뺀다.
        stagedRemovals.retainAll { entry -> bundle.entries.any { it === entry } }

        val pages = pageCount(bundle.entries.size, !removeMode)
        page = page.coerceIn(0, pages - 1)
        // Paging.slice 는 쪽을 항목 수로 자른다 — 등록용 빈 쪽(꽉 찬 다음 쪽)을 그리려면 직접 자른다.
        bundle.entries.drop(Paging.startIndex(page)).take(Paging.PER_PAGE).forEachIndexed { slot, entry ->
            slotToEntry[slot] = entry
            val icon = entryIcon(entry)
            set(slot, icon) { event ->
                if (removeMode) {
                    // 아이콘은 사본이라 손에 주지 않는다 — 칸에서 빼 보이기만 하고, 다시 누르면 되돌린다.
                    if (!stagedRemovals.add(entry)) stagedRemovals.remove(entry)
                    inventory.setItem(slot, if (entry in stagedRemovals) null else icon)
                    viewer.updateInventory()
                    return@set
                }
                event.isCancelled = true
                EntryMenu(entry).open(viewer)
            }
            if (removeMode && entry in stagedRemovals) inventory.setItem(slot, null)
        }

        for (slot in Paging.PER_PAGE until SIZE) set(slot, Icon.EDGE)
        back?.let { go -> set(SLOT_BACK, Icon.back()) { go() } }
        if (page > 0) set(SLOT_PREV, Icon.prevPage()) { page--; redraw() }
        if (page < pages - 1) set(SLOT_NEXT, Icon.nextPage()) { page++; redraw() }
        set(SLOT_MODE, modeIcon()) { event -> onModeClick(event) }
        set(SLOT_REMOVE, removeIcon()) {
            removeMode = !removeMode
            stagedRemovals.clear()
            redraw()
        }
        set(SLOT_ADD, Icon.of(Material.GOLD_INGOT, "<green>＋ 돈·명령어 보상</green>", listOf(
            "<gray>아이템 없이 돈이나 명령어만 주는 보상입니다.</gray>",
            if (showAnnounce) "<dark_gray>화폐·공지·확률은 만든 뒤 상세 화면에서.</dark_gray>" else "<dark_gray>화폐·확률은 만든 뒤 상세 화면에서.</dark_gray>",
            "", "<yellow>▶ 클릭</yellow>",
        ))) { askNonItemEntry() }
        set(SLOT_HELP, helpIcon())
        set(SLOT_CONFIRM, Icon.confirm(
            if (removeMode) "<red>✔ 제거 확정</red>" else "<green>✔ 등록 확정</green>",
            listOf(if (removeMode) "<gray>빼낸 보상을 지웁니다.</gray>" else "<gray>올린 아이템을 보상으로 등록합니다.</gray>",
                if (removeMode) "" else "<dark_gray>올린 아이템은 돌려드립니다.</dark_gray>").filter { it.isNotEmpty() },
        )) { if (removeMode) applyRemovals() else applyAdditions() }
        set(SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    override fun isSlotEditable(slot: Int): Boolean =
        !removeMode && slot in 0 until Paging.PER_PAGE && slot !in slotToEntry

    override fun acceptsShiftInsert(): Boolean = !removeMode

    override fun handleClick(event: InventoryClickEvent) {
        // 두 번 눌러 모으기는 위쪽 칸까지 훑는다 — 이 화면에서는 쓸 일이 없고, 막아 두면 사본이 손에 올 길이 하나 줄어든다.
        if (event.action == InventoryAction.COLLECT_TO_CURSOR) {
            event.isCancelled = true
            return
        }
        super.handleClick(event)
    }

    override fun onClose(event: InventoryCloseEvent) {
        returnStaged()
    }

    // --- 등록·제거 -------------------------------------------------------------------------

    private fun staged(): List<ItemStack> = (0 until Paging.PER_PAGE)
        .filter { it !in slotToEntry }
        .mapNotNull { slot -> inventory.getItem(slot)?.takeIf { !it.type.isAir } }

    /** 올린 것마다 보상 하나(사본). 아이템은 다음 그리기의 [returnStaged] 가 돌려준다. */
    private fun applyAdditions() {
        val stacks = staged()
        if (stacks.isEmpty()) {
            tell("<gray>위쪽 빈 칸에 등록할 아이템을 올린 뒤 확인을 누르세요.</gray>")
            return
        }
        for (stack in stacks) {
            bundle.entries.add(RewardEntry(item = resolver.capture(stack), chance = 100.0, minAmount = stack.amount, maxAmount = stack.amount))
        }
        save(bundle)
        tell("<green>보상 ${stacks.size}개를 등록했습니다. <gray>올린 아이템은 돌려드렸습니다.</gray></green>")
        redraw()
    }

    private fun applyRemovals() {
        val removed = bundle.entries.count { it in stagedRemovals }
        if (removed == 0) {
            tell("<gray>지울 보상을 눌러 빼낸 뒤 확인을 누르세요.</gray>")
            return
        }
        bundle.entries.removeAll { it in stagedRemovals }
        stagedRemovals.clear()
        save(bundle)
        tell("<yellow>보상 ${removed}개를 지웠습니다.</yellow>")
        redraw()
    }

    /** 위쪽 칸에 올려 두고 확정하지 않은 아이템을 돌려준다(가방이 차면 발밑에). 여러 번 불러도 한 번만 돌려준다 — 칸을 먼저 비운다. */
    private fun returnStaged() {
        for (slot in 0 until Paging.PER_PAGE) {
            if (slot in slotToEntry) continue
            val stack = inventory.getItem(slot) ?: continue
            if (stack.type.isAir) continue
            inventory.setItem(slot, null)
            viewer.inventory.addItem(stack).values.forEach { viewer.world.dropItemNaturally(viewer.location, it) }
        }
    }

    // --- 지급 방식 ---------------------------------------------------------------------------

    private fun onModeClick(event: InventoryClickEvent) {
        when {
            bundle.mode == GiveMode.ROLL_N && Editors.isPrompt(event) -> {
                ask(
                    DialogForm("<yellow>추첨 개수</yellow>")
                        .line("<gray>N개 추첨에서 중복 없이 뽑을 개수입니다.</gray>")
                        .long("count", "개수 (1 ~ $MAX_ROLL)", bundle.rollCount.toLong(), 1, MAX_ROLL.toLong()),
                    reopen = ::show,
                ) { values ->
                    values.long("count")?.let { bundle.rollCount = it.toInt() }
                    save(bundle)
                    show()
                }
                return
            }
            // 숫자키·Q·F·휠클릭은 isRightClick 이 거짓이라 cycle 이 앞으로 돌려 바로 저장한다 — 좌/우클릭만 돌린다.
            !event.isLeftClick && !event.isRightClick -> return
            bundle.mode == GiveMode.ROLL_N && event.isShiftClick -> bundle.rollCount += if (event.isLeftClick) 1 else -1
            else -> bundle.mode = Editors.cycle(event, GiveMode.entries.toList(), bundle.mode)
        }
        save(bundle)
        redraw()
    }

    private fun modeIcon(): ItemStack = Icon.of(Material.COMPARATOR, "<yellow>지급 방식: ${bundle.mode.display}</yellow>", buildList {
        add("<gray>${bundle.mode.help}</gray>")
        add("")
        addAll(Editors.optionList(GiveMode.entries.toList(), bundle.mode) { it.display })
        if (bundle.mode == GiveMode.ROLL_N) {
            add("")
            add("<gray>추첨 개수: <yellow>${bundle.rollCount}개</yellow></gray>")
            add("<yellow>▶ Shift+좌클릭 +1  /  Shift+우클릭 -1</yellow>")
            add("<dark_gray>숫자키(1~9): 개수 직접 입력</dark_gray>")
        }
        addAll(Editors.cycleHint)
    })

    private fun removeIcon(): ItemStack = Icon.of(
        if (removeMode) Material.LAVA_BUCKET else Material.HOPPER,
        if (removeMode) "<red>제거 모드 (켜짐)</red>" else "<gray>제거 모드 (꺼짐)</gray>",
        if (removeMode) listOf(
            "<gray>지울 보상을 클릭해 빼낸 뒤</gray>",
            "<gray>확인을 누르면 지워집니다.</gray>",
            "<dark_gray>다시 클릭하면 되돌립니다.</dark_gray>",
            "", "<yellow>▶ 클릭하여 등록 모드로</yellow>",
        ) else listOf(
            "<gray>빈 칸에 아이템을 올린 뒤</gray>",
            "<gray>확인을 누르면 등록됩니다.</gray>",
            "", "<yellow>▶ 클릭하여 제거 모드로</yellow>",
        ),
    )

    private fun helpIcon(): ItemStack = Icon.of(Material.PAPER, "<yellow>도움말</yellow>", buildList {
        add("<gray>등록된 보상: <white>${bundle.entries.size}개</white></gray>")
        if (bundle.mode == GiveMode.ALL && bundle.entries.isNotEmpty()) {
            val sum = bundle.entries.sumOf { it.chance }
            add("<gray>확률 합: <white>${Numbers.chance(sum)}%</white> <dark_gray>(한 번에 평균 ${Numbers.chance(sum / 100.0)}개)</dark_gray></gray>")
        }
        add("")
        add("<gray>빈 칸에 아이템을 올리고 <green>✔ 확인</green><gray>: 등록</gray></gray>")
        add("<dark_gray>보상에는 사본이 들어가고 올린 아이템은 돌려받습니다.</dark_gray>")
        add("<gray>보상 클릭: 확률·수량·명령어 등 상세 설정</gray>")
    })

    // --- 아이템 없는 보상 ---------------------------------------------------------------------

    private fun askNonItemEntry() {
        ask(
            DialogForm("<gold>돈·명령어 보상 추가</gold>")
                .line("<gray>아이템 없이 돈이나 명령어만 주는 보상입니다. 둘 중 하나는 넣어 주세요.</gray>")
                .apply { commandHints.forEach { line(it) } }
                .decimal("money", "금액 (비우면 없음)", null, 0.0, MAX_MONEY, optional = true)
                .text("commands", "명령어 (한 줄에 하나)", "", 2048, multiline = true),
            reopen = ::show,
        ) { values ->
            val money = values.decimal("money") ?: 0.0
            val commands = commandsOf(values.text("commands"))
            if (money <= 0.0 && commands.isEmpty()) {
                tell("<gray>넣은 것이 없어 만들지 않았습니다.</gray>")
                show()
                return@ask
            }
            val entry = RewardEntry(chance = 100.0, money = money, commands = commands, giveItem = false)
            bundle.entries.add(entry)
            save(bundle)
            // 화폐·확률(showAnnounce 면 공지도)을 바로 고를 수 있게 상세로.
            EntryMenu(entry).open(viewer)
        }
    }

    // --- 공용 ---------------------------------------------------------------------------------

    private fun redraw() {
        refresh()
        viewer.updateInventory()
    }

    private fun tell(message: String) = viewer.sendMessage(Text.render(message, null, viewer))

    /** 입력창. 취소(Esc 포함)면 [reopen], 확인이면 [onSubmit] — 둘 다 메인 스레드. 열면서 이 화면이 닫혀 올린 아이템은 돌아간다. */
    private fun ask(form: DialogForm, reopen: () -> Unit, onSubmit: (DialogForm.Values) -> Unit) {
        form.show(plugin, viewer, onCancel = { reopen() }) { _, values -> onSubmit(values) }
    }

    private fun chanceWord(): String = if (bundle.mode == GiveMode.ALL) "확률" else "가중치"

    private fun amountText(entry: RewardEntry): String =
        if (entry.minAmount == entry.maxAmount) "${entry.minAmount}개" else "${entry.minAmount}~${entry.maxAmount}개"

    private fun storageText(mode: StorageMode): String =
        if (mode == StorageMode.REFERENCE) "동적·참조 (원본을 고치면 따라감)" else "스냅샷 (등록한 그대로)"

    private fun currencyName(id: String): String =
        if (id.isBlank()) "기본" else Currencies.get(id)?.name ?: id

    private fun plainIcon(entry: RewardEntry): Material = when {
        entry.money > 0.0 -> Material.GOLD_INGOT
        entry.commands.isNotEmpty() -> Material.COMMAND_BLOCK
        else -> Material.PAPER
    }

    private fun entryLore(entry: RewardEntry): MutableList<String> {
        val item = entry.item
        val lore = mutableListOf("<gray>${chanceWord()}: <yellow>${Numbers.chance(entry.chance)}%</yellow></gray>")
        if (entry.chance <= 0.0) lore += "<red>0% — 꺼진 보상 (지우지 않고 쉬게 할 때)</red>"
        if (item != null) {
            if (entry.giveItem) lore += "<gray>수량: <white>${amountText(entry)}</white></gray>"
            else lore += "<dark_gray>아이템 지급 없음 (아이콘만)</dark_gray>"
            lore += "<gray>저장 방식: <white>${storageText(item.mode)}</white></gray>"
            lore += "<dark_gray>${item.ref.serialize()}</dark_gray>"
        }
        if (entry.money > 0.0) lore += "<gold>돈: ${entry.moneyLabel()}</gold>"
        if (entry.commands.isNotEmpty()) lore += "<aqua>실행 명령어 ${entry.commands.size}개</aqua>"
        if (entry.announce && showAnnounce) lore += "<gold>★ 받으면 서버 공지</gold>"
        return lore
    }

    private fun entryIcon(entry: RewardEntry): ItemStack {
        val lore = entryLore(entry)
        val item = entry.item
        val resolved = item?.let { resolver.icon(it) }
        resolved?.let { lore.addAll(it.notes()) }
        lore += ""
        lore += if (removeMode) "<red>▶ 클릭하여 빼낸 뒤 확인</red>" else "<yellow>▶ 클릭: 상세 설정</yellow>"
        return if (resolved != null) Icon.annotate(resolved.stack, lore = lore)
        else Icon.of(plainIcon(entry), "<white>${entry.label()}</white>", lore)
    }

    // --- 상세 화면 ---------------------------------------------------------------------------

    /** 보상 하나의 설정. 같은 주인([owner])으로 열려 리로드 청소에 함께 걸린다. */
    private inner class EntryMenu(private val entry: RewardEntry) : Menu(DETAIL_SIZE, Text.renderFlat("<dark_gray>보상 상세 설정</dark_gray>")) {

        override val owner: Any get() = this@RewardBundleEditor.owner

        override fun draw() {
            clear()
            set(D_PREVIEW, preview()) { event -> setItemFromCursor(event) }

            for ((slot, delta) in CHANCE_STEPS) {
                set(slot, Icon.of(Material.LIGHT_BLUE_DYE, "<aqua>${chanceWord()} ±${Numbers.chance(delta)}%</aqua>", listOf(
                    "<gray>현재: <yellow>${Numbers.chance(entry.chance)}%</yellow></gray>",
                    "", "<yellow>▶ 좌클릭: +${Numbers.chance(delta)}</yellow>", "<yellow>▶ 우클릭: -${Numbers.chance(delta)}</yellow>",
                ))) { event ->
                    // 숫자키 등은 좌클릭이 아니라 빼기가 된다 — 직접 입력으로.
                    if (Editors.isPrompt(event)) askChance() else if (event.isLeftClick || event.isRightClick) {
                        entry.chance = entry.chance + if (event.isLeftClick) delta else -delta
                        changed()
                    }
                }
            }
            set(D_CHANCE_INPUT, Icon.of(Material.WRITABLE_BOOK, "<yellow>${chanceWord()} 직접 입력</yellow>", buildList {
                add("<gray>현재: <yellow>${Numbers.chance(entry.chance)}%</yellow></gray>")
                add("<dark_gray>0 ~ 100, 소수점 두 자리. 0 은 꺼짐(지우지 않고 쉬게).</dark_gray>")
                if (bundle.mode != GiveMode.ALL) add("<dark_gray>추첨에서는 다른 보상과 견준 가중치입니다.</dark_gray>")
                add("")
                add("<yellow>▶ 클릭</yellow>")
            })) { askChance() }

            set(D_MONEY, Icon.of(Material.GOLD_INGOT, "<yellow>돈</yellow>", listOf(
                "<gray>현재: <white>${if (entry.money > 0.0) entry.moneyLabel() else "없음"}</white></gray>",
                "", "<yellow>▶ 클릭: 금액 입력 (0 = 없음)</yellow>",
            ))) {
                ask(DialogForm("<yellow>돈</yellow>").decimal("money", "금액 (0 = 없음)", entry.money, 0.0, MAX_MONEY), reopen = ::show) { values ->
                    entry.money = values.decimal("money")?.coerceAtLeast(0.0) ?: entry.money
                    save(bundle)
                    show()
                }
            }
            // 화폐가 하나뿐이면 묻지 않는다(EconomyHook.multiCurrency 와 같은 판단). 이미 정해 둔 것이 있으면 되돌릴 수 있게 보인다.
            if (Currencies.all().size > 1 || entry.currency.isNotBlank()) {
                set(D_CURRENCY, Icon.of(Material.GOLD_NUGGET, "<yellow>화폐</yellow>", listOf(
                    "<gray>화폐: <white>${currencyName(entry.currency)}</white></gray>",
                ) + Editors.pickHint)) {
                    CurrencyPick.open(owner, viewer, entry.currency, back = ::show) { id ->
                        entry.currency = id
                        save(bundle)
                        show()
                    }
                }
            }

            val item = entry.item
            if (item != null) {
                referenceTile(item)
                set(D_MIN, Editors.intIcon(Material.IRON_NUGGET, "<yellow>최소 수량</yellow>", entry.minAmount, "개")) { event ->
                    if (Editors.isPrompt(event)) askAmount(min = true) else { entry.minAmount += Editors.step(event, 1); changed() }
                }
                set(D_MAX, Editors.intIcon(Material.GOLD_NUGGET, "<yellow>최대 수량</yellow>", entry.maxAmount, "개")) { event ->
                    if (Editors.isPrompt(event)) askAmount(min = false) else { entry.maxAmount += Editors.step(event, 1); changed() }
                }
                set(D_GIVE_ITEM, Icon.of(Icon.toggleMaterial(entry.giveItem), "<yellow>아이템 지급</yellow>", listOf(
                    "<gray>현재: </gray>" + Icon.toggle(entry.giveItem),
                    "<dark_gray>끄면 아이템은 아이콘으로만 쓰이고</dark_gray>",
                    "<dark_gray>돈·명령어만 지급됩니다.</dark_gray>",
                    "", "<yellow>▶ 클릭하여 전환</yellow>",
                ))) { entry.giveItem = !entry.giveItem; changed() }
                storageTile(item)
            }

            if (showAnnounce) set(D_ANNOUNCE, Icon.of(if (entry.announce) Material.BELL else Material.GRAY_DYE, "<yellow>받으면 서버 공지</yellow>", listOf(
                "<gray>현재: </gray>" + Icon.toggle(entry.announce),
                "<dark_gray>쓰는 플러그인이 공지를 켜 두었을 때만 나갑니다.</dark_gray>",
                "", "<yellow>▶ 클릭하여 전환</yellow>",
            ))) { entry.announce = !entry.announce; changed() }

            set(D_COMMANDS, Icon.of(Material.COMMAND_BLOCK, "<yellow>실행 명령어</yellow>", buildList {
                if (entry.commands.isEmpty()) add("<gray>등록된 명령어가 없습니다.</gray>")
                else entry.commands.forEach { add("<dark_gray>/$it</dark_gray>") }
                add("")
                add("<yellow>▶ 클릭: 명령어 편집</yellow>")
            })) { askCommands() }

            set(D_BACK, Icon.back()) { this@RewardBundleEditor.show() }
            set(D_DELETE, Icon.of(Material.TNT, "<red>이 보상 삭제</red>", listOf("<gray>목록에서 지웁니다.</gray>", "", "<red>▶ Shift+클릭</red>"))) { event ->
                if (!event.isShiftClick) return@set
                bundle.entries.remove(entry)
                save(bundle)
                this@RewardBundleEditor.show()
            }
            fillEmpty(Icon.EDGE)
        }

        fun show() = open(viewer)

        private fun changed() {
            save(bundle)
            refresh()
            viewer.updateInventory()
        }

        private fun preview(): ItemStack {
            val lore = entryLore(entry)
            val resolved = entry.item?.let { resolver.icon(it) }
            resolved?.let { lore.addAll(it.notes()) }
            lore += ""
            lore += "<yellow>▶ 커서에 아이템을 들고 클릭: 이 아이템으로 바꾸기</yellow>"
            lore += "<dark_gray>들고 있던 아이템은 그대로 남습니다(사본을 씁니다).</dark_gray>"
            return if (resolved != null) Icon.annotate(resolved.stack, lore = lore)
            else Icon.of(plainIcon(entry), "<white>${entry.label()}</white>", lore)
        }

        /** 커서의 아이템을 사본으로 — 클릭은 취소돼 커서의 아이템은 움직이지 않는다. */
        private fun setItemFromCursor(event: InventoryClickEvent) {
            event.isCancelled = true
            val cursor = event.view.cursor
            if (cursor.type.isAir) return
            entry.item = resolver.capture(cursor)
            changed()
        }

        private fun referenceTile(item: kr.inmc.core.item.StoredItem) {
            val candidates = resolver.candidates(item)
            set(D_REF, Icon.of(if (candidates.size > 1) Material.SPYGLASS else Material.GRAY_DYE, "<yellow>참조 플러그인</yellow>", buildList {
                add("<gray>현재: <white>${refLabel(item.ref)}</white></gray>")
                add("<dark_gray>${item.ref.serialize()}</dark_gray>")
                add("")
                when {
                    candidates.size <= 1 -> add("<gray>이 아이템을 알아본 플러그인이 하나뿐입니다.</gray>")
                    else -> {
                        add("<gray>이 아이템을 <white>${candidates.size}개</white> 플러그인이 알아봅니다.</gray>")
                        candidates.forEach { candidate ->
                            val marker = if (candidate == item.ref) "<green>▶</green>" else "<dark_gray>·</dark_gray>"
                            add("$marker <dark_gray>${refLabel(candidate)} - ${candidate.serialize()}</dark_gray>")
                        }
                        add("")
                        add("<red>⚠ 바꾸면 그 플러그인의 정의로만 지급됩니다.</red>")
                        add("<yellow>▶ 좌클릭: 다음 후보  /  우클릭: 이전 후보</yellow>")
                    }
                }
            })) { event ->
                if (candidates.size <= 1 || (!event.isLeftClick && !event.isRightClick)) return@set
                val current = entry.item ?: return@set
                entry.item = current.copy(ref = Editors.cycle(event, candidates, current.ref))
                changed()
            }
        }

        private fun storageTile(item: kr.inmc.core.item.StoredItem) {
            val reference = item.mode == StorageMode.REFERENCE
            val canReference = item.ref != ItemRef.None
            set(D_STORAGE, Icon.of(
                if (reference) Material.RECOVERY_COMPASS else Material.BUNDLE,
                if (reference) "<green>저장 방식: 동적·참조 (자동 갱신)</green>" else "<yellow>저장 방식: 스냅샷 (고정)</yellow>",
                buildList {
                    if (reference) {
                        add("<gray>지급할 때마다 원본 정의대로 새로 만듭니다.</gray>")
                        add("<gray>원본을 고치면 그대로 따라갑니다.</gray>")
                    } else {
                        add("<gray>등록 당시의 아이템을 그대로 지급합니다.</gray>")
                        add("<gray>원본 플러그인이 사라져도 동작합니다.</gray>")
                    }
                    add("")
                    add(if (canReference) "<yellow>▶ 클릭하여 전환</yellow>" else "<red>이 아이템은 참조로 표현할 수 없습니다.</red>")
                },
            )) {
                if (!canReference) return@set
                val current = entry.item ?: return@set
                entry.item = current.withMode(current.mode.toggle())
                changed()
            }
        }

        private fun askChance() {
            ask(DialogForm("<yellow>${chanceWord()}</yellow>").decimal("chance", "${chanceWord()} (%)", entry.chance, 0.0, 100.0), reopen = ::show) { values ->
                values.decimal("chance")?.let { entry.chance = it }
                save(bundle)
                show()
            }
        }

        private fun askAmount(min: Boolean) {
            val label = if (min) "최소 수량" else "최대 수량"
            val current = if (min) entry.minAmount else entry.maxAmount
            ask(DialogForm("<yellow>$label</yellow>").long("amount", "$label (1 ~ ${RewardEntry.MAX_AMOUNT})", current.toLong(), 1, RewardEntry.MAX_AMOUNT.toLong()), reopen = ::show) { values ->
                values.long("amount")?.toInt()?.let { if (min) entry.minAmount = it else entry.maxAmount = it }
                save(bundle)
                show()
            }
        }

        private fun askCommands() {
            ask(
                DialogForm("<yellow>실행 명령어</yellow>")
                    .apply { commandHints.forEach { line(it) } }
                    .line("<dark_gray>모두 지우면 명령어가 없어집니다.</dark_gray>")
                    .text("commands", "명령어 (한 줄에 하나)", entry.commands.joinToString("\n"), 2048, multiline = true),
                reopen = ::show,
            ) { values ->
                entry.commands = commandsOf(values.text("commands"))
                save(bundle)
                show()
            }
        }
    }

    /** 참조가 어느 플러그인의 것인지 — 관리자가 쓰는 말로(urb 와 같다). */
    private fun refLabel(ref: ItemRef): String = when (ref) {
        is ItemRef.MMOItems -> "MMOItems"
        is ItemRef.Namespaced -> when (ref.namespace) {
            "itemsadder" -> "ItemsAdder"
            "nexo" -> "Nexo"
            "oraxen" -> "Oraxen"
            "ecoitems" -> "EcoItems"
            else -> ref.namespace
        }
        is ItemRef.Vanilla -> "바닐라"
        is ItemRef.None -> "스냅샷 전용"
    }

    companion object {
        const val SIZE = 54

        // 목록 화면 아래 줄 — 뒤로·쪽·닫기는 Paging 과 같은 자리.
        const val SLOT_BACK = Paging.SLOT_BACK
        const val SLOT_PREV = Paging.SLOT_PREV
        const val SLOT_NEXT = Paging.SLOT_NEXT
        const val SLOT_MODE = 48
        const val SLOT_REMOVE = 49
        const val SLOT_ADD = 50
        const val SLOT_HELP = 51
        const val SLOT_CONFIRM = 52
        const val SLOT_CLOSE = Paging.SLOT_CLOSE

        // 상세 화면(45칸) — urb RewardDetailMenu 와 같은 배치를 따른다.
        const val DETAIL_SIZE = 45
        const val D_PREVIEW = 4
        const val D_CHANCE_STEP_1 = 19
        const val D_CHANCE_STEP_2 = 20
        const val D_CHANCE_STEP_3 = 21
        const val D_CHANCE_STEP_4 = 22
        const val D_CHANCE_INPUT = 23
        const val D_MONEY = 24
        const val D_CURRENCY = 25
        const val D_REF = 28
        const val D_MIN = 29
        const val D_MAX = 30
        const val D_GIVE_ITEM = 31
        const val D_STORAGE = 32
        const val D_ANNOUNCE = 33
        const val D_COMMANDS = 34
        const val D_BACK = 36
        const val D_DELETE = 44

        private val CHANCE_STEPS = listOf(D_CHANCE_STEP_1 to 0.01, D_CHANCE_STEP_2 to 0.1, D_CHANCE_STEP_3 to 1.0, D_CHANCE_STEP_4 to 10.0)

        const val MAX_ROLL = 64
        private const val MAX_MONEY = 1_000_000_000_000.0

        /** 쪽 수. 등록 모드에서는 맨 뒤 쪽이 꽉 찼으면 빈 쪽을 하나 더 둔다 — 올릴 칸이 있어야 한다. */
        @JvmStatic
        fun pageCount(entries: Int, addMode: Boolean): Int = Paging.pageCount(if (addMode) entries + 1 else entries)

        /** 입력창의 명령어 글 → 목록. 줄마다 하나, 앞의 `/` 와 빈 줄은 뺀다. */
        @JvmStatic
        fun commandsOf(raw: String): MutableList<String> =
            raw.split('\n').map { it.trim().removePrefix("/").trim() }.filter { it.isNotEmpty() }.toMutableList()
    }
}
