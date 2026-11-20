package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AchievementDef
import com.example.data.CardCategory
import com.example.data.CharacterClassDef
import com.example.data.EvolutionCardDef
import com.example.data.GameCatalog
import com.example.data.GameDataRepository
import com.example.data.GraphicsQuality
import com.example.data.PermanentUpgradeDef
import com.example.data.PlayerSaveData
import com.example.data.SaveRepository
import com.example.data.SkinDef
import com.example.data.resolvePalette
import com.example.engine3d.GameSimulation
import com.example.engine3d.HudSnapshot
import com.example.engine3d.RunStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dataRepo = GameDataRepository(application)
    private val saveRepo = SaveRepository(application)

    val catalog: GameCatalog = dataRepo.catalog
    val permanentUpgrades: List<PermanentUpgradeDef> = dataRepo.permanentUpgrades
    val achievements: List<AchievementDef> = dataRepo.achievements

    val saveState: StateFlow<PlayerSaveData> = saveRepo.saveFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = PlayerSaveData()
    )

    private val _activeSimulation = MutableStateFlow<GameSimulation?>(null)
    val activeSimulation: StateFlow<GameSimulation?> = _activeSimulation.asStateFlow()

    private val _hudState = MutableStateFlow(HudSnapshot())
    val hudState: StateFlow<HudSnapshot> = _hudState.asStateFlow()

    private var runFinalized = false

    fun playerAccountLevel(save: PlayerSaveData): Int {
        val score = save.totalKills + save.totalBossesDefeated * 35 + save.totalRuns * 12
        return 1 + (score / 40)
    }

    fun selectedClass(save: PlayerSaveData): CharacterClassDef {
        return catalog.classes.firstOrNull { it.id == save.selectedClassId } ?: catalog.classes.first()
    }

    fun equippedSkin(save: PlayerSaveData, classId: String): SkinDef {
        val skinId = save.equippedSkins[classId] ?: "padrao"
        return catalog.skins.firstOrNull { it.id == skinId && (it.classId == null || it.classId == classId) }
            ?: catalog.skins.first()
    }

    fun selectClass(classId: String) {
        viewModelScope.launch {
            saveRepo.updateSave { s ->
                if (classId in s.unlockedClassIds) s.copy(selectedClassId = classId) else s
            }
        }
    }

    fun unlockClass(charDef: CharacterClassDef) {
        viewModelScope.launch {
            saveRepo.updateSave { s ->
                if (charDef.id in s.unlockedClassIds || s.credits < charDef.unlockCost) s
                else s.copy(
                    credits = s.credits - charDef.unlockCost,
                    unlockedClassIds = s.unlockedClassIds + charDef.id,
                    unlockedSkinKeys = s.unlockedSkinKeys + "${charDef.id}:padrao",
                    selectedClassId = charDef.id
                )
            }
        }
    }

    fun buyOrEquipSkin(classId: String, skin: SkinDef) {
        val key = "$classId:${skin.id}"
        viewModelScope.launch {
            saveRepo.updateSave { s ->
                if (skin.unlockType == "FREE" || key in s.unlockedSkinKeys) {
                    s.copy(
                        unlockedSkinKeys = s.unlockedSkinKeys + key,
                        equippedSkins = s.equippedSkins + (classId to skin.id)
                    )
                } else if (s.credits >= skin.cost) {
                    s.copy(
                        credits = s.credits - skin.cost,
                        unlockedSkinKeys = s.unlockedSkinKeys + key,
                        equippedSkins = s.equippedSkins + (classId to skin.id)
                    )
                } else {
                    s
                }
            }
        }
    }

    fun toggleDeckCard(cardId: String) {
        viewModelScope.launch {
            saveRepo.updateSave { s ->
                val cur = s.equippedDeckCardIds
                val next = if (cardId in cur) {
                    if (cur.size <= 6) cur else cur - cardId
                } else {
                    if (cur.size >= 12) cur else cur + cardId
                }
                s.copy(equippedDeckCardIds = next)
            }
        }
    }

    fun applyDeckPreset(category: CardCategory?) {
        val chosen = if (category == null) {
            catalog.cards.shuffled().take(12).map { it.id }.toSet()
        } else {
            val primary = catalog.cards.filter { it.category == category }.take(9)
            val fill = catalog.cards.filter { it.category != category }.take(12 - primary.size)
            (primary + fill).map { it.id }.toSet()
        }
        viewModelScope.launch {
            saveRepo.updateSave { it.copy(equippedDeckCardIds = chosen) }
        }
    }

    fun purchaseUpgrade(upg: PermanentUpgradeDef) {
        viewModelScope.launch {
            saveRepo.updateSave { s ->
                val curLvl = s.upgradeLevels[upg.id] ?: 0
                if (curLvl >= upg.maxLevel) return@updateSave s
                val cost = upg.baseCost + curLvl * upg.costStep
                if (s.credits < cost) return@updateSave s
                s.copy(
                    credits = s.credits - cost,
                    upgradeLevels = s.upgradeLevels + (upg.id to (curLvl + 1))
                )
            }
        }
    }

    fun achievementMetricValue(ach: AchievementDef, save: PlayerSaveData): Int {
        return when (ach.metric) {
            "KILLS" -> save.totalKills
            "BOSSES" -> save.totalBossesDefeated
            "RUNS" -> save.totalRuns
            "BEST_SECONDS" -> save.bestSurvivalSeconds
            "LEVEL" -> save.highestLevelReached
            else -> 0
        }
    }

    fun claimAchievement(ach: AchievementDef) {
        viewModelScope.launch {
            saveRepo.updateSave { s ->
                if (ach.id in s.claimedAchievements) return@updateSave s
                if (achievementMetricValue(ach, s) < ach.targetValue) return@updateSave s
                var unlockedSkins = s.unlockedSkinKeys
                if (ach.rewardSkinId != null) {
                    val newKeys = catalog.classes.map { "${it.id}:${ach.rewardSkinId}" }
                    unlockedSkins = unlockedSkins + newKeys
                }
                s.copy(
                    credits = s.credits + ach.rewardCredits,
                    unlockedSkinKeys = unlockedSkins,
                    claimedAchievements = s.claimedAchievements + ach.id
                )
            }
        }
    }

    fun updateSettings(
        quality: GraphicsQuality? = null,
        cameraDistance: Float? = null,
        rainEnabled: Boolean? = null,
        autoAttack: Boolean? = null,
        haptics: Boolean? = null
    ) {
        viewModelScope.launch {
            saveRepo.updateSave { s ->
                val updated = s.copy(
                    graphicsQuality = quality ?: s.graphicsQuality,
                    cameraDistance = cameraDistance ?: s.cameraDistance,
                    rainEnabled = rainEnabled ?: s.rainEnabled,
                    autoAttackEnabled = autoAttack ?: s.autoAttackEnabled,
                    hapticsEnabled = haptics ?: s.hapticsEnabled
                )
                _activeSimulation.value?.let { sim ->
                    sim.graphicsQuality = updated.graphicsQuality
                    sim.desiredCameraDistance = updated.cameraDistance.coerceIn(11f, 32f)
                    sim.rainEnabled = updated.rainEnabled
                    sim.autoAttackEnabled = updated.autoAttackEnabled
                    sim.hapticsEnabled = updated.hapticsEnabled
                }
                updated
            }
        }
    }

    fun startNewRun(onHaptic: () -> Unit) {
        val save = saveState.value
        val cls = selectedClass(save)
        val skin = equippedSkin(save, cls.id)
        val pal = resolvePalette(cls, skin)
        runFinalized = false
        val sim = GameSimulation(catalog, save, cls, pal, onHaptic, skin.accentStyle)
        _activeSimulation.value = sim
        _hudState.value = sim.snapshotHud()
    }

    fun onHudSnapshot(snapshot: HudSnapshot) {
        _hudState.value = snapshot
        if (!runFinalized && (snapshot.status == RunStatus.VICTORY || snapshot.status == RunStatus.DEFEAT)) {
            finalizeRunStats(snapshot)
        }
    }

    fun togglePause() {
        val sim = _activeSimulation.value ?: return
        if (sim.status == RunStatus.PLAYING) {
            sim.status = RunStatus.PAUSED
            _hudState.value = sim.snapshotHud()
        } else if (sim.status == RunStatus.PAUSED) {
            sim.status = RunStatus.PLAYING
            _hudState.value = sim.snapshotHud()
        }
    }

    fun chooseCard(card: EvolutionCardDef) {
        val sim = _activeSimulation.value ?: return
        sim.applyCard(card)
        _hudState.value = sim.snapshotHud()
    }

    fun abandonRun() {
        val snap = _hudState.value
        if (!runFinalized) {
            finalizeRunStats(snap)
        }
        _activeSimulation.value = null
    }

    private fun finalizeRunStats(snap: HudSnapshot) {
        runFinalized = true
        val elapsed = snap.elapsedSeconds.toInt()
        val runCredits = snap.creditsEarned + (elapsed / 12) + snap.bossesDefeatedInRun * 60
        viewModelScope.launch {
            saveRepo.updateSave { s ->
                var newSkins = s.unlockedSkinKeys
                if (snap.bossesDefeatedInRun > 0) {
                    newSkins = newSkins + "${s.selectedClassId}:lendaria"
                }
                s.copy(
                    credits = s.credits + runCredits,
                    totalKills = s.totalKills + snap.kills,
                    totalBossesDefeated = s.totalBossesDefeated + snap.bossesDefeatedInRun,
                    totalRuns = s.totalRuns + 1,
                    bestSurvivalSeconds = maxOf(s.bestSurvivalSeconds, elapsed),
                    highestLevelReached = maxOf(s.highestLevelReached, snap.level),
                    unlockedSkinKeys = newSkins
                )
            }
        }
    }
}
