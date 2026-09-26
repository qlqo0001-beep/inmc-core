package kr.inmc.core.integration

import kr.inmc.core.item.ItemRef
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 아이템 **역할** — 한 아이템이 다른 플러그인에서 맡는 일(인벤 보호권 · 상자 캡슐 · 낚싯대 · 화폐 …).
 *
 * 아이템을 한 곳(커스텀아이템)에서 관리하려고 둔 자리다(사용자 결정 2026-09-25).
 * - 역할을 **쓰는** 플러그인이 [Role] 을 내놓는다 — 이름·아이콘과 **설정 칸의 모양**([Field])까지. 커스텀아이템의 아이템 설정
 *   화면은 이걸 받아 그대로 그린다(플러그인마다 화면을 따로 만들 필요가 없다).
 * - 아이템을 **정의하는** 플러그인(커스텀아이템)이 [Store] 로 꽂혀, 아이템마다 어떤 역할을 어떤 값으로 맡는지 쥔다.
 * - 쓰는 플러그인은 [holders] 로 "이 역할을 맡은 아이템들"을 읽어 제 정의를 만든다. 제 화면에서 손에 든 것을 등록하면
 *   [adopt](커스텀아이템으로 만들기) → [assign](역할 붙이기)으로 같은 곳에 적는다 — **양쪽 어디서 등록해도 서로 보인다.**
 *
 * [Store] 가 없으면(커스텀아이템이 없거나 아직 안 켜졌으면) [active] 가 false 이고, 쓰는 플러그인은 제 파일로 돈다.
 * 꽂히고 빠질 때와 역할이 바뀔 때 [listen] 한 곳에 알린다 — 적재 순서가 보장되지 않으므로 이것이 주 경로다.
 *
 * core 의 클래스는 모든 플러그인이 공유하므로(`join-classpath: true`) 이 객체가 곧 서버 전체에 하나다.
 */
object ItemRoles {

    /** 역할의 설정 칸 하나. 값은 전부 글자로 오간다([Store] 가 YAML 에 그대로 적는다). */
    sealed interface Field {
        val key: String
        val label: String
        val default: String
        /** 이 칸을 보일지 — 다른 칸의 값에 따라(보호권 종류가 "시간형"일 때만 "보호 시간"). */
        val visible: (Map<String, String>) -> Boolean
    }

    data class Number(
        override val key: String,
        override val label: String,
        val min: Double,
        val max: Double,
        val step: Double = 1.0,
        override val default: String = trim(min),
        val integer: Boolean = true,
        override val visible: (Map<String, String>) -> Boolean = { true },
    ) : Field

    data class Toggle(
        override val key: String,
        override val label: String,
        override val default: String = "false",
        override val visible: (Map<String, String>) -> Boolean = { true },
    ) : Field

    /** 고르기. [options] 는 (값, 보이는 이름) — **열 때마다** 부른다(상자 목록처럼 살아 있는 것). */
    data class Choice(
        override val key: String,
        override val label: String,
        val options: () -> List<Pair<String, String>>,
        override val default: String = "",
        override val visible: (Map<String, String>) -> Boolean = { true },
    ) : Field

    data class Text(
        override val key: String,
        override val label: String,
        override val default: String = "",
        override val visible: (Map<String, String>) -> Boolean = { true },
    ) : Field

    /**
     * @param key `<플러그인>.<역할>` (`invkeeper.item`) — 아이템에 이 글자로 적힌다. **바꾸지 않는다.**
     * @param owner 화면에서 묶는 이름(인벤키퍼).
     */
    class Role(
        val key: String,
        val owner: String,
        val label: String,
        val icon: Material,
        val description: List<String> = emptyList(),
        val fields: List<Field> = emptyList(),
        /**
         * 이 역할의 아이템은 **역할을 내놓은 플러그인이 만든다**(값, 개수) — 인첸트의 가루처럼 제 표식(PDC)이 있어야 동작하는 것.
         * 정의하는 쪽(커스텀아이템)은 지급할 때 이것을 부른다. 겉모습은 [Holder] 의 재질·모델로 가져가면 된다(돌지 않게 — 이 안에서
         * 커스텀아이템에 아이템을 만들어 달라고 하면 다시 여기로 온다).
         */
        val factory: ((Map<String, String>, Int) -> ItemStack?)? = null,
    ) {
        fun defaults(): Map<String, String> = fields.associate { it.key to it.default }
    }

    /** 역할을 맡은 아이템 하나. [material]·[label] 은 쓰는 쪽이 재질로 거르고 목록에 이름을 보이려고. */
    data class Holder(
        val ref: ItemRef.Namespaced,
        val values: Map<String, String>,
        val material: Material = Material.STONE,
        val label: String = ref.id,
        /** 겉모습 모델(`inmc:xxx` · `ia:coin`). 비었으면 없음. */
        val itemModel: String = "",
        /** 모델 번호(custom_model_data). 0 이면 없음. */
        val modelData: Int = 0,
    ) {
        /** 이 아이템을 가리키는 [StoredItem]. 쓰는 쪽의 정의가 이것을 든다. */
        fun item(): kr.inmc.core.item.StoredItem =
            kr.inmc.core.item.StoredItem(ref, material, kr.inmc.core.item.StorageMode.REFERENCE, null, label)

        /** 옮기기 전의 아이템(있으면) — 이미 밖에 나가 있는 옛 아이템도 계속 알아보게. [LEGACY] 참조. */
        fun legacy(): kr.inmc.core.item.StoredItem? = ItemRoles.legacy(values)
    }

    /**
     * 역할 값에 숨겨 두는 **옮기기 전의 아이템**. 플러그인이 제 파일의 아이템을 커스텀아이템으로 옮길 때, 이미 플레이어가 들고 있는
     * 옛 아이템(바닐라·수제 — 커스텀아이템의 표식이 없다)도 계속 알아보도록 원래의 [kr.inmc.core.item.StoredItem] 을 글자로 적어 둔다.
     * 설정 칸([Field])이 아니라 화면에는 안 보인다.
     */
    const val LEGACY = "legacy"

    fun legacy(values: Map<String, String>): kr.inmc.core.item.StoredItem? {
        val raw = values[LEGACY]?.takeIf { it.isNotBlank() } ?: return null
        val yaml = org.bukkit.configuration.file.YamlConfiguration()
        return runCatching { yaml.loadFromString(raw); kr.inmc.core.item.StoredItem.load(yaml) }.getOrNull()
    }

    /**
     * 옮길 아이템의 견본. 바닐라 참조는 이름 없이 만들어지는데(이름은 알아보는 데만 적혀 있다) 그대로 옮기면 커스텀아이템이 "종이"가
     * 된다 — [item] 에 적힌 이름을 붙인다.
     */
    fun sample(stack: ItemStack, item: kr.inmc.core.item.StoredItem): ItemStack {
        val name = item.displayName?.takeIf { it.isNotBlank() } ?: return stack
        if (stack.itemMeta?.hasDisplayName() == true) return stack
        stack.editMeta { it.displayName(net.kyori.adventure.text.Component.text(name).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)) }
        return stack
    }

    /** [values] 에 [item] 을 옛 아이템으로 적는다. 커스텀아이템 참조면(옮길 필요가 없던 것) 적지 않는다. */
    fun withLegacy(values: Map<String, String>, item: kr.inmc.core.item.StoredItem?): Map<String, String> {
        if (item == null || item.ref is ItemRef.Namespaced) return values
        val yaml = org.bukkit.configuration.file.YamlConfiguration()
        item.save(yaml)
        return values + (LEGACY to yaml.saveToString())
    }

    // --- 옮기기(쓰는 쪽의 제 파일) ---------------------------------------------------

    /** 커스텀아이템으로 옮긴 뒤 원본 파일이 갖는 이름(`items.yml` → `items.yml.migrated`). 지우지 않는다 — 되돌릴 수 있게. */
    fun retiredFile(file: java.io.File): java.io.File = java.io.File(file.parentFile, file.name + ".migrated")

    /**
     * 이 파일은 이미 옮겼다 — 쓰는 쪽은 **다시는 이 파일을 쓰지 않는다.** 쓰면 다음 시작에 또 옮겨 같은 아이템이 둘이 된다.
     * 배포 기본값을 다시 깔지도 않는다.
     */
    fun isRetired(file: java.io.File): Boolean = retiredFile(file).exists()

    /** 원본을 [retiredFile] 로 바꾼다. 그 이름이 이미 있으면 시각을 붙인다. */
    fun retire(file: java.io.File): java.io.File {
        var target = retiredFile(file)
        if (target.exists()) target = java.io.File(file.parentFile, file.name + ".migrated-" + System.currentTimeMillis())
        if (!file.renameTo(target)) java.nio.file.Files.move(file.toPath(), target.toPath())
        return target
    }

    /** 아이템을 정의하는 쪽(커스텀아이템). */
    interface Store {
        fun holders(role: String): List<Holder>
        fun rolesOf(ref: ItemRef.Namespaced): Map<String, Map<String, String>>
        /** 역할을 붙이거나 값을 바꾼다. [values] 가 null 이면 뗀다. 모르는 아이템이면 false. */
        fun assign(ref: ItemRef.Namespaced, role: String, values: Map<String, String>?): Boolean
        /** [stack] 을 이 쪽의 아이템으로 만든다(이미 그렇다면 그 참조). [idHint] 는 새 id 의 바탕. 못 만들면 null. */
        fun adopt(stack: ItemStack, idHint: String): ItemRef.Namespaced?
        /** 이 아이템의 설정 화면을 연다([role] 이 있으면 그 역할 칸으로). */
        fun openEditor(player: Player, ref: ItemRef.Namespaced, role: String?): Boolean

        /**
         * 세트를 만든다 — 이미 그 id 가 있으면 아무것도 안 하고 false. [effects] 는 벌 수 → 다른 플러그인용 효과 트리
         * ([CustomItemHook.SetEffects.effects]), [members] 는 이 세트에 넣을 아이템. 다른 플러그인의 옛 세트를 옮길 때 쓴다.
         */
        fun defineSet(id: String, name: String, effects: Map<Int, Map<String, Any?>>, members: List<ItemRef.Namespaced>): Boolean = false
    }

    private val roles = CopyOnWriteArrayList<Role>()

    @Volatile
    private var store: Store? = null

    private class Watcher(val owner: String, val callback: (String?) -> Unit)

    private val watchers = CopyOnWriteArrayList<Watcher>()

    // --- 역할 -----------------------------------------------------------------------

    /** 역할을 내놓는다. 같은 key 는 **바꾼다**(리로드가 둘을 남기지 않는다). */
    fun register(role: Role) {
        roles.removeAll { it.key == role.key }
        roles += role
    }

    /** 이 이름(owner)의 역할과 알림을 전부 뺀다. 내려갈 때 부른다. */
    fun unregisterAll(owner: String) {
        roles.removeAll { it.owner == owner }
        watchers.removeAll { it.owner == owner }
    }

    fun roles(): List<Role> = roles.toList()

    fun role(key: String): Role? = roles.firstOrNull { it.key == key }

    // --- 정의하는 쪽 ------------------------------------------------------------------

    val active: Boolean get() = store != null

    fun attach(store: Store) {
        this.store = store
        changed(null)
    }

    fun detach(store: Store) {
        if (this.store !== store) return
        this.store = null
        changed(null)
    }

    fun holders(role: String): List<Holder> = store?.holders(role).orEmpty()

    fun rolesOf(ref: ItemRef.Namespaced): Map<String, Map<String, String>> = store?.rolesOf(ref).orEmpty()

    fun assign(ref: ItemRef.Namespaced, role: String, values: Map<String, String>?): Boolean = store?.assign(ref, role, values) ?: false

    fun adopt(stack: ItemStack, idHint: String): ItemRef.Namespaced? = store?.adopt(stack, idHint)

    fun defineSet(id: String, name: String, effects: Map<Int, Map<String, Any?>>, members: List<ItemRef.Namespaced>): Boolean =
        store?.defineSet(id, name, effects, members) ?: false

    fun openEditor(player: Player, ref: ItemRef.Namespaced, role: String? = null): Boolean = store?.openEditor(player, ref, role) ?: false

    // --- 알림 -----------------------------------------------------------------------

    /** 역할을 쓰는 플러그인이 "다시 읽어라"를 받는다. 인자는 바뀐 역할(null 이면 전부 — 정의하는 쪽이 꽂히고 빠질 때도). */
    fun listen(owner: String, callback: (String?) -> Unit) {
        watchers.removeAll { it.owner == owner }
        watchers += Watcher(owner, callback)
    }

    /** 정의하는 쪽이 부른다. 한 곳이 던져도 나머지는 받는다. */
    fun changed(role: String?) {
        for (watcher in watchers) {
            try {
                watcher.callback(role)
            } catch (t: Throwable) {
                java.util.logging.Logger.getLogger("inmc-core").log(java.util.logging.Level.WARNING, "역할 알림 실패 (${watcher.owner})", t)
            }
        }
    }

    private fun trim(value: Double): String = if (value == Math.floor(value)) value.toLong().toString() else value.toString()
}
