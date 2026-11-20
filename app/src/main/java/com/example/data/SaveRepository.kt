package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.neonfallDataStore by preferencesDataStore(name = "neonfall_save")

class SaveRepository(private val context: Context) {

    private object Keys {
        val CREDITS = intPreferencesKey("credits")
        val SELECTED_CLASS = stringPreferencesKey("selected_class")
        val UNLOCKED_CLASSES = stringPreferencesKey("unlocked_classes")
        val UNLOCKED_SKINS = stringPreferencesKey("unlocked_skins")
        val EQUIPPED_SKINS = stringPreferencesKey("equipped_skins")
        val EQUIPPED_DECK_CARDS = stringPreferencesKey("equipped_deck_cards")
        val UPGRADE_LEVELS = stringPreferencesKey("upgrade_levels")
        val TOTAL_KILLS = intPreferencesKey("total_kills")
        val TOTAL_BOSSES = intPreferencesKey("total_bosses")
        val TOTAL_RUNS = intPreferencesKey("total_runs")
        val BEST_SECONDS = intPreferencesKey("best_seconds")
        val HIGHEST_LEVEL = intPreferencesKey("highest_level")
        val CLAIMED_ACHIEVEMENTS = stringPreferencesKey("claimed_achievements")
        val GRAPHICS_QUALITY = stringPreferencesKey("graphics_quality")
        val CAMERA_DISTANCE = floatPreferencesKey("camera_distance")
        val RAIN_ENABLED = booleanPreferencesKey("rain_enabled")
        val AUTO_ATTACK = booleanPreferencesKey("auto_attack")
        val HAPTICS = booleanPreferencesKey("haptics")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val UNLOCKED_PETS = stringPreferencesKey("unlocked_pets")
        val EQUIPPED_PET = stringPreferencesKey("equipped_pet")
        val DISCOVERED_RELICS = stringPreferencesKey("discovered_relics")
    }

    val saveFlow: Flow<PlayerSaveData> = context.neonfallDataStore.data.map { prefs ->
        val defaultSave = PlayerSaveData()
        PlayerSaveData(
            credits = prefs[Keys.CREDITS] ?: defaultSave.credits,
            selectedClassId = prefs[Keys.SELECTED_CLASS] ?: defaultSave.selectedClassId,
            unlockedClassIds = parseSet(prefs[Keys.UNLOCKED_CLASSES], defaultSave.unlockedClassIds),
            unlockedSkinKeys = parseSet(prefs[Keys.UNLOCKED_SKINS], defaultSave.unlockedSkinKeys),
            equippedSkins = parseStringMap(prefs[Keys.EQUIPPED_SKINS]),
            equippedDeckCardIds = parseSet(prefs[Keys.EQUIPPED_DECK_CARDS], defaultSave.equippedDeckCardIds),
            upgradeLevels = parseIntMap(prefs[Keys.UPGRADE_LEVELS]),
            totalKills = prefs[Keys.TOTAL_KILLS] ?: 0,
            totalBossesDefeated = prefs[Keys.TOTAL_BOSSES] ?: 0,
            totalRuns = prefs[Keys.TOTAL_RUNS] ?: 0,
            bestSurvivalSeconds = prefs[Keys.BEST_SECONDS] ?: 0,
            highestLevelReached = prefs[Keys.HIGHEST_LEVEL] ?: 1,
            claimedAchievements = parseSet(prefs[Keys.CLAIMED_ACHIEVEMENTS], emptySet()),
            graphicsQuality = prefs[Keys.GRAPHICS_QUALITY]?.let {
                runCatching { GraphicsQuality.valueOf(it) }.getOrNull()
            } ?: GraphicsQuality.ALTA,
            cameraDistance = prefs[Keys.CAMERA_DISTANCE] ?: defaultSave.cameraDistance,
            rainEnabled = prefs[Keys.RAIN_ENABLED] ?: true,
            autoAttackEnabled = prefs[Keys.AUTO_ATTACK] ?: true,
            hapticsEnabled = prefs[Keys.HAPTICS] ?: true,
            soundEnabled = prefs[Keys.SOUND_ENABLED] ?: true,
            unlockedPetIds = parseSet(prefs[Keys.UNLOCKED_PETS], defaultSave.unlockedPetIds),
            equippedPetId = prefs[Keys.EQUIPPED_PET]?.takeIf { it.isNotBlank() && it != "NONE" }
                ?: if (prefs.contains(Keys.EQUIPPED_PET)) null else defaultSave.equippedPetId,
            discoveredRelicIds = parseSet(prefs[Keys.DISCOVERED_RELICS], emptySet())
        )
    }

    suspend fun updateSave(transform: (PlayerSaveData) -> PlayerSaveData) {
        context.neonfallDataStore.edit { prefs ->
            val defaultSave = PlayerSaveData()
            val current = PlayerSaveData(
                credits = prefs[Keys.CREDITS] ?: defaultSave.credits,
                selectedClassId = prefs[Keys.SELECTED_CLASS] ?: defaultSave.selectedClassId,
                unlockedClassIds = parseSet(prefs[Keys.UNLOCKED_CLASSES], defaultSave.unlockedClassIds),
                unlockedSkinKeys = parseSet(prefs[Keys.UNLOCKED_SKINS], defaultSave.unlockedSkinKeys),
                equippedSkins = parseStringMap(prefs[Keys.EQUIPPED_SKINS]),
                equippedDeckCardIds = parseSet(prefs[Keys.EQUIPPED_DECK_CARDS], defaultSave.equippedDeckCardIds),
                upgradeLevels = parseIntMap(prefs[Keys.UPGRADE_LEVELS]),
                totalKills = prefs[Keys.TOTAL_KILLS] ?: 0,
                totalBossesDefeated = prefs[Keys.TOTAL_BOSSES] ?: 0,
                totalRuns = prefs[Keys.TOTAL_RUNS] ?: 0,
                bestSurvivalSeconds = prefs[Keys.BEST_SECONDS] ?: 0,
                highestLevelReached = prefs[Keys.HIGHEST_LEVEL] ?: 1,
                claimedAchievements = parseSet(prefs[Keys.CLAIMED_ACHIEVEMENTS], emptySet()),
                graphicsQuality = prefs[Keys.GRAPHICS_QUALITY]?.let {
                    runCatching { GraphicsQuality.valueOf(it) }.getOrNull()
                } ?: GraphicsQuality.ALTA,
                cameraDistance = prefs[Keys.CAMERA_DISTANCE] ?: defaultSave.cameraDistance,
                rainEnabled = prefs[Keys.RAIN_ENABLED] ?: true,
                autoAttackEnabled = prefs[Keys.AUTO_ATTACK] ?: true,
                hapticsEnabled = prefs[Keys.HAPTICS] ?: true,
                soundEnabled = prefs[Keys.SOUND_ENABLED] ?: true,
                unlockedPetIds = parseSet(prefs[Keys.UNLOCKED_PETS], defaultSave.unlockedPetIds),
                equippedPetId = prefs[Keys.EQUIPPED_PET]?.takeIf { it.isNotBlank() && it != "NONE" }
                    ?: if (prefs.contains(Keys.EQUIPPED_PET)) null else defaultSave.equippedPetId,
                discoveredRelicIds = parseSet(prefs[Keys.DISCOVERED_RELICS], emptySet())
            )
            val updated = transform(current)
            prefs[Keys.CREDITS] = updated.credits
            prefs[Keys.SELECTED_CLASS] = updated.selectedClassId
            prefs[Keys.UNLOCKED_CLASSES] = encodeSet(updated.unlockedClassIds)
            prefs[Keys.UNLOCKED_SKINS] = encodeSet(updated.unlockedSkinKeys)
            prefs[Keys.EQUIPPED_SKINS] = encodeStringMap(updated.equippedSkins)
            prefs[Keys.EQUIPPED_DECK_CARDS] = encodeSet(updated.equippedDeckCardIds)
            prefs[Keys.UPGRADE_LEVELS] = encodeIntMap(updated.upgradeLevels)
            prefs[Keys.TOTAL_KILLS] = updated.totalKills
            prefs[Keys.TOTAL_BOSSES] = updated.totalBossesDefeated
            prefs[Keys.TOTAL_RUNS] = updated.totalRuns
            prefs[Keys.BEST_SECONDS] = updated.bestSurvivalSeconds
            prefs[Keys.HIGHEST_LEVEL] = updated.highestLevelReached
            prefs[Keys.CLAIMED_ACHIEVEMENTS] = encodeSet(updated.claimedAchievements)
            prefs[Keys.GRAPHICS_QUALITY] = updated.graphicsQuality.name
            prefs[Keys.CAMERA_DISTANCE] = updated.cameraDistance
            prefs[Keys.RAIN_ENABLED] = updated.rainEnabled
            prefs[Keys.AUTO_ATTACK] = updated.autoAttackEnabled
            prefs[Keys.HAPTICS] = updated.hapticsEnabled
            prefs[Keys.SOUND_ENABLED] = updated.soundEnabled
            prefs[Keys.UNLOCKED_PETS] = encodeSet(updated.unlockedPetIds)
            prefs[Keys.EQUIPPED_PET] = updated.equippedPetId ?: "NONE"
            prefs[Keys.DISCOVERED_RELICS] = encodeSet(updated.discoveredRelicIds)
        }
    }

    private fun parseSet(raw: String?, fallback: Set<String>): Set<String> {
        if (raw.isNullOrBlank()) return fallback
        return runCatching {
            val arr = JSONArray(raw)
            buildSet {
                for (i in 0 until arr.length()) add(arr.getString(i))
            }
        }.getOrDefault(fallback)
    }

    private fun encodeSet(set: Set<String>): String {
        return JSONArray(set.toList()).toString()
    }

    private fun parseStringMap(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                obj.keys().forEach { k -> put(k, obj.getString(k)) }
            }
        }.getOrDefault(emptyMap())
    }

    private fun encodeStringMap(map: Map<String, String>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    private fun parseIntMap(raw: String?): Map<String, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                obj.keys().forEach { k -> put(k, obj.getInt(k)) }
            }
        }.getOrDefault(emptyMap())
    }

    private fun encodeIntMap(map: Map<String, Int>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }
}
