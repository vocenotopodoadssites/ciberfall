package com.example.data

import android.content.Context
import org.json.JSONObject

class GameDataRepository(private val context: Context) {

    val catalog: GameCatalog by lazy { loadCatalog() }

    val permanentUpgrades: List<PermanentUpgradeDef> = listOf(
        PermanentUpgradeDef("upg_damage", "Núcleo de Potência", "+8% de dano base em todas as armas", 6, 80, 65, "+8% Dano"),
        PermanentUpgradeDef("upg_hp", "Blindagem Bio-Sintética", "+18 de vida máxima inicial", 6, 70, 55, "+18 Vida"),
        PermanentUpgradeDef("upg_shield", "Capacitor de Escudo", "+12 de escudo energético inicial", 5, 85, 65, "+12 Escudo"),
        PermanentUpgradeDef("upg_speed", "Servomotores Cinéticos", "+5% de velocidade de movimento", 5, 75, 60, "+5% Veloc."),
        PermanentUpgradeDef("upg_crit", "Mira Neural", "+4% de chance crítica base", 5, 95, 75, "+4% Crítico"),
        PermanentUpgradeDef("upg_xp", "Extrator de Dados", "+10% de ganho de experiência", 5, 90, 70, "+10% XP"),
        PermanentUpgradeDef("upg_magnet", "Bobina Magnética", "+20% de raio de coleta de gemas e créditos", 5, 65, 50, "+20% Raio")
    )

    val achievements: List<AchievementDef> = listOf(
        AchievementDef("ach_first_blood", "Protocolo Zero", "Derrote 25 máquinas hostis no total.", 100, null, 25, "KILLS"),
        AchievementDef("ach_hunter_100", "Exterminador Urbano", "Derrote 150 robôs em combate.", 220, "neon", 150, "KILLS"),
        AchievementDef("ach_survivor_180", "Sobrevivente da Chuva", "Sobreviva por 3 minutos (180s) em uma partida.", 180, null, 180, "BEST_SECONDS"),
        AchievementDef("ach_boss_omega", "Queda de OMEGA-01", "Derrote um Chefe Gigante em combate.", 350, "lendaria", 1, "BOSSES"),
        AchievementDef("ach_veteran_runs", "Resistência Ativa", "Complete 3 incursões em Neo-Vértice.", 150, null, 3, "RUNS"),
        AchievementDef("ach_level_8", "Evolução Máxima", "Alcance o Nível 8 em uma única partida.", 180, null, 8, "LEVEL")
    )

    private fun readAsset(path: String): String {
        return context.assets.open(path).bufferedReader().use { it.readText() }
    }

    private fun loadCatalog(): GameCatalog {
        val classesJson = JSONObject(readAsset("data/characters.json")).getJSONArray("classes")
        val classes = buildList {
            for (i in 0 until classesJson.length()) {
                val obj = classesJson.getJSONObject(i)
                val st = obj.getJSONObject("stats")
                val pal = obj.getJSONObject("palette")
                add(
                    CharacterClassDef(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        role = obj.getString("role"),
                        description = obj.getString("description"),
                        weaponType = WeaponType.valueOf(obj.getString("weaponType")),
                        unlockCost = obj.getInt("unlockCost"),
                        unlockedByDefault = obj.getBoolean("unlockedByDefault"),
                        stats = CharacterStats(
                            maxHp = st.getDouble("maxHp").toFloat(),
                            maxShield = st.getDouble("maxShield").toFloat(),
                            moveSpeed = st.getDouble("moveSpeed").toFloat(),
                            attackDamage = st.getDouble("attackDamage").toFloat(),
                            attackCooldown = st.getDouble("attackCooldown").toFloat(),
                            attackRange = st.getDouble("attackRange").toFloat(),
                            critChance = st.getDouble("critChance").toFloat(),
                            armor = st.getDouble("armor").toFloat()
                        ),
                        activeSkillId = obj.getString("activeSkillId"),
                        passiveSkillId = obj.getString("passiveSkillId"),
                        palette = CharacterPalette(
                            primaryHex = pal.getString("primary"),
                            secondaryHex = pal.getString("secondary"),
                            visorHex = pal.getString("visor"),
                            weaponHex = pal.getString("weapon")
                        )
                    )
                )
            }
        }

        val skinsJson = JSONObject(readAsset("data/skins.json")).getJSONArray("skins")
        val skins = buildList {
            for (i in 0 until skinsJson.length()) {
                val obj = skinsJson.getJSONObject(i)
                add(
                    SkinDef(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        tier = CardRarity.valueOf(obj.getString("tier")),
                        unlockType = obj.getString("unlockType"),
                        cost = obj.getInt("cost"),
                        description = obj.getString("description"),
                        primaryOverride = obj.optString("primaryOverride").takeIf { it.isNotBlank() && it != "null" },
                        secondaryOverride = obj.optString("secondaryOverride").takeIf { it.isNotBlank() && it != "null" },
                        visorOverride = obj.optString("visorOverride").takeIf { it.isNotBlank() && it != "null" },
                        weaponOverride = obj.optString("weaponOverride").takeIf { it.isNotBlank() && it != "null" },
                        classId = obj.optString("classId").takeIf { it.isNotBlank() && it != "null" },
                        previewDrawable = obj.optString("previewDrawable").takeIf { it.isNotBlank() && it != "null" },
                        accentStyle = obj.optString("accentStyle").takeIf { it.isNotBlank() && it != "null" }
                    )
                )
            }
        }

        val abilitiesJson = JSONObject(readAsset("data/abilities.json")).getJSONArray("abilities")
        val abilities = buildMap {
            for (i in 0 until abilitiesJson.length()) {
                val obj = abilitiesJson.getJSONObject(i)
                val ability = AbilityDef(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    description = obj.getString("description"),
                    cooldown = obj.getDouble("cooldown").toFloat(),
                    damageMult = obj.getDouble("damageMult").toFloat(),
                    radius = obj.getDouble("radius").toFloat()
                )
                put(ability.id, ability)
            }
        }

        val enemiesRoot = JSONObject(readAsset("data/enemies.json"))
        val enemiesArray = enemiesRoot.getJSONArray("enemies")
        val enemies = buildList {
            for (i in 0 until enemiesArray.length()) {
                val obj = enemiesArray.getJSONObject(i)
                add(
                    EnemyDef(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        category = obj.getString("category"),
                        aiState = obj.getString("aiState"),
                        maxHp = obj.getDouble("maxHp").toFloat(),
                        speed = obj.getDouble("speed").toFloat(),
                        damage = obj.getDouble("damage").toFloat(),
                        attackRange = obj.getDouble("attackRange").toFloat(),
                        scale = obj.getDouble("scale").toFloat(),
                        xpReward = obj.getInt("xpReward"),
                        creditReward = obj.getInt("creditReward"),
                        primaryHex = obj.getString("primaryColor"),
                        glowHex = obj.getString("glowColor")
                    )
                )
            }
        }

        val bossesArray = enemiesRoot.getJSONArray("bosses")
        val bosses = buildList {
            for (i in 0 until bossesArray.length()) {
                val obj = bossesArray.getJSONObject(i)
                add(
                    EnemyDef(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        category = "BOSS",
                        aiState = "BOSS_PHASE",
                        maxHp = obj.getDouble("maxHp").toFloat(),
                        speed = obj.getDouble("speed").toFloat(),
                        damage = obj.getDouble("damage").toFloat(),
                        attackRange = obj.getDouble("attackRange").toFloat(),
                        scale = obj.getDouble("scale").toFloat(),
                        xpReward = obj.getInt("xpReward"),
                        creditReward = obj.getInt("creditReward"),
                        primaryHex = obj.getString("primaryColor"),
                        glowHex = obj.getString("glowColor"),
                        title = obj.getString("title"),
                        phases = obj.getInt("phases"),
                        weakpointHex = obj.getString("weakpointColor")
                    )
                )
            }
        }

        val cardsJson = JSONObject(readAsset("data/cards.json")).getJSONArray("cards")
        val cards = buildList {
            for (i in 0 until cardsJson.length()) {
                val obj = cardsJson.getJSONObject(i)
                val catStr = obj.optString("category", "OFENSIVA")
                val cat = runCatching { CardCategory.valueOf(catStr) }.getOrDefault(CardCategory.OFENSIVA)
                add(
                    EvolutionCardDef(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        description = obj.getString("description"),
                        rarity = CardRarity.valueOf(obj.getString("rarity")),
                        category = cat,
                        effectType = obj.getString("effectType"),
                        value = obj.getDouble("value").toFloat(),
                        maxStacks = obj.getInt("maxStacks")
                    )
                )
            }
        }

        return GameCatalog(
            classes = classes,
            skins = skins,
            abilities = abilities,
            enemies = enemies,
            bosses = bosses,
            cards = cards
        )
    }
}
