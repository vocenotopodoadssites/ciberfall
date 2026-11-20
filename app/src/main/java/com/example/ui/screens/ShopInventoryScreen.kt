package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.CharacterClassDef
import com.example.data.PetDef
import com.example.data.PlayerSaveData
import com.example.data.RelicDef
import com.example.data.SkinDef
import com.example.data.parseHexColor
import com.example.data.skinsForClass
import com.example.engine3d.CharacterTurntable3D
import com.example.ui.components.CyberTopBar
import com.example.ui.components.SkinBlueprintDialog
import com.example.ui.components.resolveSkinPreviewDrawable
import com.example.ui.theme.AcidGreen
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceElevated
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack

@Composable
fun ShopInventoryScreen(
    classes: List<CharacterClassDef>,
    skins: List<SkinDef>,
    pets: List<PetDef> = emptyList(),
    relics: List<RelicDef> = emptyList(),
    equippedPet: PetDef? = null,
    save: PlayerSaveData,
    onUnlockClass: (CharacterClassDef) -> Unit,
    onBuyOrEquipSkin: (String, SkinDef) -> Unit,
    onBuyOrEquipPet: (PetDef) -> Unit = {},
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0=Skins, 1=Mascotes, 2=Colecionáveis
    var activeClassId by remember { mutableStateOf(save.selectedClassId) }
    val activeClass = classes.firstOrNull { it.id == activeClassId } ?: classes.first()
    val classSkins = remember(skins, activeClass.id) { skinsForClass(skins, activeClass.id) }
    var previewSkinId by remember(activeClassId) { mutableStateOf(save.equippedSkins[activeClassId] ?: "padrao") }
    val previewSkin = classSkins.firstOrNull { it.id == previewSkinId } ?: classSkins.first()
    var previewPetId by remember { mutableStateOf(save.equippedPetId ?: pets.firstOrNull()?.id) }
    val previewPet = if (selectedTab == 1) pets.firstOrNull { it.id == previewPetId } ?: equippedPet else equippedPet
    var blueprintInspectSkin by remember { mutableStateOf<SkinDef?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CyberTopBar(title = "LOJA & COLEÇÃO", credits = save.credits, onBack = onBack)

        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // 3D Skin & Companion Turntable Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(215.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                CharacterTurntable3D(
                    classDef = activeClass,
                    skinDef = previewSkin,
                    petDef = previewPet,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = VoidBlack.copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, previewSkin.tier.color),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                ) {
                    val labelText = if (selectedTab == 1 && previewPet != null) {
                        "Mascote: ${previewPet.name} (${previewPet.rarity.label})"
                    } else {
                        "${activeClass.name} • ${previewSkin.name} (${previewSkin.tier.label})"
                    }
                    Text(
                        text = labelText,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selectedTab == 1 && previewPet != null) previewPet.rarity.color else previewSkin.tier.color,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                val activeSheetRes = resolveSkinPreviewDrawable(previewSkin.previewDrawable, activeClass.id)
                if (activeSheetRes != null && selectedTab == 0) {
                    Surface(
                        onClick = { blueprintInspectSkin = previewSkin },
                        shape = RoundedCornerShape(10.dp),
                        color = VoidBlack.copy(alpha = 0.78f),
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.65f)),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = activeSheetRes),
                                contentDescription = "Blueprint da skin",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("VER PEÇAS", style = MaterialTheme.typography.labelSmall, color = NeonCyan, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Primary Shop Category Tabs (Skins / Mascotes / Colecionáveis)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val tabs = listOf("SKINS", "MASCOTES (${pets.size})", "COLECIONÁVEIS (${save.discoveredRelicIds.size}/${relics.size})")
                tabs.forEachIndexed { index, title ->
                    FilterChip(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        label = { Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("shop_tab_$index"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonCyan.copy(alpha = 0.22f),
                            selectedLabelColor = NeonCyan
                        )
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            when (selectedTab) {
                0 -> {
                    // Class Filter Tabs
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(classes, key = { it.id }) { cls ->
                            FilterChip(
                                selected = cls.id == activeClass.id,
                                onClick = { activeClassId = cls.id },
                                label = { Text(cls.name, style = MaterialTheme.typography.labelMedium) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonMagenta.copy(alpha = 0.22f),
                                    selectedLabelColor = NeonMagenta
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    if (activeClass.id !in save.unlockedClassIds) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = CyberSurfaceElevated,
                            border = BorderStroke(1.dp, NeonAmber),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("CLASSE BLOQUEADA: ${activeClass.name}", style = MaterialTheme.typography.titleLarge, color = NeonAmber)
                                    Text(activeClass.role, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = { onUnlockClass(activeClass) },
                                    enabled = save.credits >= activeClass.unlockCost,
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonAmber, contentColor = VoidBlack)
                                ) {
                                    Text("${activeClass.unlockCost} CR", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    Text("SKINS & CONJUNTOS (${activeClass.name})", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                    Spacer(Modifier.height(8.dp))

                    classSkins.forEach { skin ->
                        val key = "${activeClass.id}:${skin.id}"
                        val owned = skin.unlockType == "FREE" || key in save.unlockedSkinKeys
                        val equipped = (save.equippedSkins[activeClass.id] ?: "padrao") == skin.id
                        val canAfford = save.credits >= skin.cost
                        val thumbRes = resolveSkinPreviewDrawable(skin.previewDrawable)

                        Surface(
                            onClick = { previewSkinId = skin.id },
                            shape = RoundedCornerShape(14.dp),
                            color = CyberSurface,
                            border = BorderStroke(1.dp, if (previewSkinId == skin.id) skin.tier.color else CyberBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                if (thumbRes != null) {
                                    Image(
                                        painter = painterResource(id = thumbRes),
                                        contentDescription = "Arte da skin ${skin.name}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                previewSkinId = skin.id
                                                blueprintInspectSkin = skin
                                            }
                                    )
                                    Spacer(Modifier.width(12.dp))
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(skin.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.width(6.dp))
                                        Text(skin.tier.label, style = MaterialTheme.typography.labelSmall, color = skin.tier.color)
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Text(skin.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    if (thumbRes != null) {
                                        Text(
                                            text = "Toque na arte para ver peças separadas",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NeonCyan.copy(alpha = 0.85f)
                                        )
                                    } else if (!owned && (skin.unlockType == "ACHIEVEMENT" || skin.unlockType == "BOSS_REWARD")) {
                                        val hint = if (skin.unlockType == "BOSS_REWARD") "Também desbloqueia derrotando Chefes" else "Também desbloqueia por Conquista"
                                        Text(hint, style = MaterialTheme.typography.labelSmall, color = NeonAmber)
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                when {
                                    equipped -> {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = AcidGreen.copy(alpha = 0.2f),
                                            border = BorderStroke(1.dp, AcidGreen)
                                        ) {
                                            Text(
                                                "EQUIPADA",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AcidGreen,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                    owned -> {
                                        Button(
                                            onClick = {
                                                previewSkinId = skin.id
                                                onBuyOrEquipSkin(activeClass.id, skin)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = VoidBlack)
                                        ) {
                                            Text("EQUIPAR", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    else -> {
                                        Button(
                                            onClick = {
                                                previewSkinId = skin.id
                                                onBuyOrEquipSkin(activeClass.id, skin)
                                            },
                                            enabled = canAfford && (activeClass.id in save.unlockedClassIds),
                                            colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta, contentColor = TextPrimary)
                                        ) {
                                            Text("${skin.cost} CR", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    Text("MASCOTES CIBERNÉTICOS", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                    Text(
                        "Acompanham seu herói na arena 3D e também podem ser encontrados em cápsulas durante o combate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))

                    pets.forEach { pet ->
                        val owned = pet.id in save.unlockedPetIds
                        val isEquipped = save.equippedPetId == pet.id
                        val canAfford = save.credits >= pet.cost
                        val glowArr = parseHexColor(pet.glowHex)
                        val accentCol = Color(glowArr[0], glowArr[1], glowArr[2])

                        Surface(
                            onClick = { previewPetId = pet.id },
                            shape = RoundedCornerShape(14.dp),
                            color = CyberSurface,
                            border = BorderStroke(1.dp, if (previewPetId == pet.id) accentCol else CyberBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(pet.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.width(8.dp))
                                        Text(pet.rarity.label, style = MaterialTheme.typography.labelSmall, color = pet.rarity.color)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(pet.passiveDesc, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                                Spacer(Modifier.width(10.dp))
                                when {
                                    isEquipped -> {
                                        Button(
                                            onClick = { onBuyOrEquipPet(pet) },
                                            colors = ButtonDefaults.buttonColors(containerColor = AcidGreen.copy(alpha = 0.22f), contentColor = AcidGreen),
                                            border = BorderStroke(1.dp, AcidGreen)
                                        ) {
                                            Text("EQUIPADO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    owned -> {
                                        Button(
                                            onClick = {
                                                previewPetId = pet.id
                                                onBuyOrEquipPet(pet)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = VoidBlack)
                                        ) {
                                            Text("EQUIPAR", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    else -> {
                                        Button(
                                            onClick = {
                                                previewPetId = pet.id
                                                onBuyOrEquipPet(pet)
                                            },
                                            enabled = canAfford,
                                            colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta, contentColor = TextPrimary)
                                        ) {
                                            Text("${pet.cost} CR", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    val foundCount = save.discoveredRelicIds.size
                    Text("RELÍQUIAS & COLECIONÁVEIS ($foundCount/${relics.size})", style = MaterialTheme.typography.labelLarge, color = NeonAmber)
                    Text(
                        "Cada relíquia encontrada nas cápsulas da arena concede +2.5% Dano e +3% Créditos permanentes!",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))

                    relics.forEach { relic ->
                        val discovered = relic.id in save.discoveredRelicIds
                        val colArr = parseHexColor(relic.colorHex)
                        val accentCol = Color(colArr[0], colArr[1], colArr[2])

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = CyberSurface,
                            border = BorderStroke(1.dp, if (discovered) accentCol else CyberBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (discovered) relic.name else "${relic.name} (Não coletado)",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (discovered) accentCol else TextSecondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(relic.category.uppercase(), style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                                    Spacer(Modifier.height(4.dp))
                                    Text(relic.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                                Spacer(Modifier.width(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (discovered) AcidGreen.copy(alpha = 0.2f) else VoidBlack.copy(alpha = 0.6f),
                                    border = BorderStroke(1.dp, if (discovered) AcidGreen else CyberBorder)
                                ) {
                                    Text(
                                        text = if (discovered) "COLETADO" else "NA ARENA",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (discovered) AcidGreen else TextSecondary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }

    val inspect = blueprintInspectSkin
    if (inspect != null) {
        val res = resolveSkinPreviewDrawable(inspect.previewDrawable, activeClass.id)
        if (res != null) {
            SkinBlueprintDialog(
                className = activeClass.name,
                skin = inspect,
                drawableRes = res,
                onDismiss = { blueprintInspectSkin = null }
            )
        }
    }
}
