package kr.inmc.core.integration

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * 커스텀 인첸트 플러그인과 다른 플러그인이 만나는 자리.
 *
 * 인첸트를 **정의하는** 쪽(inmc-enchants)이 [Provider] 를 꽂고, **쓰는** 쪽이 여기에 묻는다 —
 * 커스텀아이템은 아이템 편집 화면에서 붙일 인첸트를 고르고, 낚시는 낚싯대에 붙은 인첸트가 주는
 * 수치(`fishing.*`)를 읽는다. 어느 쪽도 서로를 컴파일 시점에 알지 않는다.
 *
 * [CustomItemHook] 과 달리 싱글톤이다. 여기는 남의 플러그인을 리플렉션으로 감쌀 일이 없고 공급처가
 * 하나뿐이라, 플러그인마다 인스턴스를 둘 이유가 없다. core 의 클래스는 모든 플러그인이 공유하므로
 * (`join-classpath: true`) 이 객체가 곧 서버 전체에 하나다.
 *
 * 공급처가 없으면(인첸트 플러그인이 없거나 아직 안 켜졌으면) 전부 "없음"으로 답한다 — 쓰는 쪽은
 * 인첸트가 없는 아이템처럼 다루면 되고, 막히거나 던지지 않는다.
 */
object CustomEnchantHook {

    /** 인첸트 하나의 겉모습. 고르는 화면이 그린다. */
    data class Info(
        val id: String,
        /** 색이 들어간 표시 이름(MiniMessage). */
        val display: String,
        val maxLevel: Int,
        /** 등급 id. 정렬·색에 쓴다. */
        val group: String,
        /** "검·도끼" 같은 사람이 읽는 설명. */
        val appliesTo: String,
        val description: List<String>,
    )

    interface Provider {
        fun all(): List<Info>

        /** 이 재질에 붙을 수 있는가. 모르는 id 면 false. */
        fun canApply(id: String, material: String): Boolean

        /** 이 아이템에 붙은 인첸트와 레벨. */
        fun levels(stack: ItemStack?): Map<String, Int>

        /**
         * 붙인다. 성공률·칸 수·붙는 아이템 제한을 **보지 않는다** — 관리자가 아이템을 정의하는 길이다.
         * 로어도 다시 그린다. 모르는 id 면 false.
         */
        fun apply(stack: ItemStack, id: String, level: Int): Boolean

        fun remove(stack: ItemStack, id: String): Boolean

        /**
         * 다른 플러그인이 이 아이템의 로어를 **통째로 새로 썼다.** 공급처가 로어에 얹어 둔 줄은 이제 없으니
         * 처음부터 다시 얹는다(자기 줄이 몇 줄 있었다는 기억은 버린다). 붙은 인첸트가 없으면 아무것도 안 한다.
         */
        fun redraw(stack: ItemStack)

        /**
         * 이 아이템에 붙은 인첸트들이 주는 **다른 플러그인용 값**을 합친 것.
         *
         * 같은 열쇠를 여러 인첸트가 주면 **숫자는 더한다**(글자는 나중 것이 이긴다). 낚싯대에 인첸트 둘이
         * 각각 `fishing.reel-power` 를 주면 합이 낚싯대에 얹혀야 한다.
         */
        fun data(stack: ItemStack?): Map<String, String>

        /**
         * 이 아이템의 인첸트 줄(MiniMessage) — **로어를 통째로 쥔 쪽**(커스텀아이템)이 자기 순서대로 끼워 넣을 때 쓴다.
         * [EnchantLines.enchants] 는 인첸트 이름·레벨(+설명), [EnchantLines.status] 는 보호됨·영혼·킬 수 같은 상태 줄.
         * 인첸트 칸 수("인첸트 칸 n/m")는 넣지 않는다 — 그건 관리 화면에서 [slots] 로 본다.
         */
        fun lines(stack: ItemStack): EnchantLines = EnchantLines()

        /** 인첸트 칸 (쓴 것, 최대). 칸 제한이 꺼져 있거나 모르면 null. 관리 화면이 보여준다. */
        fun slots(stack: ItemStack): Pair<Int, Int>? = null

        /**
         * 세트 효과([CustomItemHook.SetEffects.effects]) 편집 화면을 연다. 효과 문법을 아는 것은 인첸트뿐이라 화면도 인첸트가
         * 그린다(세트를 가진 쪽 — 커스텀아이템 — 은 트리를 들고만 있다). 고칠 때마다 [save] 로 새 트리를 돌려주고,
         * 뒤로 가면 [back] 을 부른다. 못 열면 false.
         */
        fun editEffects(viewer: Player, title: String, effects: Map<String, Any?>, save: (Map<String, Any?>) -> Unit, back: () -> Unit): Boolean = false

        /** 세트 효과를 로어 한 줄로(MiniMessage) — "공격할 때 · 맞을 때". 비었거나 모르면 빈 글자. */
        fun describeEffects(effects: Map<String, Any?>): String = ""
    }

    /** [Provider.lines] 의 답. */
    data class EnchantLines(val enchants: List<String> = emptyList(), val status: List<String> = emptyList())

    @Volatile
    private var provider: Provider? = null

    /** 인첸트 플러그인이 켜질 때 부른다. 다시 부르면 **바꾼다**(리로드가 둘을 남기지 않는다). */
    fun register(provider: Provider) {
        this.provider = provider
    }

    /** 꺼질 때. 남이 꽂은 것은 빼지 않는다. */
    fun unregister(provider: Provider) {
        if (this.provider === provider) this.provider = null
    }

    val isEnabled: Boolean get() = provider != null

    fun all(): List<Info> = provider?.all() ?: emptyList()

    fun canApply(id: String, material: String): Boolean = provider?.canApply(id, material) ?: false

    fun levels(stack: ItemStack?): Map<String, Int> = provider?.levels(stack) ?: emptyMap()

    fun apply(stack: ItemStack, id: String, level: Int): Boolean = provider?.apply(stack, id, level) ?: false

    fun remove(stack: ItemStack, id: String): Boolean = provider?.remove(stack, id) ?: false

    fun redraw(stack: ItemStack) {
        provider?.redraw(stack)
    }

    fun data(stack: ItemStack?): Map<String, String> = provider?.data(stack) ?: emptyMap()

    fun lines(stack: ItemStack): EnchantLines = provider?.lines(stack) ?: EnchantLines()

    fun slots(stack: ItemStack): Pair<Int, Int>? = provider?.slots(stack)

    fun editEffects(viewer: Player, title: String, effects: Map<String, Any?>, save: (Map<String, Any?>) -> Unit, back: () -> Unit): Boolean =
        provider?.editEffects(viewer, title, effects, save, back) ?: false

    fun describeEffects(effects: Map<String, Any?>): String = if (effects.isEmpty()) "" else provider?.describeEffects(effects).orEmpty()

    /**
     * 여러 곳의 연동 값을 합친다. 숫자면 더하고, 아니면 나중 것이 이긴다.
     *
     * 공급처가 인첸트 여럿을 합칠 때 쓰고, 쓰는 쪽이 "아이템 정의의 값 + 인첸트의 값"을 합칠 때도 쓴다.
     * 한 자리에 둬야 두 곳의 규칙이 어긋나지 않는다.
     */
    fun merge(vararg sources: Map<String, String>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (source in sources) for ((key, value) in source) {
            val before = out[key]?.trim()?.toDoubleOrNull()
            val add = value.trim().toDoubleOrNull()
            out[key] = if (before != null && add != null) format(before + add) else value
        }
        return out
    }

    private fun format(value: Double): String =
        if (value == Math.floor(value) && !value.isInfinite()) value.toLong().toString() else value.toString()
}
