package kr.inmc.core.util

import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Vibration
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * 입자 — **한국어 이름**과, 색·블록 같은 자료가 있어야 하는 입자의 **기본 자료**(2026-10-14, 사용자 요청 "입자를 GUI 로 고르고 미리보기 · 영어 말고
 * 한국 이름으로"). 바닐라에는 입자 이름의 번역이 없어 표를 둔다 — 표에 없는 새 판의 입자는 영어 id 로 보인다.
 *
 * 자료가 있어야 하는 입자(먼지 = 색 · 블록 조각 = 블록 · 아이템 조각 = 아이템 …)를 자료 없이 뿌리면 서버가 던진다. 그래서 고른 입자를 뿌리는 쪽은
 * [spawn] 을 거친다 — 자료가 필요하면 기본 자료(빨간 먼지 · 돌 · 다이아몬드 …)를 넣는다. 고르는 화면이 미리보기에 쓰는 것과 같다.
 */
object Particles {

    /** 이 서버 판의 입자 전부(enum 순서). */
    fun all(): List<Particle> = Particle.values().toList()

    /** `flame` · `FLAME` · `minecraft:flame` 을 다 받는다. 모르면 null. */
    fun of(raw: String?): Particle? {
        val text = raw?.trim()?.lowercase()?.removePrefix("minecraft:")?.replace('.', '_')?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { Particle.valueOf(text.uppercase()) }.getOrNull()
    }

    /** 한국어 이름. 표에 없으면 소문자 id. */
    fun name(particle: Particle): String = NAMES[particle.name] ?: particle.name.lowercase()

    /** 적힌 이름의 한국어 이름 — 모르는 입자면 적힌 그대로. */
    fun name(raw: String?): String = of(raw)?.let(::name) ?: raw.orEmpty()

    /** 설정에 적는 id(소문자). */
    fun id(particle: Particle): String = particle.name.lowercase()

    /** 색·블록 같은 자료가 있어야 하는가 — 있으면 [spawn] 이 기본 자료를 넣는다. */
    fun needsData(particle: Particle): Boolean = particle.dataType != Void::class.java

    /** 고르는 화면의 아이콘 — 그 입자를 떠올리게 하는 재질. 표에 없으면 폭죽 탄약. */
    fun icon(particle: Particle): Material = ICONS[particle.name]?.let { Material.matchMaterial(it) } ?: Material.FIREWORK_STAR

    /**
     * 자료가 필요한 입자의 기본 자료. 자료가 필요 없거나 모르는 자료면 null.
     * [at] 은 진동·궤적처럼 목적지가 있는 입자의 목적지(그 자리 조금 위).
     */
    fun defaultData(particle: Particle, at: Location): Any? = when (particle.dataType) {
        Void::class.java -> null
        Particle.DustOptions::class.java -> Particle.DustOptions(Color.RED, 1.2f)
        Particle.DustTransition::class.java -> Particle.DustTransition(Color.RED, Color.BLUE, 1.2f)
        ItemStack::class.java -> ItemStack(Material.DIAMOND)
        BlockData::class.java -> Material.STONE.createBlockData()
        Color::class.java -> Color.WHITE
        java.lang.Float::class.java, Float::class.javaPrimitiveType -> 1.0f
        java.lang.Integer::class.java, Int::class.javaPrimitiveType -> 0
        Vibration::class.java -> Vibration(Vibration.Destination.BlockDestination(at.clone().add(0.0, 2.0, 0.0)), 20)
        Particle.Trail::class.java -> Particle.Trail(at.clone().add(0.0, 1.5, 0.0), Color.AQUA, 20)
        Particle.Spell::class.java -> Particle.Spell(Color.WHITE, 1.0f)
        else -> null
    }

    /**
     * 월드에 뿌린다(모두에게). 자료가 필요하면 [defaultData]. 못 뿌리면(모르는 자료 · 서버가 거부) false — 그 입자만 안 나온다.
     */
    fun spawn(particle: Particle, at: Location, count: Int, dx: Double, dy: Double, dz: Double, speed: Double): Boolean = runCatching {
        val world = at.world ?: return false
        val data = defaultData(particle, at)
        if (needsData(particle) && data == null) return false
        world.spawnParticle(particle, at, count, dx, dy, dz, speed, data)
        true
    }.getOrDefault(false)

    /** 한 사람에게만 보이게(고르는 화면의 미리보기). */
    fun show(player: Player, particle: Particle, at: Location, count: Int, dx: Double, dy: Double, dz: Double, speed: Double): Boolean = runCatching {
        val data = defaultData(particle, at)
        if (needsData(particle) && data == null) return false
        player.spawnParticle(particle, at, count, dx, dy, dz, speed, data)
        true
    }.getOrDefault(false)

    private val NAMES: Map<String, String> = mapOf(
        "POOF" to "펑 연기", "EXPLOSION" to "폭발", "EXPLOSION_EMITTER" to "큰 폭발", "FIREWORK" to "폭죽 불꽃", "BUBBLE" to "물방울(물속)",
        "SPLASH" to "물 튐", "FISHING" to "낚시찌 물결", "UNDERWATER" to "물속 먼지", "CRIT" to "치명타", "ENCHANTED_HIT" to "마법 타격",
        "SMOKE" to "연기", "LARGE_SMOKE" to "큰 연기", "EFFECT" to "물약 효과", "INSTANT_EFFECT" to "즉시 효과", "ENTITY_EFFECT" to "효과 소용돌이",
        "WITCH" to "마녀", "DRIPPING_WATER" to "물 떨어짐", "DRIPPING_LAVA" to "용암 떨어짐", "ANGRY_VILLAGER" to "화난 주민",
        "HAPPY_VILLAGER" to "기쁜 주민", "MYCELIUM" to "균사체", "NOTE" to "음표", "PORTAL" to "차원문", "ENCHANT" to "마법 부여 글자",
        "FLAME" to "불꽃", "LAVA" to "용암 튐", "CLOUD" to "구름", "DUST" to "먼지(색)", "ITEM_SNOWBALL" to "눈덩이 조각",
        "ITEM_SLIME" to "슬라임 조각", "HEART" to "하트", "ITEM" to "아이템 조각", "BLOCK" to "블록 조각", "RAIN" to "빗방울",
        "ELDER_GUARDIAN" to "엘더 가디언", "DRAGON_BREATH" to "드래곤의 숨결", "END_ROD" to "엔드 막대", "DAMAGE_INDICATOR" to "피해 표시",
        "SWEEP_ATTACK" to "휩쓸기", "FALLING_DUST" to "떨어지는 먼지", "TOTEM_OF_UNDYING" to "불사의 토템", "SPIT" to "라마 침",
        "SQUID_INK" to "오징어 먹물", "BUBBLE_POP" to "물방울 터짐", "CURRENT_DOWN" to "아래 물살", "BUBBLE_COLUMN_UP" to "위 물기둥",
        "NAUTILUS" to "앵무조개", "DOLPHIN" to "돌고래", "SNEEZE" to "재채기", "CAMPFIRE_COSY_SMOKE" to "모닥불 연기",
        "CAMPFIRE_SIGNAL_SMOKE" to "모닥불 신호 연기", "COMPOSTER" to "퇴비", "FLASH" to "번쩍임", "FALLING_LAVA" to "떨어지는 용암",
        "LANDING_LAVA" to "내려앉은 용암", "FALLING_WATER" to "떨어지는 물", "DRIPPING_HONEY" to "꿀 떨어짐", "FALLING_HONEY" to "떨어지는 꿀",
        "LANDING_HONEY" to "내려앉은 꿀", "FALLING_NECTAR" to "떨어지는 꽃꿀", "SOUL_FIRE_FLAME" to "영혼 불꽃", "ASH" to "재",
        "CRIMSON_SPORE" to "진홍 포자", "WARPED_SPORE" to "뒤틀린 포자", "SOUL" to "영혼", "DRIPPING_OBSIDIAN_TEAR" to "우는 흑요석 방울",
        "FALLING_OBSIDIAN_TEAR" to "떨어지는 흑요석 눈물", "LANDING_OBSIDIAN_TEAR" to "내려앉은 흑요석 눈물", "REVERSE_PORTAL" to "거꾸로 차원문",
        "WHITE_ASH" to "흰 재", "DUST_COLOR_TRANSITION" to "먼지(색 바뀜)", "VIBRATION" to "진동", "FALLING_SPORE_BLOSSOM" to "떨어지는 포자 꽃",
        "SPORE_BLOSSOM_AIR" to "포자 꽃가루", "SMALL_FLAME" to "작은 불꽃", "SNOWFLAKE" to "눈송이", "DRIPPING_DRIPSTONE_LAVA" to "종유석 용암 방울",
        "FALLING_DRIPSTONE_LAVA" to "종유석 용암 떨어짐", "DRIPPING_DRIPSTONE_WATER" to "종유석 물방울", "FALLING_DRIPSTONE_WATER" to "종유석 물 떨어짐",
        "GLOW_SQUID_INK" to "발광 오징어 먹물", "GLOW" to "발광", "WAX_ON" to "밀랍칠", "WAX_OFF" to "밀랍 벗김", "ELECTRIC_SPARK" to "전기 불꽃",
        "SCRAPE" to "긁기", "SONIC_BOOM" to "음파 폭발", "SCULK_SOUL" to "스컬크 영혼", "SCULK_CHARGE" to "스컬크 충전",
        "SCULK_CHARGE_POP" to "스컬크 충전 터짐", "SHRIEK" to "비명", "CHERRY_LEAVES" to "벚나무 잎", "PALE_OAK_LEAVES" to "창백한 참나무 잎",
        "TINTED_LEAVES" to "물든 잎", "EGG_CRACK" to "알 깨짐", "DUST_PLUME" to "먼지 기둥", "WHITE_SMOKE" to "흰 연기", "GUST" to "돌풍",
        "SMALL_GUST" to "작은 돌풍", "GUST_EMITTER_LARGE" to "큰 돌풍 폭발", "GUST_EMITTER_SMALL" to "작은 돌풍 폭발",
        "TRIAL_SPAWNER_DETECTION" to "시련 생성기 감지", "TRIAL_SPAWNER_DETECTION_OMINOUS" to "불길한 시련 생성기 감지",
        "VAULT_CONNECTION" to "금고 연결", "INFESTED" to "벌레 먹은", "ITEM_COBWEB" to "거미줄 조각", "DUST_PILLAR" to "먼지 기둥(블록)",
        "BLOCK_CRUMBLE" to "블록 부스러기", "FIREFLY" to "반딧불이", "TRAIL" to "궤적", "OMINOUS_SPAWNING" to "불길한 생성",
        "RAID_OMEN" to "습격 징조", "TRIAL_OMEN" to "시련 징조", "BLOCK_MARKER" to "블록 표시", "COPPER_FIRE_FLAME" to "구리 불꽃",
        "PAUSE_MOB_GROWTH" to "몹 성장 멈춤", "RESET_MOB_GROWTH" to "몹 성장 되돌림", "GEYSER" to "간헐천", "GEYSER_BASE" to "간헐천 바닥",
        "GEYSER_PLUME" to "간헐천 물기둥", "GEYSER_POOF" to "간헐천 김", "NOXIOUS_GAS" to "독가스", "NOXIOUS_GAS_CLOUD" to "독가스 구름",
        "SULFUR_BUBBLES" to "유황 거품", "SULFUR_CUBE_GOO" to "유황 큐브 점액",
    )

    private val ICONS: Map<String, String> = mapOf(
        "POOF" to "GRAY_DYE", "EXPLOSION" to "TNT", "EXPLOSION_EMITTER" to "TNT_MINECART", "FIREWORK" to "FIREWORK_ROCKET",
        "BUBBLE" to "WATER_BUCKET", "SPLASH" to "WATER_BUCKET", "FISHING" to "FISHING_ROD", "UNDERWATER" to "PRISMARINE_CRYSTALS",
        "CRIT" to "IRON_SWORD", "ENCHANTED_HIT" to "ENCHANTED_BOOK", "SMOKE" to "CAMPFIRE", "LARGE_SMOKE" to "CAMPFIRE",
        "EFFECT" to "POTION", "INSTANT_EFFECT" to "SPLASH_POTION", "ENTITY_EFFECT" to "LINGERING_POTION", "WITCH" to "CAULDRON",
        "DRIPPING_WATER" to "WATER_BUCKET", "DRIPPING_LAVA" to "LAVA_BUCKET", "ANGRY_VILLAGER" to "IRON_AXE", "HAPPY_VILLAGER" to "EMERALD",
        "MYCELIUM" to "MYCELIUM", "NOTE" to "NOTE_BLOCK", "PORTAL" to "OBSIDIAN", "ENCHANT" to "ENCHANTING_TABLE", "FLAME" to "BLAZE_POWDER",
        "LAVA" to "LAVA_BUCKET", "CLOUD" to "WHITE_WOOL", "DUST" to "REDSTONE", "ITEM_SNOWBALL" to "SNOWBALL", "ITEM_SLIME" to "SLIME_BALL",
        "HEART" to "RED_DYE", "ITEM" to "DIAMOND", "BLOCK" to "STONE", "RAIN" to "BLUE_DYE", "ELDER_GUARDIAN" to "PRISMARINE_SHARD",
        "DRAGON_BREATH" to "DRAGON_BREATH", "END_ROD" to "END_ROD", "DAMAGE_INDICATOR" to "REDSTONE", "SWEEP_ATTACK" to "DIAMOND_SWORD",
        "FALLING_DUST" to "SAND", "TOTEM_OF_UNDYING" to "TOTEM_OF_UNDYING", "SPIT" to "LEAD", "SQUID_INK" to "INK_SAC",
        "BUBBLE_POP" to "WATER_BUCKET", "CURRENT_DOWN" to "MAGMA_BLOCK", "BUBBLE_COLUMN_UP" to "SOUL_SAND", "NAUTILUS" to "NAUTILUS_SHELL",
        "DOLPHIN" to "COD", "SNEEZE" to "LIME_DYE", "CAMPFIRE_COSY_SMOKE" to "CAMPFIRE", "CAMPFIRE_SIGNAL_SMOKE" to "HAY_BLOCK",
        "COMPOSTER" to "COMPOSTER", "FLASH" to "GLOWSTONE_DUST", "FALLING_LAVA" to "LAVA_BUCKET", "LANDING_LAVA" to "LAVA_BUCKET",
        "FALLING_WATER" to "WATER_BUCKET", "DRIPPING_HONEY" to "HONEY_BOTTLE", "FALLING_HONEY" to "HONEY_BOTTLE", "LANDING_HONEY" to "HONEY_BLOCK",
        "FALLING_NECTAR" to "SUNFLOWER", "SOUL_FIRE_FLAME" to "SOUL_TORCH", "ASH" to "BASALT", "CRIMSON_SPORE" to "CRIMSON_FUNGUS",
        "WARPED_SPORE" to "WARPED_FUNGUS", "SOUL" to "SOUL_SAND", "DRIPPING_OBSIDIAN_TEAR" to "CRYING_OBSIDIAN",
        "FALLING_OBSIDIAN_TEAR" to "CRYING_OBSIDIAN", "LANDING_OBSIDIAN_TEAR" to "CRYING_OBSIDIAN", "REVERSE_PORTAL" to "CRYING_OBSIDIAN",
        "WHITE_ASH" to "BONE_MEAL", "DUST_COLOR_TRANSITION" to "REDSTONE", "VIBRATION" to "SCULK_SENSOR", "FALLING_SPORE_BLOSSOM" to "SPORE_BLOSSOM",
        "SPORE_BLOSSOM_AIR" to "SPORE_BLOSSOM", "SMALL_FLAME" to "TORCH", "SNOWFLAKE" to "POWDER_SNOW_BUCKET",
        "DRIPPING_DRIPSTONE_LAVA" to "POINTED_DRIPSTONE", "FALLING_DRIPSTONE_LAVA" to "POINTED_DRIPSTONE", "DRIPPING_DRIPSTONE_WATER" to "POINTED_DRIPSTONE",
        "FALLING_DRIPSTONE_WATER" to "POINTED_DRIPSTONE", "GLOW_SQUID_INK" to "GLOW_INK_SAC", "GLOW" to "GLOW_INK_SAC", "WAX_ON" to "HONEYCOMB",
        "WAX_OFF" to "HONEYCOMB", "ELECTRIC_SPARK" to "LIGHTNING_ROD", "SCRAPE" to "IRON_AXE", "SONIC_BOOM" to "ECHO_SHARD",
        "SCULK_SOUL" to "SCULK", "SCULK_CHARGE" to "SCULK_CATALYST", "SCULK_CHARGE_POP" to "SCULK_VEIN", "SHRIEK" to "SCULK_SHRIEKER",
        "CHERRY_LEAVES" to "CHERRY_LEAVES", "PALE_OAK_LEAVES" to "PALE_OAK_LEAVES", "TINTED_LEAVES" to "OAK_LEAVES", "EGG_CRACK" to "SNIFFER_EGG",
        "DUST_PLUME" to "SAND", "WHITE_SMOKE" to "WHITE_DYE", "GUST" to "WIND_CHARGE", "SMALL_GUST" to "WIND_CHARGE",
        "GUST_EMITTER_LARGE" to "BREEZE_ROD", "GUST_EMITTER_SMALL" to "BREEZE_ROD", "TRIAL_SPAWNER_DETECTION" to "TRIAL_SPAWNER",
        "TRIAL_SPAWNER_DETECTION_OMINOUS" to "OMINOUS_TRIAL_KEY", "VAULT_CONNECTION" to "VAULT", "INFESTED" to "INFESTED_STONE",
        "ITEM_COBWEB" to "COBWEB", "DUST_PILLAR" to "GRAVEL", "BLOCK_CRUMBLE" to "CREAKING_HEART", "FIREFLY" to "GLOWSTONE_DUST",
        "TRAIL" to "ARROW", "OMINOUS_SPAWNING" to "OMINOUS_BOTTLE", "RAID_OMEN" to "OMINOUS_BOTTLE", "TRIAL_OMEN" to "OMINOUS_BOTTLE",
        "BLOCK_MARKER" to "BARRIER", "COPPER_FIRE_FLAME" to "COPPER_TORCH", "PAUSE_MOB_GROWTH" to "GOLDEN_DANDELION", "RESET_MOB_GROWTH" to "GOLDEN_DANDELION",
        "GEYSER" to "WATER_BUCKET", "GEYSER_BASE" to "WATER_BUCKET", "GEYSER_PLUME" to "WATER_BUCKET", "GEYSER_POOF" to "WATER_BUCKET",
        "NOXIOUS_GAS" to "LIME_DYE", "NOXIOUS_GAS_CLOUD" to "LIME_DYE", "SULFUR_BUBBLES" to "YELLOW_DYE", "SULFUR_CUBE_GOO" to "SLIME_BALL",
    )
}
