package com.example.engine3d

import com.example.data.AbilityDef
import com.example.data.CharacterClassDef
import com.example.data.CharacterPalette
import com.example.data.EnemyDef
import com.example.data.EvolutionCardDef
import com.example.data.GameCatalog
import com.example.data.GraphicsQuality
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
    var phase2: Boolean = false
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
    val statusBannerText: String? = null
)

class GameSimulation(
    private val catalog: GameCatalog,
    save: PlayerSaveData,
    val classDef: CharacterClassDef,
    val palette: CharacterPalette,
    private val onHapticEvent: () -> Unit = {},
    val skinStyle: String? = null
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

    // Fluid Parkour / Auto-Climb & Auto-Jump State
    var playerVelY: Float = 0f
    var isGrounded: Boolean = true
    var isClimbing: Boolean = false
    private var climbingBox: AABB? = null
    private var climbNormalX: Float = 0f
    private var climbNormalZ: Float = 0f
    private var wallPushTimer: Float = 0f
    private var climbHoldTimer: Float = 0f
    private var currentSupportY: Float = 0f
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

    // Spawner
    private var nextUid = 1
    private var spawnTimer = 0.8f
    private var omegaSpawned = false
    private var seraphSpawned = false
    private var titanSpawned = false
    private var bossBannerTimer = 0f
    private var statusBannerText: String? = null
    private var statusBannerTimer = 0f

    // Entities
    val enemies = mutableListOf<ActiveEnemy>()
    val projectiles = mutableListOf<Projectile>()
    val pickups = mutableListOf<PickupOrb>()
    val turrets = mutableListOf<AlliedTurret>()
    val mines = mutableListOf<TechMine>()
    val shockwaves = mutableListOf<ShockwaveVfx>()
    val sparks = mutableListOf<ParticleSpark>()
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
                shockwaves.add(ShockwaveVfx(playerPos.x, 0.25f, playerPos.z, 0.8f, r, 0.42f, 0.42f, weaponCol))
                damageEnemiesInRadius(playerPos.x, playerPos.z, r, dmg, stunSec = 1.6f)
            }
            "engenheiro" -> {
                turrets.add(AlliedTurret(Vec3(playerPos.x, 0f, playerPos.z), life = 14f + turretBonusLife, fireTimer = 0.1f, yawDeg = playerYawDeg))
                shockwaves.add(ShockwaveVfx(playerPos.x, 0.2f, playerPos.z, 0.5f, 2.8f, 0.35f, 0.35f, visorCol))
            }
            "atirador" -> {
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
        updateEnemies(dt)
        updateProjectiles(dt)
        updatePickups(dt)
        updateVfx(dt)
        updateWaveDirector(dt)
        updateCameraTransform(dt)
    }

    private fun updateWeatherCycle(dt: Float) {
        val cycleIdx = ((elapsedSeconds / 28f).toInt()) % WeatherPhase.entries.size
        currentWeather = WeatherPhase.entries[cycleIdx]
        val targetFog = graphicsQuality.fogDensity * currentWeather.fogMultiplier
        val targetRain = currentWeather.rainMultiplier
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

                // Reached roof/top ledge -> fluid automatic mantle hop onto top!
                if (playerPos.y >= box.maxY - 0.18f) {
                    playerPos.y = box.maxY + 0.08f
                    playerVelY = 5.4f
                    playerPos.x += -climbNormalX * 0.58f
                    playerPos.z += -climbNormalZ * 0.58f
                    isClimbing = false
                    climbingBox = null
                    wallPushTimer = 0f
                    isGrounded = false
                    playerAnimState = RigAnimState.JUMP
                }
            } else if (inputMag > 0.25f && pushInto < -0.22f) {
                // Pulling away from the wall -> fluid wall-jump off!
                playerVelY = 8.4f
                playerPos.x += climbNormalX * 0.48f
                playerPos.z += climbNormalZ * 0.48f
                isClimbing = false
                climbingBox = null
                wallPushTimer = 0f
                isGrounded = false
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
                            // Low/medium obstacle (stairs, cars, handrails, crates): instant fluid vault/jump onto top!
                            if (wallPushTimer >= 0.035f && (isGrounded || playerVelY <= 1.5f)) {
                                playerVelY = if (heightGap <= 0.95f) 8.4f else 10.6f
                                playerPos.x += -nx * 0.26f
                                playerPos.z += -nz * 0.26f
                                isGrounded = false
                                wallPushTimer = 0f
                                spawnSparks(playerPos.x, playerPos.y + 0.15f, playerPos.z, secondaryCol, 4)
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
                // Check if player just ran off an elevated surface -> fluid parkour jump arc off the ledge!
                val edgeSupport = computeSupportY(playerPos.x, playerPos.z, playerPos.y)
                if (wasGrounded && prevSupportY > 0.4f && edgeSupport < prevSupportY - 0.35f && inputMag > 0.32f && playerVelY <= 0.5f) {
                    playerVelY = 7.2f
                    isGrounded = false
                }

                // Vertical gravity & ground landing
                playerVelY -= 26.0f * dt
                playerPos.y += playerVelY * dt

                val groundY = computeSupportY(playerPos.x, playerPos.z, playerPos.y + 0.25f)
                if (playerPos.y <= groundY) {
                    if (!wasGrounded && playerVelY < -7.5f) {
                        spawnSparks(playerPos.x, groundY + 0.1f, playerPos.z, secondaryCol, 5)
                    }
                    playerPos.y = groundY
                    playerVelY = 0f
                    isGrounded = true
                } else {
                    isGrounded = false
                }
                currentSupportY = groundY
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
            shockwaves.add(ShockwaveVfx(playerPos.x + dirX * 1.4f, 0.9f, playerPos.z + dirZ * 1.4f, 0.6f, attackRange * 0.72f, 0.22f, 0.22f, weaponCol))
            damageEnemiesInRadius(playerPos.x + dirX * 1.3f, playerPos.z + dirZ * 1.3f, attackRange * 0.78f, dmg, isCrit = isCrit)
            if (extraProjectiles > 0) {
                spawnPlayerVolley(dirX, dirZ, dmg * 0.65f, isCrit, extraProjectiles)
            }
        } else {
            spawnPlayerVolley(dirX, dirZ, dmg, isCrit, 1 + extraProjectiles)
        }
    }

    private fun spawnPlayerVolley(dirX: Float, dirZ: Float, dmg: Float, isCrit: Boolean, count: Int) {
        val baseAngle = atan2(dirX, dirZ)
        val spreadStep = 0.14f
        val startOffset = -((count - 1) * spreadStep) * 0.5f
        val speed = if (classDef.weaponType == WeaponType.PLASMA_RIFLE) 26f else 20f
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

            val slowFactor = if (e.cryoTimer > 0f) (1f - max(0.25f, cryoSlow)).coerceAtLeast(0.35f) else 1f
            val speed = e.def.speed * slowFactor * (if (e.isBoss && e.phase2) 1.25f else 1f)

            val desiredStopDist = when {
                e.isBoss -> 6.5f
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
                statusBannerText = "${e.def.name}: SOBRECARGA DE REATOR!"
                statusBannerTimer = 2.8f
                shockwaves.add(ShockwaveVfx(e.pos.x, 0.4f, e.pos.z, 1.0f, 11.0f, 0.55f, 0.55f, floatArrayOf(0.95f, 0.25f, 0.25f)))
            }

            e.attackTimer -= dt
            if (e.attackTimer <= 0f) {
                if (e.isBoss) {
                    e.attackTimer = if (e.phase2) 1.55f else 2.2f
                    val spreadCount = if (e.phase2) 8 else 5
                    val baseAng = atan2(dx, dz)
                    for (i in 0 until spreadCount) {
                        val ang = baseAng + (i - spreadCount / 2f) * 0.22f
                        projectiles.add(
                            Projectile(
                                pos = Vec3(e.pos.x, 1.6f, e.pos.z),
                                vx = sin(ang) * 12f,
                                vz = cos(ang) * 12f,
                                fromPlayer = false,
                                damage = e.def.damage,
                                isCrit = false,
                                pierceLeft = 0,
                                life = 2.4f,
                                color = parseHexColor(e.def.glowHex),
                                scale = 0.34f
                            )
                        )
                    }
                    e.specialTimer -= 2f
                    if (e.specialTimer <= 0f) {
                        e.specialTimer = 6.0f
                        shockwaves.add(ShockwaveVfx(e.pos.x, 0.35f, e.pos.z, 1f, 8.5f, 0.5f, 0.5f, parseHexColor(e.def.glowHex)))
                        if (dist < 8.5f) damagePlayer(e.def.damage * 1.15f, e)
                    }
                } else if (e.def.aiState == "SUICIDE_DETONATE" && dist <= e.def.attackRange) {
                    shockwaves.add(ShockwaveVfx(e.pos.x, 0.4f, e.pos.z, 0.5f, 4.0f, 0.35f, 0.35f, floatArrayOf(0.95f, 0.65f, 0.1f)))
                    spawnSparks(e.pos.x, 0.8f, e.pos.z, floatArrayOf(0.95f, 0.65f, 0.1f), 14)
                    if (dist < 3.6f) damagePlayer(e.def.damage, e)
                    iter.remove()
                    continue
                } else if ((e.def.aiState == "RANGED_HOVER" || e.def.aiState == "RANGED_ADVANCE" || e.def.aiState == "SENTRY_TURRET") && dist <= e.def.attackRange) {
                    e.attackTimer = 1.85f
                    projectiles.add(
                        Projectile(
                            pos = Vec3(e.pos.x, 1.15f, e.pos.z),
                            vx = (dx / dist) * 11.5f,
                            vz = (dz / dist) * 11.5f,
                            fromPlayer = false,
                            damage = e.def.damage,
                            isCrit = false,
                            pierceLeft = 0,
                            life = 2.1f,
                            color = parseHexColor(e.def.glowHex),
                            scale = 0.20f
                        )
                    )
                } else if (dist <= e.def.attackRange + 0.6f && kotlin.math.abs(playerPos.y - e.pos.y) <= 1.85f) {
                    e.attackTimer = 1.05f
                    damagePlayer(e.def.damage, e)
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
        spawnSparks(e.pos.x, 1.0f * e.def.scale, e.pos.z, if (isCrit) floatArrayOf(0.95f, 0.78f, 0.25f) else weaponCol, if (isCrit) 6 else 3)
    }

    private fun handleEnemyDefeated(e: ActiveEnemy) {
        kills += 1
        if (lifestealOnKill > 0f) hp = min(maxHp, hp + lifestealOnKill)
        if (shieldOnKill > 0f) shield = min(maxShield, shield + shieldOnKill)

        spawnSparks(e.pos.x, 1.0f, e.pos.z, parseHexColor(e.def.glowHex), if (e.isBoss) 30 else 10)
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
            bossesDefeatedInRun += 1
            statusBannerText = "${e.def.name} DESTRUÍDO! +${e.def.creditReward} CRÉDITOS"
            statusBannerTimer = 3.5f
            if (e.def.id == "titan_zero") {
                status = RunStatus.VICTORY
            }
        }
    }

    private fun damagePlayer(rawDmg: Float, attacker: ActiveEnemy?) {
        if (dodgeTimer > 0f || status != RunStatus.PLAYING) return
        val dmg = max(2f, rawDmg - armor)
        shieldRegenDelay = 3.5f
        playerHitFlash = 0.20f

        if (shield > 0f) {
            val absorbed = min(shield, dmg)
            shield -= absorbed
            hp -= (dmg - absorbed)
        } else {
            hp -= dmg
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

    private fun gainXp(amount: Int) {
        xp += amount
        if (xp >= xpToNext) {
            xp -= xpToNext
            level += 1
            xpToNext = (xpToNext * 1.28f + 35).toInt()
            pendingChoices = rollEvolutionCards(3)
            if (pendingChoices.isNotEmpty()) {
                status = RunStatus.LEVEL_UP
            }
        }
    }

    private fun rollEvolutionCards(count: Int): List<EvolutionCardDef> {
        val allAvailable = catalog.cards.filter { (cardStacks[it.id] ?: 0) < it.maxStacks }
        if (allAvailable.isEmpty()) return emptyList()
        // Prioritize player's constructed Deck Build (75% weight boost or pool preference)
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
        if (sparks.size > 160) return
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
        if (!omegaSpawned && elapsedSeconds >= 65f) {
            omegaSpawned = true
            catalog.bosses.firstOrNull { it.id == "omega_01" }?.let { spawnBoss(it) }
        }
        if (!seraphSpawned && elapsedSeconds >= 155f) {
            seraphSpawned = true
            catalog.bosses.firstOrNull { it.id == "seraph_x" }?.let { spawnBoss(it) }
        }
        if (!titanSpawned && elapsedSeconds >= 250f) {
            titanSpawned = true
            catalog.bosses.firstOrNull { it.id == "titan_zero" }?.let { spawnBoss(it) }
        }

        spawnTimer -= dt
        val maxActive = (12 + waveNumber * 3).coerceAtMost(30)
        if (spawnTimer <= 0f && enemies.size < maxActive) {
            spawnTimer = (2.1f - waveNumber * 0.14f).coerceAtLeast(0.75f)
            val unlockedPool = when {
                elapsedSeconds < 25f -> listOf("rastejador", "drone_voador", "soldado_robotico")
                elapsedSeconds < 55f -> listOf("rastejador", "drone_voador", "soldado_robotico", "aranha_industrial", "robo_explosivo")
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
        val ang = rng.nextFloat() * 2f * Math.PI.toFloat()
        val sx = (playerPos.x + cos(ang) * 18f).coerceIn(-28f, 28f)
        val sz = (playerPos.z + sin(ang) * 18f).coerceIn(-28f, 28f)
        spawnEnemyAt(bossDef, isBoss = true, sx, sz)
        bossBannerTimer = 4.0f
        statusBannerText = "ALERTA CRÍTICO: ${bossDef.name} DETECTADO"
        statusBannerTimer = 4.0f
        shockwaves.add(ShockwaveVfx(sx, 0.5f, sz, 1f, 12f, 0.7f, 0.7f, parseHexColor(bossDef.glowHex)))
    }

    private fun spawnEnemyAt(def: EnemyDef, isBoss: Boolean, x: Float, z: Float) {
        val hpScale = if (isBoss) 1f else (1f + (elapsedSeconds / 90f) * 0.45f)
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

    fun snapshotHud(): HudSnapshot {
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
            weatherLabel = currentWeather.label,
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
            statusBannerText = statusBannerText
        )
    }
}
