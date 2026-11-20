package com.example.data

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.RarityCommon
import com.example.ui.theme.RarityEpic
import com.example.ui.theme.RarityLegendary
import com.example.ui.theme.RarityRare

enum class WeaponType {
    ENERGY_BLADES,
    PULSE_WRENCH_GUN,
    PLASMA_RIFLE,
    ARC_STAFF,
    CYBER_DECK,
    HEAVY_SHIELD_CANNON,
    DUAL_PISTOLS
}

enum class CardRarity(val label: String, val color: Color, val weight: Int) {
    COMUM("COMUM", RarityCommon, 50),
    RARA("RARA", RarityRare, 30),
    EPICA("ÉPICA", RarityEpic, 15),
    LENDARIA("LENDÁRIA", RarityLegendary, 6)
}

enum class CardCategory(val label: String) {
    OFENSIVA("Ofensiva"),
    ELEMENTAL("Elemental"),
    TECNOLOGIA("Tecnologia"),
    DEFESA("Defesa"),
    MOBILIDADE("Mobilidade")
}

enum class GraphicsQuality(val label: String, val rainCount: Int, val puddleReflect: Boolean, val fogDensity: Float, val glowPasses: Boolean) {
    BAIXA("Baixa", 100, false, 0.014f, false),
    MEDIA("Média", 240, true, 0.018f, true),
    ALTA("Alta", 400, true, 0.022f, true),
    ULTRA("Ultra", 600, true, 0.025f, true)
}

data class CharacterStats(
    val maxHp: Float,
    val maxShield: Float,
    val moveSpeed: Float,
    val attackDamage: Float,
    val attackCooldown: Float,
    val attackRange: Float,
    val critChance: Float,
    val armor: Float
)

data class CharacterPalette(
    val primaryHex: String,
    val secondaryHex: String,
    val visorHex: String,
    val weaponHex: String
)

data class CharacterClassDef(
    val id: String,
    val name: String,
    val role: String,
    val description: String,
    val weaponType: WeaponType,
    val unlockCost: Int,
    val unlockedByDefault: Boolean,
    val stats: CharacterStats,
    val activeSkillId: String,
    val passiveSkillId: String,
    val palette: CharacterPalette
)

data class SkinDef(
    val id: String,
    val name: String,
    val tier: CardRarity,
    val unlockType: String,
    val cost: Int,
    val description: String,
    val primaryOverride: String?,
    val secondaryOverride: String?,
    val visorOverride: String?,
    val weaponOverride: String?,
    val classId: String? = null,
    val previewDrawable: String? = null,
    val accentStyle: String? = null
)

data class AbilityDef(
    val id: String,
    val name: String,
    val description: String,
    val cooldown: Float,
    val damageMult: Float,
    val radius: Float
)

data class EnemyDef(
    val id: String,
    val name: String,
    val category: String,
    val aiState: String,
    val maxHp: Float,
    val speed: Float,
    val damage: Float,
    val attackRange: Float,
    val scale: Float,
    val xpReward: Int,
    val creditReward: Int,
    val primaryHex: String,
    val glowHex: String,
    val title: String = "",
    val phases: Int = 1,
    val weakpointHex: String = "#38BDF8"
)

data class EvolutionCardDef(
    val id: String,
    val name: String,
    val description: String,
    val rarity: CardRarity,
    val category: CardCategory,
    val effectType: String,
    val value: Float,
    val maxStacks: Int
)

data class PermanentUpgradeDef(
    val id: String,
    val name: String,
    val description: String,
    val maxLevel: Int,
    val baseCost: Int,
    val costStep: Int,
    val bonusPerLevelLabel: String
)

data class AchievementDef(
    val id: String,
    val title: String,
    val description: String,
    val rewardCredits: Int,
    val rewardSkinId: String?,
    val targetValue: Int,
    val metric: String
)

data class PetDef(
    val id: String,
    val name: String,
    val archetype: String,
    val rarity: CardRarity,
    val cost: Int,
    val primaryHex: String,
    val glowHex: String,
    val passiveDesc: String,
    val bonusType: String,
    val bonusValue: Float
)

data class RelicDef(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val colorHex: String
)

data class GameCatalog(
    val classes: List<CharacterClassDef>,
    val skins: List<SkinDef>,
    val abilities: Map<String, AbilityDef>,
    val enemies: List<EnemyDef>,
    val bosses: List<EnemyDef>,
    val cards: List<EvolutionCardDef>,
    val pets: List<PetDef> = emptyList(),
    val relics: List<RelicDef> = emptyList()
)

val DEFAULT_DECK_CARD_IDS = setOf(
    "card_01", "card_02", "card_03", "card_04",
    "card_05", "card_06", "card_07", "card_09",
    "card_11", "card_14", "card_21", "card_30"
)

data class PlayerSaveData(
    val credits: Int = 350,
    val selectedClassId: String = "berserker",
    val unlockedClassIds: Set<String> = setOf("berserker", "engenheiro", "atirador"),
    val unlockedSkinKeys: Set<String> = setOf(
        "berserker:padrao", "engenheiro:padrao", "atirador:padrao",
        "tecnomago:padrao", "hacker:padrao", "guardiao:padrao", "cacador:padrao",
        "berserker:kitbash_berserker", "engenheiro:kitbash_engenheiro", "atirador:kitbash_atirador",
        "tecnomago:kitbash_tecnomago", "hacker:kitbash_hacker", "guardiao:kitbash_guardiao",
        "cacador:kitbash_cacador"
    ),
    val equippedSkins: Map<String, String> = emptyMap(),
    val equippedDeckCardIds: Set<String> = DEFAULT_DECK_CARD_IDS,
    val upgradeLevels: Map<String, Int> = emptyMap(),
    val totalKills: Int = 0,
    val totalBossesDefeated: Int = 0,
    val totalRuns: Int = 0,
    val bestSurvivalSeconds: Int = 0,
    val highestLevelReached: Int = 1,
    val claimedAchievements: Set<String> = emptySet(),
    val graphicsQuality: GraphicsQuality = GraphicsQuality.ALTA,
    val cameraDistance: Float = 19.5f,
    val rainEnabled: Boolean = true,
    val autoAttackEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val unlockedPetIds: Set<String> = setOf("k9_cyber"),
    val equippedPetId: String? = "k9_cyber",
    val discoveredRelicIds: Set<String> = emptySet()
)

fun parseHexColor(hex: String, fallback: FloatArray = floatArrayOf(0.5f, 0.5f, 0.5f)): FloatArray {
    return try {
        val clean = hex.removePrefix("#")
        val value = clean.toLong(16)
        val r = ((value shr 16) and 0xFF) / 255f
        val g = ((value shr 8) and 0xFF) / 255f
        val b = (value and 0xFF) / 255f
        floatArrayOf(r, g, b)
    } catch (_: Exception) {
        fallback
    }
}

fun resolvePalette(charDef: CharacterClassDef, skinDef: SkinDef?): CharacterPalette {
    if (skinDef == null) return charDef.palette
    return CharacterPalette(
        primaryHex = skinDef.primaryOverride ?: charDef.palette.primaryHex,
        secondaryHex = skinDef.secondaryOverride ?: charDef.palette.secondaryHex,
        visorHex = skinDef.visorOverride ?: charDef.palette.visorHex,
        weaponHex = skinDef.weaponOverride ?: charDef.palette.weaponHex
    )
}

fun skinsForClass(allSkins: List<SkinDef>, classId: String): List<SkinDef> {
    val exclusive = allSkins.filter { it.classId == classId }
    val universal = allSkins.filter { it.classId == null }
    return exclusive + universal
}
