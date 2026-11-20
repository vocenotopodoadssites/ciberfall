package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.data.AbilityDef
import com.example.data.CharacterClassDef
import com.example.data.PlayerSaveData
import com.example.data.SkinDef
import com.example.data.skinsForClass
import com.example.engine3d.CharacterTurntable3D
import com.example.ui.components.CyberPanel
import com.example.ui.components.CyberTopBar
import com.example.ui.components.SkinBlueprintDialog
import com.example.ui.components.StatBarRow
import com.example.ui.components.resolveSkinPreviewDrawable
import com.example.ui.theme.AcidGreen
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberSurfaceElevated
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack

@Composable
fun CharacterSelectScreen(
    classes: List<CharacterClassDef>,
    skins: List<SkinDef>,
    abilities: Map<String, AbilityDef>,
    save: PlayerSaveData,
    onSelectClass: (String) -> Unit,
    onUnlockClass: (CharacterClassDef) -> Unit,
    onEquipSkin: (String, SkinDef) -> Unit,
    onPlay: () -> Unit,
    onBack: () -> Unit
) {
    var inspectedId by remember(save.selectedClassId) { mutableStateOf(save.selectedClassId) }
    val current = classes.firstOrNull { it.id == inspectedId } ?: classes.first()
    val isUnlocked = current.id in save.unlockedClassIds
    val classSkins = remember(skins, current.id) { skinsForClass(skins, current.id) }
    val equippedSkinId = save.equippedSkins[current.id] ?: "padrao"
    val equippedSkin = classSkins.firstOrNull { it.id == equippedSkinId } ?: classSkins.first()
    var showBlueprint by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CyberTopBar(title = "OPERADORES 3D", credits = save.credits, onBack = onBack)

        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // Interactive 3D OpenGL ES Turntable
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                CharacterTurntable3D(
                    classDef = current,
                    skinDef = equippedSkin,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = VoidBlack.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                ) {
                    Text(
                        text = "${current.name} • ${equippedSkin.name}",
                        style = MaterialTheme.typography.labelMedium,
                        color = NeonCyan,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                Text(
                    text = "Arraste para girar 360°",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                )

                val previewRes = resolveSkinPreviewDrawable(equippedSkin.previewDrawable, current.id)
                if (previewRes != null) {
                    Surface(
                        onClick = { showBlueprint = true },
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
                                painter = painterResource(id = previewRes),
                                contentDescription = "Kitbash ${current.name}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("KITBASH", style = MaterialTheme.typography.labelSmall, color = NeonCyan, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 7-Class Selector Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(classes, key = { it.id }) { cls ->
                    val unlocked = cls.id in save.unlockedClassIds
                    val selected = cls.id == current.id
                    FilterChip(
                        selected = selected,
                        onClick = {
                            inspectedId = cls.id
                            if (unlocked) onSelectClass(cls.id)
                        },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!unlocked) {
                                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.height(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(cls.name, style = MaterialTheme.typography.labelMedium)
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonCyan.copy(alpha = 0.22f),
                            selectedLabelColor = NeonCyan,
                            containerColor = CyberSurfaceElevated,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            selectedBorderColor = NeonCyan,
                            borderColor = CyberBorder
                        )
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Class Details & Stats Panel
            CyberPanel(borderColor = if (isUnlocked) NeonCyan.copy(alpha = 0.45f) else NeonAmber.copy(alpha = 0.45f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(current.role, style = MaterialTheme.typography.labelMedium, color = NeonMagenta)
                    Spacer(Modifier.height(4.dp))
                    Text(current.description, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    Spacer(Modifier.height(14.dp))

                    StatBarRow("VIDA MÁXIMA", "${current.stats.maxHp.toInt()}", current.stats.maxHp / 200f, AcidGreen)
                    Spacer(Modifier.height(8.dp))
                    StatBarRow("ESCUDO ENERGÉTICO", "${current.stats.maxShield.toInt()}", current.stats.maxShield / 100f, NeonCyan)
                    Spacer(Modifier.height(8.dp))
                    StatBarRow("DANO BASE", "${current.stats.attackDamage.toInt()}", current.stats.attackDamage / 32f, NeonMagenta)
                    Spacer(Modifier.height(8.dp))
                    StatBarRow("VELOCIDADE", "${current.stats.moveSpeed}", current.stats.moveSpeed / 8f, NeonAmber)
                    Spacer(Modifier.height(8.dp))
                    StatBarRow("TAXA CRÍTICA", "${(current.stats.critChance * 100).toInt()}%", current.stats.critChance / 0.30f, ElectricViolet)

                    Spacer(Modifier.height(14.dp))

                    val activeAb = abilities[current.activeSkillId]
                    val passiveAb = abilities[current.passiveSkillId]
                    if (activeAb != null) {
                        Text("HABILIDADE ATIVA: ${activeAb.name} (${activeAb.cooldown}s)", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                        Text(activeAb.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Spacer(Modifier.height(8.dp))
                    }
                    if (passiveAb != null) {
                        Text("PASSIVA: ${passiveAb.name}", style = MaterialTheme.typography.labelLarge, color = NeonAmber)
                        Text(passiveAb.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Quick Skin Strip
            Text("SKINS DA CLASSE", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(classSkins, key = { it.id }) { sk ->
                    val owned = sk.unlockType == "FREE" || "${current.id}:${sk.id}" in save.unlockedSkinKeys
                    val eq = equippedSkin.id == sk.id
                    FilterChip(
                        selected = eq,
                        enabled = owned,
                        onClick = { if (owned) onEquipSkin(current.id, sk) },
                        label = {
                            Text(if (owned) sk.name else "${sk.name} (Loja)", style = MaterialTheme.typography.labelSmall)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = sk.tier.color.copy(alpha = 0.25f),
                            selectedLabelColor = sk.tier.color
                        )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        // Bottom Action Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isUnlocked) {
                Button(
                    onClick = {
                        onSelectClass(current.id)
                        onPlay()
                    },
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("deploy_class_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = VoidBlack)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("ENTRAR EM COMBATE (${current.name})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            } else {
                val canAfford = save.credits >= current.unlockCost
                Button(
                    onClick = { onUnlockClass(current) },
                    enabled = canAfford,
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("unlock_class_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonAmber, contentColor = VoidBlack)
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (canAfford) "DESBLOQUEAR ${current.name} (${current.unlockCost} CR)" else "CRÉDITOS INSUFICIENTES (${current.unlockCost} CR)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showBlueprint) {
        val previewRes = resolveSkinPreviewDrawable(equippedSkin.previewDrawable, current.id)
        if (previewRes != null) {
            SkinBlueprintDialog(
                className = current.name,
                skin = equippedSkin,
                drawableRes = previewRes,
                onDismiss = { showBlueprint = false }
            )
        }
    }
}
