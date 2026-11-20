package com.example.engine3d

import com.example.audio.SoundEffect
import com.example.data.AbilityDef
import com.example.data.CharacterClassDef
import com.example.data.CharacterPalette
import com.example.data.EnemyDef
import com.example.data.EvolutionCardDef
import com.example.data.GameCatalog
import com.example.data.GraphicsQuality
import com.example.data.PetDef
import com.example.data.PlayerSaveData
import com.example.data.WeaponType
import com.example.data.parseHexColor
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class RunStatus {
    PLAYING,
    PAUSED,
    LEVEL_UP,
    VICTORY,
    DEFEAT
}

enum class WeatherPhase(val label: String, val rainMultiplier: Float, val fogMultiplier: Float) {
    CHUVA("Chuva Forte", 1.0f, 1.0f),
    NEBLINA("Neblina Densa", 0.15f, 2.35f),
    TEMPESTADE("Tempestade", 1.4f, 1.65f),
    GAROA("Garoa Noturna", 0.45f, 1.15f)
}

data class ActiveEnemy(
    val uid: Int,
    val def: EnemyDef,
    val isBoss: Boolean,
    val pos: Vec3,
    var yawDeg: Float,
    var hp: Float,
    var maxHp: Float,
    var attackTimer: Float = 1.0f,
    var specialTimer: Float = 4.0f,
    var hitFlash: Float = 0f,
    var animTime: Float = 0f,
    var burnTimer: Float = 0f,
    var burnDps: Float = 0f,
    var corrosiveTimer: Float = 0f,
    var cryoTimer: Float = 0f,
    var stunTimer: Float = 0f,
    var phase2: Boolean = false,
    var bossAttackCycle: Int = 0,
    var bossDashTimer: Float = 0f,
    var bossDashDirX: Float = 0f,
    var bossDashDirZ: Float = 0f,
    var bossSpiralOffset: Float = 0f
)

data class Projectile(
    val pos: Vec3,
    val vx: Float,
    val vz: Float,
    val fromPlayer: Boolean,
    val damage: Float,
    val isCrit: Boolean,
    var pierceLeft: Int,
    var life: Float,
    val color: FloatArray,
    val scale: Float = 0.22f,
    val chainCount: Int = 0,
    val electricBurst: Boolean = false,
    val ricochetShards: Int = 0
)

data class PickupOrb(
    val pos: Vec3,
    val isCredit: Boolean,
    val amount: Int,
    var life: Float = 45f
)

data class AlliedTurret(
    val pos: Vec3,
    var life: Float,
    var fireTimer: Float = 0.2f,
    var yawDeg: Float = 0f
)

data class TechMine(
    val pos: Vec3,
    var life: Float = 18f,
    val damage: Float,
    val radius: Float = 4.2f
)

data class ShockwaveVfx(
    val x: Float,
    val y: Float,
    val z: Float,
    var radius: Float,
    val maxRadius: Float,
    var life: Float,
    val maxLife: Float,
    val color: FloatArray
)

data class ParticleSpark(
    var x: Float,
    var y: Float,
    var z: Float,
    var vx: Float,
    var vy: Float,
    var vz: Float,
    var life: Float,
    val color: FloatArray
)

data class SlashArcVfx(
    val x: Float,
    val y: Float,
    val z: Float,
    val yawDeg: Float,
    val radius: Float,
    val swingDir: Float,
    var life: Float,
    val maxLife: Float,
    val color: FloatArray
)

data class MuzzleFlashVfx(
    val x: Float,
    val y: Float,
    val z: Float,
    val yawDeg: Float,
    val size: Float,
    var life: Float,
    val maxLife: Float,
    val color: FloatArray
)

data class DamageNumber(
    val uid: Int,
    var x: Float,
    var y: Float,
    var z: Float,
    val amount: Int,
    val isCrit: Boolean,
    val isPlayerHit: Boolean,
    var life: Float,
    val maxLife: Float
)

data class ProjectedDamageText(
    val id: Int,
    val normX: Float,
    val normY: Float,
    val text: String,
    val isCrit: Boolean,
    val isPlayerHit: Boolean,
    val alpha: Float
)

data class ArenaCollectible(
    val uid: Int,
    val pos: Vec3,
    val isPetCapsule: Boolean,
    val targetId: String,
    val label: String,
    val color: FloatArray,
    var life: Float = 55f
)

data class HudSnapshot(
    val status: RunStatus = RunStatus.PLAYING,
    val hp: Float = 100f,
    val maxHp: Float = 100f,
    val shield: Float = 50f,
    val maxShield: Float = 50f,
    val xp: Int = 0,
    val xpToNext: Int = 100,
    val level: Int = 1,
    val creditsEarned: Int = 0,
    val kills: Int = 0,
    val elapsedSeconds: Float = 0f,
    val waveNumber: Int = 1,
    val weatherLabel: String = "Chuva Forte",
    val dodgeCooldown: Float = 0f,
    val dodgeMaxCooldown: Float = 2.6f,
    val activeCooldown: Float = 0f,
    val activeMaxCooldown: Float = 6.5f,
    val activeSkillName: String = "",
    val bossName: String? = null,
    val bossTitle: String? = null,
    val bossHp: Float = 0f,
    val bossMaxHp: Float = 1f,
    val bossPhase2: Boolean = false,
    val bossBannerTimer: Float = 0f,
    val levelUpChoices: List<EvolutionCardDef> = emptyList(),
    val acquiredCards: Map<String, Int> = emptyMap(),
    val bossesDefeatedInRun: Int = 0,
    val statusBannerText: String? = null,
    val bossDuelActive: Boolean = false,
    val bossMoodBlend: Float = 0f,
    val difficultyTier: Int = 1,
    val playerDamageFlash: Float = 0f,
    val activePetName: String? = null,
    val collectiblesFoundInRun: Int = 0,
    val damagePopups: List<ProjectedDamageText> = emptyList()
)

class GameSimulation(
    private val catalog: GameCatalog,
    save: PlayerSaveData,
    val classDef: CharacterClassDef,
    val palette: CharacterPalette,
    private val onHapticEvent: () -> Unit = {},
    val skinStyle: String? = null,
    private val onCollectibleFound: (isPet: Boolean, targetId: String) -> Unit = { _, _ -> },
    private val onSoundEvent: (SoundEffect) -> Unit = {}
) {
    val world = CyberpunkWorldSector()
    private val rng = Random(System.currentTimeMillis())
    private val deckCardIds: Set<String> = save.equippedDeckCardIds

    // Settings
    var graphicsQuality: GraphicsQuality = save.graphicsQuality
    var desiredCameraDistance: Float = save.cameraDistance.coerceIn(11f, 32f)
    var rainEnabled: Boolean = save.rainEnabled
    var autoAttackEnabled: Boolean = save.autoAttackEnabled
    var hapticsEnabled: Boolean = save.hapticsEnabled
    var soundEnabled: Boolean = save.soundEnabled

    // Dynamic Weather (Cycles between Rain & Dense Fog)
    var currentWeather: WeatherPhase = WeatherPhase.CHUVA
    var dynamicFogDensity: Float = graphicsQuality.fogDensity
    var dynamicRainScale: Float = 1.0f

    // Player Transform & Distant Isometric Camera
    val playerPos = Vec3(0f, 0f, 4f)
    var playerYawDeg: Float = 180f
    var cameraYawDeg: Float = 45f
    var cameraPitchDeg: Float = 49f
    val cameraPos = Vec3(12f, 15f, 16f)
    var camTargetY: Float = 1.2f
    var playerAnimState = RigAnimState.IDLE
    var playerAnimTime = 0f
    var playerHitFlash = 0f

    // Fluid Parkour / Auto-Climb & Acrobatic Jump State
    var playerVelY: Float = 0f
    var isGrounded: Boolean = true
    var isClimbing: Boolean = false
    var jumpAnimTimer: Float = 0f
    var jumpStyle: Int = 0
    private var slashToggle: Float = 1f
    private var climbingBox: AABB? = null
    private var climbNormalX: Float = 0f
    private var climbNormalZ: Float = 0f
    private var wallPushTimer: Float = 0f
    private var climbHoldTimer: Float = 0f
    private var currentSupportY: Float = 0f
    private var footstepTimer: Float = 0f
    private val hitNormalScratch = FloatArray(2)

    // Player Inputs
    @Volatile var moveInputX: Float = 0f
    @Volatile var moveInputY: Float = 0f
    @Volatile var sprinting: Boolean = false

    // Stats & Progression
    var status: RunStatus = RunStatus.PLAYING
    var maxHp: Float
    var hp: Float
    var maxShield: Float
    var shield: Float
    var shieldRegenDelay: Float = 0f
    var shieldRegenMult: Float = 1.0f
    var moveSpeed: Float
    var attackDamage: Float
    var attackCooldown: Float
    var attackRange: Float
    var critChance: Float
    var critMultiplier: Float = 1.65f
    var armor: Float
    var hpRegen: Float = 0f
    var cooldownMult: Float = 1.0f
    var activeSkillCdrMult: Float = 1.0f
    var xpBonusMult: Float = 1.0f
    var creditBonusMult: Float = 1.0f
    var magnetRadius: Float = 4.6f
    var extraProjectiles: Int = 0
    var pierceCount: Int = if (classDef.id == "atirador") 1 else 0
    var chainLightningCount: Int = if (classDef.id == "tecnomago") 1 else 0
    var electricBurstLevel: Int = 0
    var plasmaAuraDamage: Float = 0f
    var fireDotDamage: Float = 0f
    var corrosiveBonus: Float = 0f
    var corrosiveAuraLevel: Int = 0
    var cryoSlow: Float = 0f
    var cryoNovaLevel: Int = 0
    var empPulseAutoLevel: Int = 0
    var thornsRatio: Float = 0f
    var deathExplosionDamage: Float = 0f
    var orbitalBladeCount: Int = 0
    var orbitalBoostMult: Float = 1.0f
    var droneCount: Int = if (classDef.id == "engenheiro") 1 else 0
    var droneFireMult: Float = 1.0f
    var droneDmgMult: Float = 1.0f
    var turretDmgMult: Float = 1.0f
    var turretBonusLife: Float = 0f
    var autoTurretInterval: Float = 0f
    var techMineInterval: Float = 0f
    var mineBoostMult: Float = 1.0f
    var gravityFieldLevel: Int = 0
    var lifestealOnKill: Float = 0f
    var shieldOnKill: Float = 0f
    var executeBonus: Float = 0f
    var bossSlayerBonus: Float = 0f
    var berserkLowHpBonus: Float = 0f
    var shockwaveOnDodgeLevel: Int = 0
    var ricochetLevel: Int = 0

    var level: Int = 1
    var xp: Int = 0
    var xpToNext: Int = 80
    var creditsEarned: Int = 0
    var kills: Int = 0
    var elapsedSeconds: Float = 0f
    var waveNumber: Int = 1
    var bossesDefeatedInRun: Int = 0

    // Timers
    private var attackTimer = 0f
    private var dodgeTimer = 0f
    private var dodgeCooldown = 0f
    private var dodgeMaxCooldown = if (classDef.id == "cacador") 1.85f else 2.6f
    private var dodgeDirX = 0f
    private var dodgeDirZ = -1f

    private val activeAbility: AbilityDef? = catalog.abilities[classDef.activeSkillId]
    private var activeSkillCooldown = 0f
    private var activeSkillMaxCooldown = activeAbility?.cooldown ?: 6.5f
    private var droneAttackTimer = 0.6f
    private var plasmaAuraTimer = 0.4f
    private var cryoNovaTimer = 4.0f
    private var empAutoTimer = 5.5f
    private var autoTurretSpawnTimer = 6.0f
    private var mineSpawnTimer = 3.0f
    private var gravityFieldTimer = 3.5f

    // Spawner, Boss Duel Mode & Progressive Difficulty
    private var nextUid = 1
    private var spawnTimer = 0.8f
    private var nextBossElapsedTarget = 52f
    private var nextBossKillsTarget = 22
    private var bossBannerTimer = 0f
    private var statusBannerText: String? = null
    private var statusBannerTimer = 0f
    var bossDuelActive: Boolean = false
    var bossMoodBlend: Float = 0f
    var difficultyTier: Int = 1

    // Equipped Cyber-Pet Companion & Arena Collectibles
    val equippedPet: PetDef? = save.equippedPetId?.let { id -> catalog.pets.firstOrNull { it.id == id } }
    val petPrimaryCol: FloatArray = equippedPet?.let { parseHexColor(it.primaryHex) } ?: floatArrayOf(0.2f, 0.25f, 0.35f)
    val petGlowCol: FloatArray = equippedPet?.let { parseHexColor(it.glowHex) } ?: floatArrayOf(0.22f, 0.75f, 0.97f)
    val petPos = Vec3(playerPos.x - 1.5f, 0f, playerPos.z - 1.2f)
    var petYawDeg: Float = 180f
    private var petActionTimer = 2.2f
    private var collectibleSpawnTimer = 24f
    var collectiblesFoundInRun: Int = 0
    private val unlockedPetCache = save.unlockedPetIds.toMutableSet()
    private val discoveredRelicCache = save.discoveredRelicIds.toMutableSet()

    // Entities
    val enemies = mutableListOf<ActiveEnemy>()
    val projectiles = mutableListOf<Projectile>()
    val pickups = mutableListOf<PickupOrb>()
    val arenaCollectibles = mutableListOf<ArenaCollectible>()
    val turrets = mutableListOf<AlliedTurret>()
    val mines = mutableListOf<TechMine>()
    val shockwaves = mutableListOf<ShockwaveVfx>()
    val sparks = mutableListOf<ParticleSpark>()
    val slashArcs = mutableListOf<SlashArcVfx>()
    val muzzleFlashes = mutableListOf<MuzzleFlashVfx>()
    val damageNumbers = mutableListOf<DamageNumber>()
    val cardStacks = mutableMapOf<String, Int>()
    var pendingChoices: List<EvolutionCardDef> = emptyList()

    val primaryCol = parseHexColor(palette.primaryHex)
    val secondaryCol = parseHexColor(palette.secondaryHex)
    val visorCol = parseHexColor(palette.visorHex)
    val weaponCol = parseHexColor(palette.weaponHex)

    init {
        val upgDmg = save.upgradeLevels["upg_damage"] ?: 0
        val upgHp = save.upgradeLevels["upg_hp"] ?: 0
        val upgShield = save.upgradeLevels["upg_shield"] ?: 0
        val upgSpeed = save.upgradeLevels["upg_speed"] ?: 0
        val upgCrit = save.upgradeLevels["upg_crit"] ?: 0
        val upgXp = save.upgradeLevels["upg_xp"] ?: 0
        val upgMag = save.upgradeLevels["upg_magnet"] ?: 0

        maxHp = classDef.stats.maxHp + upgHp * 18f
        hp = maxHp
        maxShield = classDef.stats.maxShield + upgShield * 12f
        shield = maxShield
        moveSpeed = classDef.stats.moveSpeed * (1f + upgSpeed * 0.05f)
        attackDamage = classDef.stats.attackDamage * (1f + upgDmg * 0.08f)
        attackCooldown = classDef.stats.attackCooldown
        attackRange = classDef.stats.attackRange
        critChance = classDef.stats.critChance + upgCrit * 0.04f
        armor = classDef.stats.armor
        xpBonusMult = 1f + upgXp * 0.10f
        magnetRadius = 4.6f * (1f + upgMag * 0.20f)

        // Apply permanent Relic Codex mastery bonus (+2.5% damage & +3% credits per discovered relic)
        val relicBonus = save.discoveredRelicIds.size
        if (relicBonus > 0) {
            attackDamage *= (1f + relicBonus * 0.025f)
            creditBonusMult += relicBonus * 0.03f
        }

        // Apply Equipped Cyber-Pet passive bonuses
        equippedPet?.let { pet ->
            when (pet.bonusType) {
                "MAGNET_STRIKE" -> magnetRadius *= (1f + pet.bonusValue)
                "RAPID_SHOT" -> attackCooldown *= (1f - pet.bonusValue)
                "SHIELD_PULSE" -> moveSpeed *= 1.10f
                "CRIT_IGNITE" -> critChance = (critChance + pet.bonusValue).coerceAtMost(0.92f)
                "ARC_HEAL" -> hpRegen += pet.bonusValue
            }
        }

        updateCameraTransform(1.0f)
        spawnInitialSquad()
    }

    private fun spawnInitialSquad() {
        val scout = catalog.enemies.firstOrNull { it.id == "rastejador" } ?: return
        val drone = catalog.enemies.firstOrNull { it.id == "drone_voador" } ?: scout
        spawnEnemyAt(scout, false, -9f, -8f)
        spawnEnemyAt(scout, false, 9f, -9f)
        spawnEnemyAt(drone, false, -11f, 6f)
        spawnEnemyAt(drone, false, 10f, 8f)
    }

    fun rotateCamera(deltaYaw: Float, deltaPitch: Float) {
        cameraYawDeg = (cameraYawDeg + deltaYaw) % 360f
        cameraPitchDeg = (cameraPitchDeg + deltaPitch).coerceIn(32f, 64f)
    }

    fun triggerDodge() {
        if (status != RunStatus.PLAYING || dodgeCooldown > 0f || dodgeTimer > 0f) return
        val (dx, dz) = computeMoveDirection()
        if (dx * dx + dz * dz > 0.01f) {
            dodgeDirX = dx
            dodgeDirZ = dz
        } else {
            dodgeDirX = sin(Math3D.rad(playerYawDeg))
            dodgeDirZ = cos(Math3D.rad(playerYawDeg))
        }
        dodgeTimer = 0.24f
        dodgeCooldown = dodgeMaxCooldown * cooldownMult
        playerAnimState = RigAnimState.DODGE
        emitSound(SoundEffect.DEFENSE_SHIELD)
        shockwaves.add(ShockwaveVfx(playerPos.x, 0.2f, playerPos.z, 0.4f, 2.4f, 0.25f, 0.25f, secondaryCol))
        if (shockwaveOnDodgeLevel > 0) {
            val r = 4.8f
            shockwaves.add(ShockwaveVfx(playerPos.x, 0.25f, playerPos.z, 0.5f, r, 0.35f, 0.35f, visorCol))
            damageEnemiesInRadius(playerPos.x, playerPos.z, r, attackDamage * 1.4f * shockwaveOnDodgeLevel, stunSec = 1.2f)
        }
        if (hapticsEnabled) onHapticEvent()
    }

    fun triggerManualAttack() {
        if (status != RunStatus.PLAYING) return
        if (attackTimer <= 0.12f) {
            performPrimaryAttack(forceForward = true)
        }
    }

    fun triggerActiveAbility() {
        if (status != RunStatus.PLAYING || activeSkillCooldown > 0f) return
        val ability = activeAbility ?: return
        activeSkillCooldown = activeSkillMaxCooldown * cooldownMult * activeSkillCdrMult
        val dmg = attackDamage * ability.damageMult
        val r = ability.radius * (1f + (attackRange / classDef.stats.attackRange - 1f) * 0.5f)

        when (classDef.id) {
            "berserker" -> {
                emitSound(SoundEffect.POWER_SKILL)
                emitSound(SoundEffect.STRIKE_SLASH)
                shockwaves.add(ShockwaveVfx(playerPos.x, 0.25f, playerPos.z, 0.8f, r, 0.42f, 0.42f, weaponCol))
                damageEnemiesInRadius(playerPos.x, playerPos.z, r, dmg, stunSec = 1.6f)
            }
            "engenheiro" -> {
                emitSound(SoundEffect.POWER_SKILL)
                turrets.add(AlliedTurret(Vec3(playerPos.x, 0f, playerPos.z), life = 14f + turretBonusLife, fireTimer = 0.1f, yawDeg = playerYawDeg))
                shockwaves.add(ShockwaveVfx(playerPos.x, 0.2f, playerPos.z, 0.5f, 2.8f, 0.35f, 0.35f, visorCol))
            }
            "atirador" -> {
                emitSound(SoundEffect.POWER_SKILL)
                emitSound(SoundEffect.SHOT_PLASMA)
                val target = findNearestEnemy(r + 6f)
                val dirX: Float
                val dirZ: Float
                if (target != null) {
                    val dx = target.pos.x - playerPos.x
                    val dz = target.pos.z - playerPos.z
                    val l = sqrt(dx * dx + dz * dz).coerceAtLeast(0.01f)
                    dirX = dx / l; dirZ = dz / l
                } else {
                    dirX = sin(Math3D.rad(playerYawDeg)); dirZ = cos(Math3D.rad(playerYawDeg))
                }
                projectiles.add(
                    Projectile(
                        pos = Vec3(playerPos.x, 1.15f, playerPos.z),
                        vx = dirX * 32f,
                        vz = dirZ * 32f,
                        fromPlayer = true,
                        damage = dmg,
                        isCrit = true,
                        pierceLeft = 99,
                        life = 1.4f,
                        color = weaponCol,
                        scale = 0.44f,
                        chainCount = chainLightningCount,
                        electricBurst = true
                    )
                )
            }
            "tecnomago" -> {
                emitSound(SoundEffect.POWER_SKILL)
                shockwaves.add(ShockwaveVfx(playerPos.x, 0.35f, playerPos.z, r, 0.6f, 0.65f, 0.65f, weaponCol))
                enemies.forEach { e ->
                    val dx = playerPos.x - e.pos.x
                    val dz = playerPos.z - e.pos.z
                    val d = sqrt(dx * dx + dz * dz)
                    if (d <= r && d > 0.2f) {
                        e.pos.x += (dx / d) * min(3.5f, d * 0.55f)
                        e.pos.z += (dz / d) * min(3.5f, d * 0.55f)
                        applyDamageToEnemy(e, dmg, isCrit = false)
                        e.stunTimer = max(e.stunTimer, 1.1f)
                    }
                }
            }
            "hacker" -> {
                emitSound(SoundEffect.POWER_SKILL)
                shockwaves.add(ShockwaveVfx(playerPos.x, 0.25f, playerPos.z, 0.8f, r, 0.5f, 0.5f, weaponCol))
                enemies.forEach { e ->
                    if (e.pos.distTo(playerPos.x, playerPos.z) <= r) {
                        applyDamageToEnemy(e, dmg, isCrit = false)
                        e.stunTimer = max(e.stunTimer, 2.4f)
                        e.corrosiveTimer = max(e.corrosiveTimer, 5f)
                    }
                }
            }
            "guardiao" -> {
                emitSound(SoundEffect.DEFENSE_SHIELD)
                emitSound(SoundEffect.POWER_SKILL)
                shield = min(maxShield, shield + maxShield * 0.45f)
                shockwaves.add(ShockwaveVfx(playerPos.x, 0.3f, playerPos.z, 0.6f, r, 0.45f, 0.45f, visorCol))
                enemies.forEach { e ->
                    val dx = e.pos.x - playerPos.x
                    val dz = e.pos.z - playerPos.z
                    val d = sqrt(dx * dx + dz * dz)
                    if (d <= r && d > 0.05f) {
                        e.pos.x += (dx / d) * 3.8f
                        e.pos.z += (dz / d) * 3.8f
                        applyDamageToEnemy(e, dmg, isCrit = false)
                    }
                }
            }
            "cacador" -> {
                emitSound(SoundEffect.POWER_SKILL)
                emitSound(SoundEffect.SHOT_PLASMA)
                val count = 12
                for (i in 0 until count) {
                    val ang = (2.0 * Math.PI * i / count).toFloat()
                    projectiles.add(
                        Projectile(
                            pos = Vec3(playerPos.x, 1.1f, playerPos.z),
                            vx = sin(ang) * 21f,
                            vz = cos(ang) * 21f,
                            fromPlayer = true,
                            damage = dmg,
                            isCrit = rng.nextFloat() < critChance,
                            pierceLeft = pierceCount,
                            life = 1.1f,
                            color = weaponCol,
                            scale = 0.24f
                        )
                    )
                }
            }
        }
        spawnSparks(playerPos.x, 1.1f, playerPos.z, weaponCol, 12)
        if (hapticsEnabled) onHapticEvent()
    }

    fun applyCard(card: EvolutionCardDef) {
        cardStacks[card.id] = (cardStacks[card.id] ?: 0) + 1
        when (card.effectType) {
            "DAMAGE_MULT" -> attackDamage *= (1f + card.value)
            "MOVE_SPEED" -> moveSpeed *= (1f + card.value)
            "HP_REGEN" -> hpRegen += card.value
            "MAX_SHIELD" -> { maxShield += card.value; shield = min(maxShield, shield + card.value) }
            "ADD_DRONE" -> droneCount += card.value.toInt()
            "EXTRA_PROJECTILE" -> extraProjectiles += card.value.toInt()
            "CRIT_CHANCE" -> critChance = (critChance + card.value).coerceAtMost(0.90f)
            "CRIT_DAMAGE" -> critMultiplier += card.value
            "ELECTRIC_BURST" -> electricBurstLevel += 1
            "PLASMA_AURA" -> plasmaAuraDamage += card.value
            "FIRE_DOT" -> fireDotDamage += card.value
            "CORROSIVE_DOT" -> corrosiveBonus += card.value
            "CRYO_SLOW" -> cryoSlow = (cryoSlow + card.value).coerceAtMost(0.65f)
            "COOLDOWN_REDUCE" -> cooldownMult = (cooldownMult * (1f - card.value)).coerceAtLeast(0.32f)
            "RANGE_UP" -> attackRange *= (1f + card.value)
            "MAGNET_RADIUS" -> magnetRadius *= (1f + card.value)
            "XP_BONUS" -> xpBonusMult += card.value
            "CREDITS_BONUS" -> creditBonusMult += card.value
            "ARMOR_UP" -> armor += card.value
            "THORNS" -> thornsRatio += card.value
            "ORBITAL_BLADES" -> orbitalBladeCount += card.value.toInt()
            "AUTO_TURRET" -> autoTurretInterval = if (autoTurretInterval == 0f) 9f else max(4f, autoTurretInterval - 2f)
            "TECH_MINES" -> techMineInterval = if (techMineInterval == 0f) 3.8f else max(1.6f, techMineInterval - 0.8f)
            "HEAL_AND_HP" -> { maxHp += card.value; hp = min(maxHp, hp + maxHp * 0.45f) }
            "CHAIN_LIGHTNING" -> chainLightningCount += card.value.toInt()
            "PIERCE_UP" -> pierceCount += card.value.toInt()
            "DEATH_EXPLOSION" -> deathExplosionDamage += card.value
            "DOUBLE_DRONES" -> { droneCount += 1; droneFireMult *= 1.65f }
            "GRAVITY_FIELD" -> gravityFieldLevel += 1
            "TECH_OVERLOAD" -> {
                attackDamage *= 1.35f
                cooldownMult = (cooldownMult * 0.78f).coerceAtLeast(0.32f)
                critChance = (critChance + 0.15f).coerceAtMost(0.92f)
            }
            // Cards 31-56
            "LIFESTEAL_KILL" -> lifestealOnKill += card.value
            "SHIELD_ON_KILL" -> shieldOnKill += card.value
            "FIRE_RATE" -> attackCooldown = (attackCooldown * (1f - card.value)).coerceAtLeast(0.14f)
            "EXECUTE_DMG" -> executeBonus += card.value
            "BOSS_SLAYER" -> bossSlayerBonus += card.value
            "BERSERK_LOW_HP" -> berserkLowHpBonus += card.value
            "DASH_COOLDOWN" -> { dodgeMaxCooldown *= (1f - card.value); moveSpeed *= 1.15f }
            "SHOCKWAVE_DODGE" -> shockwaveOnDodgeLevel += 1
            "CRYO_NOVA" -> cryoNovaLevel += 1
            "HP_AND_ARMOR" -> { maxHp += card.value; hp = min(maxHp, hp + card.value); armor += 4f }
            "DRONE_DMG" -> droneDmgMult += card.value
            "TURRET_DMG" -> { turretDmgMult += card.value; turretBonusLife += 4f }
            "SHIELD_REGEN_UP" -> shieldRegenMult += card.value
            "RICOCHET_SHARDS" -> ricochetLevel += 1
            "INFERNO_UP" -> { fireDotDamage += card.value; attackRange *= 1.15f }
            "CORROSIVE_AURA" -> corrosiveAuraLevel += 1
            "PRECISION_MOD" -> { critChance = (critChance + 0.15f).coerceAtMost(0.92f); critMultiplier += 0.25f }
            "MOBILITY_HYBRID" -> { moveSpeed *= 1.10f; magnetRadius *= 1.30f }
            "EMP_PULSE_AUTO" -> empPulseAutoLevel += 1
            "FORTRESS_CORE" -> { armor += 12f; maxShield += 60f; shield = min(maxShield, shield + 60f); hpRegen += 4f }
            "FRENZY_MOD" -> { attackCooldown = (attackCooldown * 0.78f).coerceAtLeast(0.14f); moveSpeed *= 1.12f }
            "ORBITAL_BOOST" -> { orbitalBoostMult += 0.55f; if (orbitalBladeCount == 0) orbitalBladeCount = 1 }
            "MINE_BOOST" -> { mineBoostMult += card.value; if (techMineInterval == 0f) techMineInterval = 4.2f }
            "SKILL_CDR" -> activeSkillCdrMult = (activeSkillCdrMult * (1f - card.value)).coerceAtLeast(0.35f)
            "PHOENIX_SURGE" -> { hp = maxHp; shield = maxShield; attackDamage *= (1f + card.value) }
            "APEX_ARSENAL" -> { extraProjectiles += 2; pierceCount += 2; chainLightningCount += 2 }
        }
        pendingChoices = emptyList()
        status = RunStatus.PLAYING
    }

    fun step(dtRaw: Float) {
        val dt = dtRaw.coerceIn(0.001f, 0.05f)
        if (status != RunStatus.PLAYING) {
            updateCameraTransform(dt)
            return
        }

        elapsedSeconds += dt
        waveNumber = 1 + (elapsedSeconds / 35f).toInt()
        if (bossBannerTimer > 0f) bossBannerTimer -= dt
        if (statusBannerTimer > 0f) {
            statusBannerTimer -= dt
            if (statusBannerTimer <= 0f) statusBannerText = null
        }

        updateWeatherCycle(dt)
        bossMoodBlend = Math3D.lerp(bossMoodBlend, if (bossDuelActive) 1f else 0f, (dt * 1.45f).coerceIn(0f, 1f))

        if (dodgeCooldown > 0f) dodgeCooldown = max(0f, dodgeCooldown - dt)
        if (activeSkillCooldown > 0f) activeSkillCooldown = max(0f, activeSkillCooldown - dt)
        if (playerHitFlash > 0f) playerHitFlash = max(0f, playerHitFlash - dt)
        if (hpRegen > 0f && hp > 0f) hp = min(maxHp, hp + hpRegen * dt)
        if (shieldRegenDelay > 0f) {
            shieldRegenDelay -= dt
        } else if (shield < maxShield) {
            val baseRate = if (classDef.id == "guardiao") 14f else 7.5f
            shield = min(maxShield, shield + baseRate * shieldRegenMult * dt)
        }

        updatePlayerMovement(dt)
        updateCombat(dt)
        updateAlliedSystems(dt)
        updatePetCompanion(dt)
        updateEnemies(dt)
        updateProjectiles(dt)
        updatePickups(dt)
        updateArenaCollectibles(dt)
        updateVfx(dt)
        updateWaveDirector(dt)
        updateCameraTransform(dt)
    }

    private fun updateWeatherCycle(dt: Float) {
        val cycleIdx = ((elapsedSeconds / 28f).toInt()) % WeatherPhase.entries.size
        currentWeather = WeatherPhase.entries[cycleIdx]
        val targetFog = graphicsQuality.fogDensity * currentWeather.fogMultiplier * (1f + bossMoodBlend * 0.45f)
        val targetRain = currentWeather.rainMultiplier * (1f + bossMoodBlend * 0.40f)
        dynamicFogDensity = Math3D.lerp(dynamicFogDensity, targetFog, dt * 0.8f)
        dynamicRainScale = Math3D.lerp(dynamicRainScale, targetRain, dt * 0.8f)
    }

    private fun computeMoveDirection(): Pair<Float, Float> {
        val ix = moveInputX
        val iy = moveInputY
        if (ix * ix + iy * iy < 0.01f) return 0f to 0f
        val yawRad = Math3D.rad(cameraYawDeg)
        val fwdX = -sin(yawRad)
        val fwdZ = -cos(yawRad)
        val rightX = cos(yawRad)
        val rightZ = -sin(yawRad)
        val wx = rightX * ix + fwdX * iy
        val wz = rightZ * ix + fwdZ * iy
        val len = sqrt(wx * wx + wz * wz).coerceAtLeast(1e-4f)
        return (wx / len) to (wz / len)
    }

    private fun computeSupportY(x: Float, z: Float, curY: Float): Float {
        var highest = 0f
        for (box in world.colliders) {
            if (box.maxY <= 8.5f && box.overlapsFootprintXZ(x, z, 0.34f) && curY >= box.maxY - 0.35f) {
                if (box.maxY > highest) highest = box.maxY
            }
        }
        return highest
    }

    private fun updatePlayerMovement(dt: Float) {
        playerAnimTime += dt
        val prevSupportY = currentSupportY
        val wasGrounded = isGrounded
        val (dx, dz) = computeMoveDirection()
        val inputMag = sqrt(moveInputX * moveInputX + moveInputY * moveInputY).coerceIn(0f, 1f)

        if (dodgeTimer > 0f) {
            isClimbing = false
            climbingBox = null
            wallPushTimer = 0f
            dodgeTimer -= dt
            val dashSpeed = moveSpeed * 2.7f
            playerPos.x += dodgeDirX * dashSpeed * dt
            playerPos.z += dodgeDirZ * dashSpeed * dt
            playerYawDeg = Math3D.deg(atan2(dodgeDirX, dodgeDirZ))
            playerAnimState = RigAnimState.DODGE
        } else if (isClimbing && climbingBox != null) {
            val box = climbingBox!!
            val pushInto = -(dx * climbNormalX + dz * climbNormalZ)
            // Face the wall we are climbing
            val wallYaw = Math3D.deg(atan2(-climbNormalX, -climbNormalZ))
            playerYawDeg = Math3D.lerpAngleDeg(playerYawDeg, wallYaw, dt * 16f)

            if (inputMag > 0.20f && pushInto > 0.12f) {
                // Pushing toward the wall: climb smoothly upward + slight lateral shimmy
                climbHoldTimer = 0.28f
                val climbSpeed = 5.9f
                playerPos.y += climbSpeed * dt
                playerVelY = 0f
                // Lateral shimmy along wall tangent
                val tx = -climbNormalZ
                val tz = climbNormalX
                val tangentDot = dx * tx + dz * tz
                playerPos.x += tx * tangentDot * (moveSpeed * 0.45f) * dt
                playerPos.z += tz * tangentDot * (moveSpeed * 0.45f) * dt
                box.resolveCircleXZ(playerPos, 0.65f)
                playerAnimState = RigAnimState.CLIMB
                footstepTimer -= dt
                if (footstepTimer <= 0f) {
                    footstepTimer = 0.27f
                    emitSound(SoundEffect.FOOTSTEP)
                }

                // Reached roof/top ledge -> fluid automatic parkour mantle flip onto top!
                if (playerPos.y >= box.maxY - 0.18f) {
                    playerPos.y = box.maxY + 0.08f
                    playerVelY = 5.8f
                    playerPos.x += -climbNormalX * 0.58f
                    playerPos.z += -climbNormalZ * 0.58f
                    isClimbing = false
                    climbingBox = null
                    wallPushTimer = 0f
                    isGrounded = false
                    jumpAnimTimer = 0f
                    jumpStyle = (jumpStyle + 1) % 3
                    spawnSparks(playerPos.x, playerPos.y + 0.1f, playerPos.z, visorCol, 6)
                    emitSound(SoundEffect.JUMP_PARKOUR)
                    playerAnimState = RigAnimState.JUMP
                }
            } else if (inputMag > 0.25f && pushInto < -0.22f) {
                // Pulling away from the wall -> acrobatic wall-kick backflip off!
                playerVelY = 8.6f
                playerPos.x += climbNormalX * 0.48f
                playerPos.z += climbNormalZ * 0.48f
                isClimbing = false
                climbingBox = null
                wallPushTimer = 0f
                isGrounded = false
                jumpAnimTimer = 0f
                jumpStyle = 2
                shockwaves.add(ShockwaveVfx(playerPos.x, playerPos.y + 0.3f, playerPos.z, 0.2f, 1.5f, 0.22f, 0.22f, visorCol))
                emitSound(SoundEffect.JUMP_PARKOUR)
                playerAnimState = RigAnimState.JUMP
            } else {
                // Idle hold briefly, then gentle slide down
                climbHoldTimer -= dt
                if (climbHoldTimer <= 0f) {
                    playerPos.y -= 2.8f * dt
                }
                playerAnimState = RigAnimState.CLIMB
                if (playerPos.y <= 0.02f) {
                    playerPos.y = 0f
                    isClimbing = false
                    climbingBox = null
                    wallPushTimer = 0f
                    isGrounded = true
                }
            }
        } else {
            if (inputMag > 0.08f) {
                val spd = moveSpeed * (if (sprinting || inputMag > 0.85f) 1.15f else 0.88f) * inputMag
                playerPos.x += dx * spd * dt
                playerPos.z += dz * spd * dt
                val targetYaw = Math3D.deg(atan2(dx, dz))
                playerYawDeg = Math3D.lerpAngleDeg(playerYawDeg, targetYaw, dt * 14f)
            }

            val limit = world.arenaHalfSize - 1.2f
            playerPos.x = playerPos.x.coerceIn(-limit, limit)
            playerPos.z = playerPos.z.coerceIn(-limit, limit)

            var pushedClimbableWall = false
            for (box in world.colliders) {
                if (box.resolveCircleXZAndNormal(playerPos, 0.65f, hitNormalScratch)) {
                    val nx = hitNormalScratch[0]
                    val nz = hitNormalScratch[1]
                    val intoWall = -(dx * nx + dz * nz)
                    val heightGap = box.maxY - playerPos.y
                    if (inputMag > 0.30f && intoWall > 0.28f && box.maxY <= 8.0f && heightGap > 0.15f) {
                        pushedClimbableWall = true
                        wallPushTimer += dt
                        if (heightGap <= 1.38f) {
                            // Low/medium obstacle: instant acrobatic parkour vault/flip onto top!
                            if (wallPushTimer >= 0.035f && (isGrounded || playerVelY <= 1.5f)) {
                                playerVelY = if (heightGap <= 0.95f) 8.6f else 10.8f
                                playerPos.x += -nx * 0.26f
                                playerPos.z += -nz * 0.26f
                                isGrounded = false
                                wallPushTimer = 0f
                                jumpAnimTimer = 0f
                                jumpStyle = (jumpStyle + 1) % 2
                                spawnSparks(playerPos.x, playerPos.y + 0.15f, playerPos.z, secondaryCol, 7)
                                emitSound(SoundEffect.JUMP_PARKOUR)
                            }
                        } else if (wallPushTimer >= 0.085f) {
                            // Taller wall (containers, houses): latch and start fluid climbing!
                            isClimbing = true
                            climbingBox = box
                            climbNormalX = nx
                            climbNormalZ = nz
                            climbHoldTimer = 0.28f
                            playerVelY = 0f
                            wallPushTimer = 0f
                            break
                        }
                    }
                }
            }
            if (!pushedClimbableWall) {
                wallPushTimer = 0f
            }

            if (!isClimbing) {
                // Check if player just ran off an elevated surface -> acrobatic parkour leap off ledge!
                val edgeSupport = computeSupportY(playerPos.x, playerPos.z, playerPos.y)
                if (wasGrounded && prevSupportY > 0.4f && edgeSupport < prevSupportY - 0.35f && inputMag > 0.32f && playerVelY <= 0.5f) {
                    playerVelY = 7.6f
                    isGrounded = false
                    jumpAnimTimer = 0f
                    jumpStyle = 0
                    spawnSparks(playerPos.x, playerPos.y + 0.15f, playerPos.z, visorCol, 5)
                    emitSound(SoundEffect.JUMP_PARKOUR)
                }

                // Vertical gravity & ground landing
                playerVelY -= 26.0f * dt
                playerPos.y += playerVelY * dt

                val groundY = computeSupportY(playerPos.x, playerPos.z, playerPos.y + 0.25f)
                if (playerPos.y <= groundY) {
                    if (!wasGrounded && playerVelY < -5.2f) {
                        emitSound(SoundEffect.LAND_IMPACT)
                    }
                    if (!wasGrounded && playerVelY < -7.5f) {
                        spawnSparks(playerPos.x, groundY + 0.1f, playerPos.z, secondaryCol, 7)
                        if (playerVelY < -11.5f) {
                            shockwaves.add(ShockwaveVfx(playerPos.x, groundY + 0.08f, playerPos.z, 0.2f, 1.8f, 0.22f, 0.22f, secondaryCol))
                        }
                    }
                    playerPos.y = groundY
                    playerVelY = 0f
                    isGrounded = true
                    jumpAnimTimer = 0f
                } else {
                    isGrounded = false
                    jumpAnimTimer += dt
                }
                currentSupportY = groundY
            }

            if (isGrounded && !isClimbing && inputMag > 0.10f) {
                footstepTimer -= dt
                if (footstepTimer <= 0f) {
                    footstepTimer = if (inputMag > 0.72f || sprinting) 0.26f else 0.36f
                    emitSound(SoundEffect.FOOTSTEP)
                }
            } else if (!isClimbing) {
                footstepTimer = 0.04f
            }

            playerAnimState = when {
                isClimbing -> RigAnimState.CLIMB
                !isGrounded -> RigAnimState.JUMP
                inputMag > 0.72f -> RigAnimState.RUN
                inputMag > 0.08f -> RigAnimState.WALK
                attackTimer > attackCooldown * cooldownMult * 0.65f -> RigAnimState.ATTACK
                else -> RigAnimState.IDLE
            }
        }

        val limit = world.arenaHalfSize - 1.2f
        playerPos.x = playerPos.x.coerceIn(-limit, limit)
        playerPos.z = playerPos.z.coerceIn(-limit, limit)
    }

    private fun updateCameraTransform(dt: Float) {
        val targetX = playerPos.x
        camTargetY = Math3D.lerp(camTargetY, playerPos.y + 1.2f, (dt * 7.5f).coerceIn(0.05f, 1f))
        val targetY = camTargetY
        val targetZ = playerPos.z

        val yawR = Math3D.rad(cameraYawDeg)
        val pitchR = Math3D.rad(cameraPitchDeg)
        val horizDist = desiredCameraDistance * cos(pitchR)
        val vertDist = desiredCameraDistance * sin(pitchR)

        val desiredX = targetX + sin(yawR) * horizDist
        val desiredY = targetY + vertDist
        val desiredZ = targetZ + cos(yawR) * horizDist

        // Only clamp isometric camera against tall structures (> 6.5m) so containers/roofs don't push zoom in
        var minT = 1.0f
        for (box in world.colliders) {
            if (box.maxY > 6.5f) {
                val t = box.intersectSegment(targetX, targetY, targetZ, desiredX, desiredY, desiredZ)
                if (t < minT) minT = t
            }
        }
        val clampedT = (minT - 0.04f).coerceIn(0.45f, 1.0f)
        val finalX = Math3D.lerp(targetX, desiredX, clampedT)
        val finalY = Math3D.lerp(targetY, desiredY, clampedT)
        val finalZ = Math3D.lerp(targetZ, desiredZ, clampedT)

        val smooth = (dt * 10f).coerceIn(0.1f, 1f)
        cameraPos.x = Math3D.lerp(cameraPos.x, finalX, smooth)
        cameraPos.y = Math3D.lerp(cameraPos.y, finalY, smooth)
        cameraPos.z = Math3D.lerp(cameraPos.z, finalZ, smooth)
    }

    private fun updateCombat(dt: Float) {
        if (attackTimer > 0f) attackTimer -= dt
        if (autoAttackEnabled && attackTimer <= 0f) {
            val target = findNearestEnemy(attackRange)
            if (target != null) {
                performPrimaryAttack(forceForward = false, explicitTarget = target)
            }
        }
    }

    private fun findNearestEnemy(range: Float): ActiveEnemy? {
        var best: ActiveEnemy? = null
        var bestDist = range
        for (e in enemies) {
            val d = e.pos.distTo(playerPos.x, playerPos.z)
            if (d <= bestDist) {
                bestDist = d
                best = e
            }
        }
        return best
    }

    private fun performPrimaryAttack(forceForward: Boolean, explicitTarget: ActiveEnemy? = null) {
        val rageBoost = if (classDef.id == "berserker") 1f - 0.32f * (1f - hp / maxHp) else 1f
        attackTimer = attackCooldown * cooldownMult * rageBoost

        val target = explicitTarget ?: findNearestEnemy(attackRange)
        val dirX: Float
        val dirZ: Float
        if (target != null && !forceForward) {
            val dx = target.pos.x - playerPos.x
            val dz = target.pos.z - playerPos.z
            val l = sqrt(dx * dx + dz * dz).coerceAtLeast(0.01f)
            dirX = dx / l; dirZ = dz / l
            playerYawDeg = Math3D.deg(atan2(dirX, dirZ))
        } else {
            dirX = sin(Math3D.rad(playerYawDeg))
            dirZ = cos(Math3D.rad(playerYawDeg))
        }

        val missingHpRatio = 1f - (hp / maxHp).coerceIn(0f, 1f)
        val lowHpMult = 1f + berserkLowHpBonus * missingHpRatio
        val isCrit = rng.nextFloat() < critChance
        val dmg = attackDamage * lowHpMult * (if (isCrit) critMultiplier else 1f)

        if (classDef.weaponType == WeaponType.ENERGY_BLADES) {
            slashToggle = -slashToggle
            emitSound(SoundEffect.STRIKE_SLASH)
            slashArcs.add(
                SlashArcVfx(
                    x = playerPos.x + dirX * 1.1f,
                    y = playerPos.y + 1.08f,
                    z = playerPos.z + dirZ * 1.1f,
                    yawDeg = playerYawDeg,
                    radius = (attackRange * 0.58f).coerceIn(1.5f, 3.2f),
                    swingDir = slashToggle,
                    life = 0.22f,
                    maxLife = 0.22f,
                    color = weaponCol
                )
            )
            shockwaves.add(ShockwaveVfx(playerPos.x + dirX * 1.4f, playerPos.y + 0.9f, playerPos.z + dirZ * 1.4f, 0.5f, attackRange * 0.72f, 0.22f, 0.22f, weaponCol))
            damageEnemiesInRadius(playerPos.x + dirX * 1.3f, playerPos.z + dirZ * 1.3f, attackRange * 0.78f, dmg, isCrit = isCrit)
            if (extraProjectiles > 0) {
                spawnPlayerVolley(dirX, dirZ, dmg * 0.65f, isCrit, extraProjectiles)
            }
        } else {
            spawnPlayerVolley(dirX, dirZ, dmg, isCrit, 1 + extraProjectiles)
        }
    }

    private fun spawnPlayerVolley(dirX: Float, dirZ: Float, dmg: Float, isCrit: Boolean, count: Int) {
        emitSound(SoundEffect.SHOT_PLASMA)
        val baseAngle = atan2(dirX, dirZ)
        val spreadStep = 0.14f
        val startOffset = -((count - 1) * spreadStep) * 0.5f
        val speed = if (classDef.weaponType == WeaponType.PLASMA_RIFLE) 26f else 20f
        muzzleFlashes.add(
            MuzzleFlashVfx(
                x = playerPos.x + dirX * 0.85f,
                y = playerPos.y + 1.16f,
                z = playerPos.z + dirZ * 0.85f,
                yawDeg = Math3D.deg(baseAngle),
                size = if (isCrit) 0.52f else 0.36f,
                life = 0.11f,
                maxLife = 0.11f,
                color = weaponCol
            )
        )
        spawnSparks(playerPos.x + dirX * 0.8f, playerPos.y + 1.16f, playerPos.z + dirZ * 0.8f, weaponCol, 4)
        for (i in 0 until count) {
            val ang = baseAngle + startOffset + i * spreadStep
            projectiles.add(
                Projectile(
                    pos = Vec3(playerPos.x + sin(ang) * 0.6f, playerPos.y + 1.15f, playerPos.z + cos(ang) * 0.6f),
                    vx = sin(ang) * speed,
                    vz = cos(ang) * speed,
                    fromPlayer = true,
                    damage = dmg,
                    isCrit = isCrit,
                    pierceLeft = pierceCount,
                    life = (attackRange / speed).coerceIn(0.55f, 1.6f),
                    color = weaponCol,
                    scale = if (isCrit) 0.28f else 0.21f,
                    chainCount = chainLightningCount,
                    electricBurst = electricBurstLevel > 0,
                    ricochetShards = ricochetLevel
                )
            )
        }
    }

    private fun updateAlliedSystems(dt: Float) {
        if (droneCount > 0) {
            droneAttackTimer -= dt
            if (droneAttackTimer <= 0f) {
                droneAttackTimer = (0.78f * cooldownMult) / droneFireMult
                val target = findNearestEnemy(14f)
                if (target != null) {
                    for (i in 0 until droneCount) {
                        val angle = elapsedSeconds * 2.2f + (i * 2.0f * Math.PI.toFloat() / droneCount)
                        val dx = playerPos.x + cos(angle) * 1.9f
                        val dz = playerPos.z + sin(angle) * 1.9f
                        val tx = target.pos.x - dx
                        val tz = target.pos.z - dz
                        val len = sqrt(tx * tx + tz * tz).coerceAtLeast(0.01f)
                        projectiles.add(
                            Projectile(
                                pos = Vec3(dx, 2.0f, dz),
                                vx = (tx / len) * 20f,
                                vz = (tz / len) * 20f,
                                fromPlayer = true,
                                damage = attackDamage * 0.55f * droneDmgMult,
                                isCrit = false,
                                pierceLeft = 0,
                                life = 0.85f,
                                color = visorCol,
                                scale = 0.16f
                            )
                        )
                    }
                }
            }
        }

        if (orbitalBladeCount > 0) {
            val bladeRadius = 2.4f
            val spinSpeed = 4.2f * orbitalBoostMult
            for (i in 0 until orbitalBladeCount) {
                val ang = elapsedSeconds * spinSpeed + (i * 2f * Math.PI.toFloat() / orbitalBladeCount)
                val bx = playerPos.x + cos(ang) * bladeRadius
                val bz = playerPos.z + sin(ang) * bladeRadius
                for (e in enemies) {
                    if (e.hitFlash <= 0f && e.pos.distTo(bx, bz) < 1.15f * e.def.scale) {
                        applyDamageToEnemy(e, attackDamage * 0.65f * orbitalBoostMult, isCrit = false)
                    }
                }
            }
        }

        if (plasmaAuraDamage > 0f || corrosiveAuraLevel > 0) {
            plasmaAuraTimer -= dt
            if (plasmaAuraTimer <= 0f) {
                plasmaAuraTimer = 0.45f
                if (plasmaAuraDamage > 0f) {
                    damageEnemiesInRadius(playerPos.x, playerPos.z, 4.0f, plasmaAuraDamage * 0.45f)
                }
                if (corrosiveAuraLevel > 0) {
                    enemies.forEach { e ->
                        if (e.pos.distTo(playerPos.x, playerPos.z) <= 5.2f) {
                            e.corrosiveTimer = max(e.corrosiveTimer, 3.0f)
                            applyDamageToEnemy(e, attackDamage * 0.25f * corrosiveAuraLevel, false)
                        }
                    }
                }
            }
        }

        if (cryoNovaLevel > 0) {
            cryoNovaTimer -= dt
            if (cryoNovaTimer <= 0f) {
                cryoNovaTimer = 4.5f
                val r = 6.2f
                shockwaves.add(ShockwaveVfx(playerPos.x, 0.25f, playerPos.z, 0.5f, r, 0.4f, 0.4f, floatArrayOf(0.35f, 0.85f, 1.0f)))
                enemies.forEach { e ->
                    if (e.pos.distTo(playerPos.x, playerPos.z) <= r) {
                        e.cryoTimer = max(e.cryoTimer, 4.0f)
                        applyDamageToEnemy(e, attackDamage * 0.7f * cryoNovaLevel, false)
                    }
                }
            }
        }

        if (empPulseAutoLevel > 0) {
            empAutoTimer -= dt
            if (empAutoTimer <= 0f) {
                empAutoTimer = 5.5f
                val r = 7.0f
                shockwaves.add(ShockwaveVfx(playerPos.x, 0.3f, playerPos.z, 0.6f, r, 0.4f, 0.4f, floatArrayOf(0.55f, 0.75f, 1.0f)))
                damageEnemiesInRadius(playerPos.x, playerPos.z, r, attackDamage * 1.1f * empPulseAutoLevel, stunSec = 1.4f)
            }
        }

        if (autoTurretInterval > 0f) {
            autoTurretSpawnTimer -= dt
            if (autoTurretSpawnTimer <= 0f) {
                autoTurretSpawnTimer = autoTurretInterval
                turrets.add(AlliedTurret(Vec3(playerPos.x, 0f, playerPos.z), life = 11f + turretBonusLife))
            }
        }
        val tIter = turrets.iterator()
        while (tIter.hasNext()) {
            val t = tIter.next()
            t.life -= dt
            t.fireTimer -= dt
            if (t.life <= 0f) { tIter.remove(); continue }
            if (t.fireTimer <= 0f) {
                t.fireTimer = 0.48f
                var target: ActiveEnemy? = null
                var best = 13.5f
                for (e in enemies) {
                    val d = e.pos.distTo(t.pos.x, t.pos.z)
                    if (d < best) { best = d; target = e }
                }
                if (target != null) {
                    val dx = target.pos.x - t.pos.x
                    val dz = target.pos.z - t.pos.z
                    val len = sqrt(dx * dx + dz * dz).coerceAtLeast(0.01f)
                    t.yawDeg = Math3D.deg(atan2(dx, dz))
                    projectiles.add(
                        Projectile(
                            pos = Vec3(t.pos.x, 1.15f, t.pos.z),
                            vx = (dx / len) * 22f,
                            vz = (dz / len) * 22f,
                            fromPlayer = true,
                            damage = attackDamage * 0.72f * turretDmgMult,
                            isCrit = false,
                            pierceLeft = 0,
                            life = 0.75f,
                            color = floatArrayOf(0.9f, 0.66f, 0.24f),
                            scale = 0.18f
                        )
                    )
                }
            }
        }

        if (techMineInterval > 0f) {
            mineSpawnTimer -= dt
            if (mineSpawnTimer <= 0f) {
                mineSpawnTimer = techMineInterval
                mines.add(TechMine(Vec3(playerPos.x, 0.1f, playerPos.z), damage = attackDamage * 2.1f * mineBoostMult, radius = 4.2f * mineBoostMult))
            }
        }
        val mIter = mines.iterator()
        while (mIter.hasNext()) {
            val m = mIter.next()
            m.life -= dt
            if (m.life <= 0f) { mIter.remove(); continue }
            val triggered = enemies.any { it.pos.distTo(m.pos.x, m.pos.z) < 1.8f }
            if (triggered) {
                shockwaves.add(ShockwaveVfx(m.pos.x, 0.25f, m.pos.z, 0.4f, m.radius, 0.32f, 0.32f, floatArrayOf(0.95f, 0.55f, 0.15f)))
                damageEnemiesInRadius(m.pos.x, m.pos.z, m.radius, m.damage)
                spawnSparks(m.pos.x, 0.5f, m.pos.z, floatArrayOf(0.95f, 0.55f, 0.15f), 10)
                mIter.remove()
            }
        }

        if (gravityFieldLevel > 0) {
            gravityFieldTimer -= dt
            if (gravityFieldTimer <= 0f) {
                gravityFieldTimer = 3.2f
                val rad = 6.5f + gravityFieldLevel
                shockwaves.add(ShockwaveVfx(playerPos.x, 0.25f, playerPos.z, rad, 0.8f, 0.55f, 0.55f, floatArrayOf(0.55f, 0.45f, 0.9f)))
                enemies.forEach { e ->
                    val dx = playerPos.x - e.pos.x
                    val dz = playerPos.z - e.pos.z
                    val d = sqrt(dx * dx + dz * dz)
                    if (d in 1.2f..rad) {
                        e.pos.x += (dx / d) * 2.2f
                        e.pos.z += (dz / d) * 2.2f
                        applyDamageToEnemy(e, attackDamage * 0.9f * gravityFieldLevel, isCrit = false)
                    }
                }
            }
        }
    }

    private fun updatePetCompanion(dt: Float) {
        val pet = equippedPet ?: return
        val rad = Math3D.rad(playerYawDeg + 135f)
        val goalX = playerPos.x + sin(rad) * 1.45f
        val goalZ = playerPos.z + cos(rad) * 1.45f
        val dx = goalX - petPos.x
        val dz = goalZ - petPos.z
        val dist = sqrt(dx * dx + dz * dz)
        if (dist > 0.12f) {
            val followSpd = (moveSpeed * 1.25f).coerceAtLeast(8f)
            val step = min(dist, followSpd * dt)
            petPos.x += (dx / dist) * step
            petPos.z += (dz / dist) * step
            petYawDeg = Math3D.lerpAngleDeg(petYawDeg, Math3D.deg(atan2(dx, dz)), dt * 10f)
        } else {
            petYawDeg = Math3D.lerpAngleDeg(petYawDeg, playerYawDeg, dt * 6f)
        }
        petPos.y = Math3D.lerp(petPos.y, playerPos.y, (dt * 8f).coerceIn(0f, 1f))

        petActionTimer -= dt
        if (petActionTimer <= 0f) {
            when (pet.bonusType) {
                "MAGNET_STRIKE" -> {
                    petActionTimer = 2.5f
                    val target = findNearestEnemy(7.5f)
                    if (target != null) {
                        applyDamageToEnemy(target, attackDamage * 0.85f, isCrit = false)
                        shockwaves.add(ShockwaveVfx(target.pos.x, 0.4f, target.pos.z, 0.2f, 2.0f, 0.25f, 0.25f, petGlowCol))
                    }
                }
                "RAPID_SHOT" -> {
                    petActionTimer = 1.15f
                    val target = findNearestEnemy(15f)
                    if (target != null) {
                        val tx = target.pos.x - petPos.x
                        val tz = target.pos.z - petPos.z
                        val l = sqrt(tx * tx + tz * tz).coerceAtLeast(0.01f)
                        emitSound(SoundEffect.SHOT_PLASMA)
                        muzzleFlashes.add(MuzzleFlashVfx(petPos.x, petPos.y + 1.1f, petPos.z, Math3D.deg(atan2(tx, tz)), 0.28f, 0.1f, 0.1f, petGlowCol))
                        projectiles.add(
                            Projectile(
                                pos = Vec3(petPos.x, petPos.y + 1.1f, petPos.z),
                                vx = (tx / l) * 23f,
                                vz = (tz / l) * 23f,
                                fromPlayer = true,
                                damage = attackDamage * 0.55f,
                                isCrit = false,
                                pierceLeft = 0,
                                life = 0.85f,
                                color = petGlowCol,
                                scale = 0.16f
                            )
                        )
                    }
                }
                "SHIELD_PULSE" -> {
                    petActionTimer = 5.5f
                    shield = min(maxShield, shield + pet.bonusValue)
                    emitSound(SoundEffect.DEFENSE_SHIELD)
                    shockwaves.add(ShockwaveVfx(petPos.x, petPos.y + 0.5f, petPos.z, 0.3f, 2.8f, 0.35f, 0.35f, petGlowCol))
                }
                "CRIT_IGNITE" -> {
                    petActionTimer = 2.2f
                    val target = findNearestEnemy(6.5f)
                    if (target != null) {
                        target.burnTimer = 3.5f
                        target.burnDps = max(target.burnDps, attackDamage * 0.45f)
                        applyDamageToEnemy(target, attackDamage * 0.9f, isCrit = true)
                    }
                }
                "ARC_HEAL" -> {
                    petActionTimer = 1.9f
                    val target = findNearestEnemy(12f)
                    if (target != null) {
                        applyDamageToEnemy(target, attackDamage * 0.7f, isCrit = false)
                        chainStrike(target, attackDamage * 0.55f, 2)
                    }
                }
                else -> petActionTimer = 3.0f
            }
        }
    }

    private fun updateEnemies(dt: Float) {
        val iter = enemies.iterator()
        while (iter.hasNext()) {
            val e = iter.next()
            e.animTime += dt
            if (e.hitFlash > 0f) e.hitFlash = max(0f, e.hitFlash - dt)

            if (e.burnTimer > 0f) {
                e.burnTimer -= dt
                e.hp -= e.burnDps * dt
            }
            if (e.corrosiveTimer > 0f) e.corrosiveTimer -= dt
            if (e.cryoTimer > 0f) e.cryoTimer -= dt

            if (e.hp <= 0f) {
                handleEnemyDefeated(e)
                iter.remove()
                continue
            }

            if (e.stunTimer > 0f) {
                e.stunTimer -= dt
                continue
            }

            val dx = playerPos.x - e.pos.x
            val dz = playerPos.z - e.pos.z
            val dist = sqrt(dx * dx + dz * dz).coerceAtLeast(0.001f)
            e.yawDeg = Math3D.deg(atan2(dx, dz))

            val tierSpeedMult = 1f + (difficultyTier - 1) * 0.08f
            val slowFactor = if (e.cryoTimer > 0f) (1f - max(0.25f, cryoSlow)).coerceAtLeast(0.35f) else 1f
            val speed = e.def.speed * tierSpeedMult * slowFactor * (if (e.isBoss && e.phase2) 1.25f else 1f)

            if (e.isBoss && e.bossDashTimer > 0f) {
                e.bossDashTimer -= dt
                val dashSpd = speed * 3.4f
                e.pos.x += e.bossDashDirX * dashSpd * dt
                e.pos.z += e.bossDashDirZ * dashSpd * dt
                for (box in world.colliders) {
                    box.resolveCircleXZ(e.pos, 0.55f * e.def.scale)
                }
                if (rng.nextFloat() < 0.45f) {
                    spawnSparks(e.pos.x, 0.5f, e.pos.z, parseHexColor(e.def.glowHex), 2)
                }
                if (dist < 2.2f) {
                    damagePlayer(e.def.damage * (1f + (difficultyTier - 1) * 0.16f) * 1.15f, e)
                    e.bossDashTimer = 0f
                }
                continue
            }

            val desiredStopDist = when {
                e.isBoss -> if (e.def.id == "seraph_x") 8.2f else 5.8f
                e.def.aiState == "SENTRY_TURRET" -> 999f
                e.def.aiState == "RANGED_HOVER" || e.def.aiState == "RANGED_ADVANCE" -> e.def.attackRange * 0.72f
                else -> 1.15f
            }

            if (dist > desiredStopDist && speed > 0f) {
                e.pos.x += (dx / dist) * speed * dt
                e.pos.z += (dz / dist) * speed * dt
                for (box in world.colliders) {
                    box.resolveCircleXZ(e.pos, 0.55f * e.def.scale)
                }
            }

            if (e.isBoss && !e.phase2 && e.hp <= e.maxHp * 0.5f) {
                e.phase2 = true
                statusBannerText = "${e.def.name}: FASE 2 — SOBRECARGA MÁXIMA!"
                statusBannerTimer = 2.8f
                shockwaves.add(ShockwaveVfx(e.pos.x, 0.4f, e.pos.z, 1.0f, 11.0f, 0.55f, 0.55f, floatArrayOf(0.95f, 0.25f, 0.25f)))
            }

            val tierDmgMult = 1f + (difficultyTier - 1) * 0.16f
            e.attackTimer -= dt
            if (e.attackTimer <= 0f) {
                if (e.isBoss) {
                    executeUniqueBossPattern(e, dx, dz, dist, tierDmgMult)
                } else if (e.def.aiState == "SUICIDE_DETONATE" && dist <= e.def.attackRange) {
                    shockwaves.add(ShockwaveVfx(e.pos.x, 0.4f, e.pos.z, 0.5f, 4.0f, 0.35f, 0.35f, floatArrayOf(0.95f, 0.65f, 0.1f)))
                    spawnSparks(e.pos.x, 0.8f, e.pos.z, floatArrayOf(0.95f, 0.65f, 0.1f), 14)
                    if (dist < 3.6f) damagePlayer(e.def.damage * tierDmgMult, e)
                    iter.remove()
                    continue
                } else if ((e.def.aiState == "RANGED_HOVER" || e.def.aiState == "RANGED_ADVANCE" || e.def.aiState == "SENTRY_TURRET") && dist <= e.def.attackRange) {
                    e.attackTimer = (1.85f / tierSpeedMult).coerceAtLeast(1.05f)
                    val glowCol = parseHexColor(e.def.glowHex)
                    emitSound(SoundEffect.SHOT_ENEMY)
                    muzzleFlashes.add(MuzzleFlashVfx(e.pos.x, 1.15f, e.pos.z, e.yawDeg, 0.28f, 0.1f, 0.1f, glowCol))
                    projectiles.add(
                        Projectile(
                            pos = Vec3(e.pos.x, 1.15f, e.pos.z),
                            vx = (dx / dist) * 11.5f * tierSpeedMult,
                            vz = (dz / dist) * 11.5f * tierSpeedMult,
                            fromPlayer = false,
                            damage = e.def.damage * tierDmgMult,
                            isCrit = false,
                            pierceLeft = 0,
                            life = 2.1f,
                            color = glowCol,
                            scale = 0.20f
                        )
                    )
                } else if (dist <= e.def.attackRange + 0.6f && kotlin.math.abs(playerPos.y - e.pos.y) <= 1.85f) {
                    e.attackTimer = 1.05f
                    emitSound(SoundEffect.STRIKE_SLASH)
                    damagePlayer(e.def.damage * tierDmgMult, e)
                }
            }
        }
    }

    private fun executeUniqueBossPattern(
        e: ActiveEnemy,
        dx: Float,
        dz: Float,
        dist: Float,
        tierDmgMult: Float
    ) {
        val glow = parseHexColor(e.def.glowHex)
        val baseAng = atan2(dx, dz)
        val dmg = e.def.damage * tierDmgMult
        val cdScale = (if (e.phase2) 0.72f else 1.0f) / (1f + (difficultyTier - 1) * 0.10f)
        val pattern = e.bossAttackCycle % 3
        e.bossAttackCycle += 1

        when (e.def.id) {
            "omega_01" -> {
                // OMEGA-01: Siege Spread / Bull Charge Dash / Double Seismic Tremor Nova
                when (pattern) {
                    0 -> {
                        e.attackTimer = 1.8f * cdScale
                        val count = if (e.phase2) 7 else 5
                        emitSound(SoundEffect.SHOT_ENEMY)
                        muzzleFlashes.add(MuzzleFlashVfx(e.pos.x, 1.6f, e.pos.z, e.yawDeg, 0.65f, 0.14f, 0.14f, glow))
                        for (i in 0 until count) {
                            val ang = baseAng + (i - count / 2f) * 0.24f
                            projectiles.add(Projectile(Vec3(e.pos.x, 1.55f, e.pos.z), sin(ang) * 12.5f, cos(ang) * 12.5f, false, dmg, false, 0, 2.5f, glow, 0.36f))
                        }
                    }
                    1 -> {
                        e.attackTimer = 2.1f * cdScale
                        e.bossDashTimer = 0.62f
                        e.bossDashDirX = dx / dist
                        e.bossDashDirZ = dz / dist
                        shockwaves.add(ShockwaveVfx(e.pos.x, 0.35f, e.pos.z, 0.8f, 5.2f, 0.35f, 0.35f, floatArrayOf(1f, 0.45f, 0.15f)))
                    }
                    else -> {
                        e.attackTimer = 2.2f * cdScale
                        shockwaves.add(ShockwaveVfx(e.pos.x, 0.35f, e.pos.z, 1.0f, 9.8f, 0.55f, 0.55f, floatArrayOf(0.95f, 0.22f, 0.22f)))
                        shockwaves.add(ShockwaveVfx(e.pos.x, 0.55f, e.pos.z, 0.5f, 6.2f, 0.45f, 0.45f, glow))
                        if (dist < 9.5f && playerPos.y < 1.8f) {
                            damagePlayer(dmg * 1.25f, e)
                        }
                    }
                }
            }
            "seraph_x" -> {
                // SERAPH-X: Radial Spiral Lasers / Rapid Aimed Lance / Aerial Dive Blast
                when (pattern) {
                    0 -> {
                        e.attackTimer = 1.65f * cdScale
                        val bolts = if (e.phase2) 12 else 8
                        e.bossSpiralOffset += 0.45f
                        for (i in 0 until bolts) {
                            val ang = e.bossSpiralOffset + (2f * Math.PI.toFloat() * i / bolts)
                            projectiles.add(Projectile(Vec3(e.pos.x, 1.8f, e.pos.z), sin(ang) * 11.5f, cos(ang) * 11.5f, false, dmg * 0.9f, false, 0, 2.7f, glow, 0.32f))
                        }
                    }
                    1 -> {
                        e.attackTimer = 1.5f * cdScale
                        muzzleFlashes.add(MuzzleFlashVfx(e.pos.x, 1.8f, e.pos.z, e.yawDeg, 0.6f, 0.14f, 0.14f, glow))
                        for (k in -1..1) {
                            val ang = baseAng + k * 0.08f
                            val spd = 17.5f + kotlin.math.abs(k) * 1.5f
                            projectiles.add(Projectile(Vec3(e.pos.x, 1.7f, e.pos.z), sin(ang) * spd, cos(ang) * spd, false, dmg * 1.1f, false, 0, 2.2f, floatArrayOf(0.95f, 0.35f, 0.95f), 0.30f))
                        }
                    }
                    else -> {
                        e.attackTimer = 2.0f * cdScale
                        e.bossDashTimer = 0.48f
                        e.bossDashDirX = dx / dist
                        e.bossDashDirZ = dz / dist
                        shockwaves.add(ShockwaveVfx(playerPos.x, 0.3f, playerPos.z, 0.6f, 6.2f, 0.55f, 0.55f, glow))
                        if (dist < 7.5f) damagePlayer(dmg * 1.1f, e)
                    }
                }
            }
            else -> {
                // TITAN ZERO: Rotational Cross Streams / Triple EMP Cataclysm / Overload Ring Burst
                when (pattern) {
                    0 -> {
                        e.attackTimer = 1.75f * cdScale
                        val arms = if (e.phase2) 6 else 4
                        e.bossSpiralOffset += 0.35f
                        for (a in 0 until arms) {
                            val ang = e.bossSpiralOffset + (2f * Math.PI.toFloat() * a / arms)
                            for (layer in 0..1) {
                                val spd = 10.5f + layer * 4.5f
                                projectiles.add(Projectile(Vec3(e.pos.x, 1.65f, e.pos.z), sin(ang) * spd, cos(ang) * spd, false, dmg, false, 0, 2.8f, glow, 0.36f))
                            }
                        }
                    }
                    1 -> {
                        e.attackTimer = 2.15f * cdScale
                        shockwaves.add(ShockwaveVfx(e.pos.x, 0.35f, e.pos.z, 1.0f, 11.5f, 0.6f, 0.6f, floatArrayOf(0.95f, 0.25f, 0.35f)))
                        shockwaves.add(ShockwaveVfx(e.pos.x, 0.55f, e.pos.z, 1.5f, 8.2f, 0.48f, 0.48f, floatArrayOf(0.95f, 0.72f, 0.25f)))
                        if (dist < 10.5f && playerPos.y < 2.1f) {
                            damagePlayer(dmg * 1.3f, e)
                        }
                    }
                    else -> {
                        e.attackTimer = 1.65f * cdScale
                        val count = if (e.phase2) 9 else 6
                        muzzleFlashes.add(MuzzleFlashVfx(e.pos.x, 1.65f, e.pos.z, e.yawDeg, 0.75f, 0.15f, 0.15f, glow))
                        for (i in 0 until count) {
                            val ang = baseAng + (i - count / 2f) * 0.20f
                            projectiles.add(Projectile(Vec3(e.pos.x, 1.6f, e.pos.z), sin(ang) * 14f, cos(ang) * 14f, false, dmg * 1.05f, false, 0, 2.5f, floatArrayOf(1f, 0.25f, 0.35f), 0.38f))
                        }
                    }
                }
            }
        }
    }

    private fun updateProjectiles(dt: Float) {
        val iter = projectiles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.life -= dt
            p.pos.x += p.vx * dt
            p.pos.z += p.vz * dt
            if (p.life <= 0f) { iter.remove(); continue }

            var hitWall = false
            for (box in world.colliders) {
                if (p.pos.x in box.minX..box.maxX && p.pos.z in box.minZ..box.maxZ && p.pos.y <= box.maxY) {
                    hitWall = true
                    break
                }
            }
            if (hitWall) {
                spawnSparks(p.pos.x, p.pos.y, p.pos.z, p.color, 4)
                iter.remove()
                continue
            }

            if (p.fromPlayer) {
                var removed = false
                for (e in enemies) {
                    val hitRadius = 0.72f * e.def.scale + p.scale
                    if (e.pos.distTo(p.pos.x, p.pos.z) <= hitRadius) {
                        applyDamageToEnemy(e, p.damage, p.isCrit)
                        if (p.electricBurst) {
                            damageEnemiesInRadius(e.pos.x, e.pos.z, 3.2f, p.damage * 0.4f)
                            shockwaves.add(ShockwaveVfx(e.pos.x, 0.6f, e.pos.z, 0.3f, 3.2f, 0.25f, 0.25f, floatArrayOf(0.22f, 0.75f, 0.97f)))
                        }
                        if (p.chainCount > 0) {
                            chainStrike(e, p.damage * 0.55f, p.chainCount)
                        }
                        if (p.ricochetShards > 0) {
                            spawnRicochetShards(e.pos.x, e.pos.z, p.damage * 0.45f, p.color, p.ricochetShards * 2)
                        }
                        if (p.pierceLeft > 0) {
                            p.pierceLeft -= 1
                        } else {
                            iter.remove()
                            removed = true
                        }
                        break
                    }
                }
                if (removed) continue
            } else {
                if (playerPos.distTo(p.pos.x, p.pos.z) <= 0.72f + p.scale) {
                    damagePlayer(p.damage, null)
                    iter.remove()
                }
            }
        }
    }

    private fun spawnRicochetShards(x: Float, z: Float, dmg: Float, color: FloatArray, count: Int) {
        if (projectiles.size > 90) return
        for (i in 0 until count) {
            val ang = rng.nextFloat() * 2f * Math.PI.toFloat()
            projectiles.add(
                Projectile(
                    pos = Vec3(x, 1.1f, z),
                    vx = sin(ang) * 16f,
                    vz = cos(ang) * 16f,
                    fromPlayer = true,
                    damage = dmg,
                    isCrit = false,
                    pierceLeft = 0,
                    life = 0.45f,
                    color = color,
                    scale = 0.14f
                )
            )
        }
    }

    private fun chainStrike(source: ActiveEnemy, dmg: Float, jumps: Int) {
        var hits = 0
        for (other in enemies) {
            if (other.uid != source.uid && other.pos.distTo(source.pos) <= 6.5f) {
                applyDamageToEnemy(other, dmg, isCrit = false)
                spawnSparks(other.pos.x, 1.1f, other.pos.z, floatArrayOf(0.35f, 0.75f, 0.98f), 5)
                hits++
                if (hits >= jumps) break
            }
        }
    }

    private fun damageEnemiesInRadius(cx: Float, cz: Float, radius: Float, dmg: Float, isCrit: Boolean = false, stunSec: Float = 0f) {
        for (e in enemies) {
            if (e.pos.distTo(cx, cz) <= radius + 0.5f * e.def.scale) {
                applyDamageToEnemy(e, dmg, isCrit)
                if (stunSec > 0f) e.stunTimer = max(e.stunTimer, stunSec)
            }
        }
    }

    private fun applyDamageToEnemy(e: ActiveEnemy, rawDamage: Float, isCrit: Boolean) {
        val corrMult = if (e.corrosiveTimer > 0f) 1f + max(0.2f, corrosiveBonus) else 1f
        val execMult = if (executeBonus > 0f && e.hp < e.maxHp * 0.35f) 1f + executeBonus else 1f
        val bossMult = if (bossSlayerBonus > 0f && (e.isBoss || e.def.id == "tita_industrial")) 1f + bossSlayerBonus else 1f
        val finalDmg = rawDamage * corrMult * execMult * bossMult
        e.hp -= finalDmg
        e.hitFlash = 0.14f
        if (fireDotDamage > 0f) {
            e.burnTimer = 3.5f
            e.burnDps = fireDotDamage
        }
        if (corrosiveBonus > 0f || classDef.id == "hacker") {
            e.corrosiveTimer = 4.0f
        }
        if (cryoSlow > 0f) {
            e.cryoTimer = 3.0f
        }
        val impactY = 1.25f * e.def.scale
        val sparkCol = if (isCrit) floatArrayOf(0.98f, 0.80f, 0.22f) else weaponCol
        emitSound(SoundEffect.HIT_IMPACT)
        spawnSparks(e.pos.x, impactY, e.pos.z, sparkCol, if (isCrit) 9 else 4)
        if (isCrit) {
            shockwaves.add(ShockwaveVfx(e.pos.x, impactY * 0.6f, e.pos.z, 0.2f, 1.55f, 0.22f, 0.22f, sparkCol))
        }
        if (damageNumbers.size < 22) {
            damageNumbers.add(
                DamageNumber(
                    uid = nextUid++,
                    x = e.pos.x + (rng.nextFloat() - 0.5f) * 0.6f,
                    y = impactY + 0.55f,
                    z = e.pos.z + (rng.nextFloat() - 0.5f) * 0.6f,
                    amount = finalDmg.toInt().coerceAtLeast(1),
                    isCrit = isCrit,
                    isPlayerHit = false,
                    life = 0.72f,
                    maxLife = 0.72f
                )
            )
        }
    }

    private fun handleEnemyDefeated(e: ActiveEnemy) {
        kills += 1
        if (lifestealOnKill > 0f) hp = min(maxHp, hp + lifestealOnKill)
        if (shieldOnKill > 0f) shield = min(maxShield, shield + shieldOnKill)

        spawnSparks(e.pos.x, 1.0f, e.pos.z, parseHexColor(e.def.glowHex), if (e.isBoss) 36 else 12)
        if (deathExplosionDamage > 0f) {
            shockwaves.add(ShockwaveVfx(e.pos.x, 0.3f, e.pos.z, 0.4f, 3.6f, 0.3f, 0.3f, floatArrayOf(0.95f, 0.55f, 0.15f)))
            for (other in enemies) {
                if (other.uid != e.uid && other.pos.distTo(e.pos) <= 3.6f) {
                    other.hp -= deathExplosionDamage
                    other.hitFlash = 0.12f
                }
            }
        }

        pickups.add(PickupOrb(Vec3(e.pos.x, 0.5f, e.pos.z), isCredit = false, amount = e.def.xpReward))
        if (e.isBoss || rng.nextFloat() < 0.48f) {
            pickups.add(PickupOrb(Vec3(e.pos.x + 0.5f, 0.5f, e.pos.z - 0.4f), isCredit = true, amount = e.def.creditReward))
        }

        if (e.isBoss) {
            bossDuelActive = false
            bossesDefeatedInRun += 1
            difficultyTier = 1 + bossesDefeatedInRun
            nextBossElapsedTarget = elapsedSeconds + 64f
            nextBossKillsTarget = kills + 26 + bossesDefeatedInRun * 6
            statusBannerText = "${e.def.name} DERROTADO! NÍVEL DE AMEAÇA $difficultyTier!"
            statusBannerTimer = 3.8f
            shockwaves.add(ShockwaveVfx(e.pos.x, 0.4f, e.pos.z, 1.0f, 14.0f, 0.7f, 0.7f, floatArrayOf(0.22f, 0.85f, 0.98f)))
            spawnArenaCollectibleAt(e.pos.x, e.pos.z, preferPet = bossesDefeatedInRun % 2 == 1)
        }
    }

    private fun damagePlayer(rawDmg: Float, attacker: ActiveEnemy?) {
        if (dodgeTimer > 0f || status != RunStatus.PLAYING) return
        val dmg = max(2f, rawDmg - armor)
        shieldRegenDelay = 3.5f
        playerHitFlash = 0.20f

        if (shield > 0f) {
            emitSound(SoundEffect.DEFENSE_SHIELD)
            val absorbed = min(shield, dmg)
            shield -= absorbed
            hp -= (dmg - absorbed)
        } else {
            emitSound(SoundEffect.HIT_IMPACT)
            hp -= dmg
        }

        spawnSparks(playerPos.x, playerPos.y + 1.1f, playerPos.z, floatArrayOf(0.95f, 0.20f, 0.28f), 7)
        if (damageNumbers.size < 22) {
            damageNumbers.add(
                DamageNumber(
                    uid = nextUid++,
                    x = playerPos.x,
                    y = playerPos.y + 1.85f,
                    z = playerPos.z,
                    amount = dmg.toInt().coerceAtLeast(1),
                    isCrit = false,
                    isPlayerHit = true,
                    life = 0.75f,
                    maxLife = 0.75f
                )
            )
        }

        if (attacker != null && thornsRatio > 0f) {
            attacker.hp -= dmg * thornsRatio
            attacker.hitFlash = 0.12f
        }

        if (hp <= 0f) {
            hp = 0f
            playerAnimState = RigAnimState.DEFEAT
            status = RunStatus.DEFEAT
        }
    }

    private fun updatePickups(dt: Float) {
        val iter = pickups.iterator()
        while (iter.hasNext()) {
            val orb = iter.next()
            orb.life -= dt
            if (orb.life <= 0f) { iter.remove(); continue }

            val dx = playerPos.x - orb.pos.x
            val dz = playerPos.z - orb.pos.z
            val dist = sqrt(dx * dx + dz * dz)
            if (dist <= magnetRadius && dist > 0.05f) {
                val pull = 12.5f * dt
                orb.pos.x += (dx / dist) * pull
                orb.pos.z += (dz / dist) * pull
            }
            if (dist <= 1.05f) {
                if (orb.isCredit) {
                    creditsEarned += (orb.amount * creditBonusMult).toInt().coerceAtLeast(1)
                } else {
                    gainXp((orb.amount * xpBonusMult).toInt().coerceAtLeast(1))
                }
                iter.remove()
            }
        }
    }

    private fun updateArenaCollectibles(dt: Float) {
        if (!bossDuelActive && arenaCollectibles.size < 2) {
            collectibleSpawnTimer -= dt
            if (collectibleSpawnTimer <= 0f) {
                collectibleSpawnTimer = 34f
                val ang = rng.nextFloat() * 2f * Math.PI.toFloat()
                val rad = 10f + rng.nextFloat() * 14f
                val cx = (playerPos.x + cos(ang) * rad).coerceIn(-25f, 25f)
                val cz = (playerPos.z + sin(ang) * rad).coerceIn(-25f, 25f)
                spawnArenaCollectibleAt(cx, cz, preferPet = rng.nextFloat() < 0.35f)
            }
        }

        val iter = arenaCollectibles.iterator()
        while (iter.hasNext()) {
            val col = iter.next()
            col.life -= dt
            if (col.life <= 0f) { iter.remove(); continue }
            if (playerPos.distTo(col.pos.x, col.pos.z) <= 1.55f && kotlin.math.abs(playerPos.y - col.pos.y) <= 2.2f) {
                collectiblesFoundInRun += 1
                creditsEarned += 35
                gainXp(45)
                shield = min(maxShield, shield + maxShield * 0.25f)
                shockwaves.add(ShockwaveVfx(col.pos.x, col.pos.y + 0.3f, col.pos.z, 0.3f, 3.8f, 0.45f, 0.45f, col.color))
                spawnSparks(col.pos.x, col.pos.y + 0.6f, col.pos.z, col.color, 18)

                if (col.isPetCapsule) {
                    unlockedPetCache.add(col.targetId)
                    statusBannerText = "MASCOTE COLETADO: ${col.label.uppercase()}!"
                } else {
                    discoveredRelicCache.add(col.targetId)
                    statusBannerText = "COLECIONÁVEL: ${col.label.uppercase()} (+35 CR)"
                }
                statusBannerTimer = 3.5f
                emitSound(SoundEffect.COLLECTIBLE_CHIME)
                onCollectibleFound(col.isPetCapsule, col.targetId)
                iter.remove()
            }
        }
    }

    private fun spawnArenaCollectibleAt(x: Float, z: Float, preferPet: Boolean) {
        val lockedPets = catalog.pets.filter { it.id !in unlockedPetCache }
        if (preferPet && lockedPets.isNotEmpty()) {
            val pet = lockedPets[rng.nextInt(lockedPets.size)]
            val pos = Vec3(x, 0.55f, z)
            for (box in world.colliders) {
                if (box.overlapsFootprintXZ(x, z, 0.5f) && box.maxY <= 5.5f) {
                    pos.y = box.maxY + 0.55f
                    break
                }
            }
            arenaCollectibles.add(ArenaCollectible(nextUid++, pos, true, pet.id, pet.name, parseHexColor(pet.glowHex)))
            return
        }

        val relicPool = catalog.relics.filter { it.id !in discoveredRelicCache }.ifEmpty { catalog.relics }
        if (relicPool.isEmpty()) return
        val relic = relicPool[rng.nextInt(relicPool.size)]
        val pos = Vec3(x, 0.55f, z)
        for (box in world.colliders) {
            if (box.overlapsFootprintXZ(x, z, 0.5f) && box.maxY <= 5.5f) {
                pos.y = box.maxY + 0.55f
                break
            }
        }
        arenaCollectibles.add(ArenaCollectible(nextUid++, pos, false, relic.id, relic.name, parseHexColor(relic.colorHex)))
    }

    private fun gainXp(amount: Int) {
        xp += amount
        if (xp >= xpToNext) {
            xp -= xpToNext
            level += 1
            xpToNext = (xpToNext * 1.28f + 35).toInt()
            pendingChoices = rollEvolutionCards(3)
            if (pendingChoices.isNotEmpty()) {
                emitSound(SoundEffect.POWER_SKILL)
                status = RunStatus.LEVEL_UP
            }
        }
    }

    private fun rollEvolutionCards(count: Int): List<EvolutionCardDef> {
        val allAvailable = catalog.cards.filter { (cardStacks[it.id] ?: 0) < it.maxStacks }
        if (allAvailable.isEmpty()) return emptyList()
        val deckAvailable = allAvailable.filter { it.id in deckCardIds }
        val pool = (if (deckAvailable.size >= count) deckAvailable else allAvailable).toMutableList()
        val chosen = mutableListOf<EvolutionCardDef>()
        repeat(min(count, pool.size)) {
            val totalWeight = pool.sumOf { it.rarity.weight }
            var roll = rng.nextInt(totalWeight.coerceAtLeast(1))
            var pick = pool.first()
            for (c in pool) {
                roll -= c.rarity.weight
                if (roll < 0) { pick = c; break }
            }
            chosen.add(pick)
            pool.remove(pick)
        }
        return chosen
    }

    private fun updateVfx(dt: Float) {
        val sIter = shockwaves.iterator()
        while (sIter.hasNext()) {
            val s = sIter.next()
            s.life -= dt
            if (s.life <= 0f) sIter.remove()
        }
        val slIter = slashArcs.iterator()
        while (slIter.hasNext()) {
            val sl = slIter.next()
            sl.life -= dt
            if (sl.life <= 0f) slIter.remove()
        }
        val mIter = muzzleFlashes.iterator()
        while (mIter.hasNext()) {
            val mf = mIter.next()
            mf.life -= dt
            if (mf.life <= 0f) mIter.remove()
        }
        val dIter = damageNumbers.iterator()
        while (dIter.hasNext()) {
            val dn = dIter.next()
            dn.life -= dt
            dn.y += 1.65f * dt
            if (dn.life <= 0f) dIter.remove()
        }
        val pIter = sparks.iterator()
        while (pIter.hasNext()) {
            val p = pIter.next()
            p.life -= dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.z += p.vz * dt
            p.vy -= 9.5f * dt
            if (p.life <= 0f || p.y < 0f) pIter.remove()
        }
    }

    private fun spawnSparks(x: Float, y: Float, z: Float, color: FloatArray, count: Int) {
        if (sparks.size > 180) return
        repeat(count) {
            sparks.add(
                ParticleSpark(
                    x = x, y = y, z = z,
                    vx = (rng.nextFloat() - 0.5f) * 8f,
                    vy = rng.nextFloat() * 5f + 1.5f,
                    vz = (rng.nextFloat() - 0.5f) * 8f,
                    life = 0.28f + rng.nextFloat() * 0.30f,
                    color = color
                )
            )
        }
    }

    private fun updateWaveDirector(dt: Float) {
        if (!bossDuelActive && catalog.bosses.isNotEmpty() &&
            (elapsedSeconds >= nextBossElapsedTarget || kills >= nextBossKillsTarget)
        ) {
            val bossDef = catalog.bosses[bossesDefeatedInRun % catalog.bosses.size]
            spawnBoss(bossDef)
            return
        }

        // Strict 1v1 Boss Duel: stop regular mob spawns whenever a boss is active!
        if (bossDuelActive) return

        spawnTimer -= dt
        val maxActive = (12 + waveNumber * 3 + (difficultyTier - 1) * 4).coerceAtMost(36)
        if (spawnTimer <= 0f && enemies.size < maxActive) {
            spawnTimer = (2.1f - waveNumber * 0.14f - (difficultyTier - 1) * 0.16f).coerceAtLeast(0.55f)
            val unlockedPool = when {
                elapsedSeconds < 25f -> listOf("rastejador", "drone_voador", "soldado_robotico")
                elapsedSeconds < 50f -> listOf("rastejador", "drone_voador", "soldado_robotico", "aranha_industrial", "robo_explosivo")
                else -> catalog.enemies.map { it.id }
            }
            val pickId = unlockedPool[rng.nextInt(unlockedPool.size)]
            val def = catalog.enemies.firstOrNull { it.id == pickId } ?: return
            val ang = rng.nextFloat() * 2f * Math.PI.toFloat()
            val rad = 16f + rng.nextFloat() * 8f
            val sx = (playerPos.x + cos(ang) * rad).coerceIn(-30f, 30f)
            val sz = (playerPos.z + sin(ang) * rad).coerceIn(-30f, 30f)
            spawnEnemyAt(def, isBoss = false, sx, sz)
        }
    }

    private fun spawnBoss(bossDef: EnemyDef) {
        bossDuelActive = true
        // Seamless transition to 1v1 Boss Arena: dissolve remaining regular mobs without any loading screen
        val iter = enemies.iterator()
        while (iter.hasNext()) {
            val mob = iter.next()
            if (!mob.isBoss) {
                spawnSparks(mob.pos.x, 1.0f, mob.pos.z, floatArrayOf(0.55f, 0.22f, 0.85f), 8)
                iter.remove()
            }
        }

        val ang = rng.nextFloat() * 2f * Math.PI.toFloat()
        val sx = (playerPos.x + cos(ang) * 16f).coerceIn(-28f, 28f)
        val sz = (playerPos.z + sin(ang) * 16f).coerceIn(-28f, 28f)
        spawnEnemyAt(bossDef, isBoss = true, sx, sz)
        bossBannerTimer = 4.0f
        statusBannerText = "DUELO SOMBRIO: ${bossDef.name} (AMEAÇA $difficultyTier)"
        statusBannerTimer = 4.0f
        emitSound(SoundEffect.BOSS_ALERT)
        shockwaves.add(ShockwaveVfx(sx, 0.5f, sz, 1f, 13f, 0.7f, 0.7f, parseHexColor(bossDef.glowHex)))
    }

    private fun emitSound(effect: SoundEffect) {
        if (soundEnabled) {
            onSoundEvent(effect)
        }
    }

    private fun spawnEnemyAt(def: EnemyDef, isBoss: Boolean, x: Float, z: Float) {
        val tierHpScale = 1f + (difficultyTier - 1) * 0.45f
        val hpScale = if (isBoss) tierHpScale else (1f + (elapsedSeconds / 90f) * 0.45f) * tierHpScale
        val hpVal = def.maxHp * hpScale
        val pos = Vec3(x, 0f, z)
        for (box in world.colliders) {
            box.resolveCircleXZ(pos, 0.7f * def.scale)
        }
        enemies.add(
            ActiveEnemy(
                uid = nextUid++,
                def = def,
                isBoss = isBoss,
                pos = pos,
                yawDeg = 0f,
                hp = hpVal,
                maxHp = hpVal
            )
        )
    }

    fun snapshotHud(damagePopups: List<ProjectedDamageText> = emptyList()): HudSnapshot {
        val activeBoss = enemies.firstOrNull { it.isBoss }
        return HudSnapshot(
            status = status,
            hp = hp,
            maxHp = maxHp,
            shield = shield,
            maxShield = maxShield,
            xp = xp,
            xpToNext = xpToNext,
            level = level,
            creditsEarned = creditsEarned,
            kills = kills,
            elapsedSeconds = elapsedSeconds,
            waveNumber = waveNumber,
            weatherLabel = if (bossDuelActive) "Duelo Sombrio" else currentWeather.label,
            dodgeCooldown = dodgeCooldown,
            dodgeMaxCooldown = dodgeMaxCooldown * cooldownMult,
            activeCooldown = activeSkillCooldown,
            activeMaxCooldown = activeSkillMaxCooldown * cooldownMult * activeSkillCdrMult,
            activeSkillName = activeAbility?.name ?: "Habilidade",
            bossName = activeBoss?.def?.name,
            bossTitle = activeBoss?.def?.title,
            bossHp = activeBoss?.hp ?: 0f,
            bossMaxHp = activeBoss?.maxHp ?: 1f,
            bossPhase2 = activeBoss?.phase2 ?: false,
            bossBannerTimer = bossBannerTimer,
            levelUpChoices = pendingChoices,
            acquiredCards = cardStacks.toMap(),
            bossesDefeatedInRun = bossesDefeatedInRun,
            statusBannerText = statusBannerText,
            bossDuelActive = bossDuelActive,
            bossMoodBlend = bossMoodBlend,
            difficultyTier = difficultyTier,
            playerDamageFlash = (playerHitFlash / 0.20f).coerceIn(0f, 1f),
            activePetName = equippedPet?.name,
            collectiblesFoundInRun = collectiblesFoundInRun,
            damagePopups = damagePopups
        )
    }
}
