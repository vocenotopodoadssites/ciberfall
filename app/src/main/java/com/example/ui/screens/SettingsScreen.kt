package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.GraphicsQuality
import com.example.data.PlayerSaveData
import com.example.ui.components.CyberPanel
import com.example.ui.components.CyberTopBar
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack

@Composable
fun SettingsScreen(
    save: PlayerSaveData,
    onUpdateQuality: (GraphicsQuality) -> Unit,
    onUpdateCameraDist: (Float) -> Unit,
    onToggleRain: (Boolean) -> Unit,
    onToggleAutoAttack: (Boolean) -> Unit,
    onToggleHaptics: (Boolean) -> Unit,
    onToggleSound: (Boolean) -> Unit = {},
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CyberTopBar(title = "CONFIGURAÇÕES", credits = save.credits, onBack = onBack)

        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            CyberPanel(borderColor = NeonCyan.copy(alpha = 0.45f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("QUALIDADE GRÁFICA 3D", style = MaterialTheme.typography.titleLarge, color = NeonCyan)
                    Text(
                        text = "Ajusta densidade da chuva, reflexos de poças no asfalto e neblina volumétrica.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GraphicsQuality.entries.forEach { q ->
                            FilterChip(
                                selected = save.graphicsQuality == q,
                                onClick = { onUpdateQuality(q) },
                                label = { Text(q.label, style = MaterialTheme.typography.labelMedium) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonCyan.copy(alpha = 0.22f),
                                    selectedLabelColor = NeonCyan
                                )
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            CyberPanel(borderColor = CyberBorder) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("DISTÂNCIA DA CÂMERA ISOMÉTRICA", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                        Text("${"%.1f".format(save.cameraDistance)}m", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                    }
                    Slider(
                        value = save.cameraDistance.coerceIn(11.0f, 32.0f),
                        onValueChange = onUpdateCameraDist,
                        valueRange = 11.0f..32.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonCyan,
                            activeTrackColor = NeonCyan
                        )
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            SettingSwitchRow(
                title = "Chuva & Partículas Cyberpunk",
                subtitle = "Ativa gotas 3D caindo na arena noturna.",
                checked = save.rainEnabled,
                onCheckedChange = onToggleRain
            )
            Spacer(Modifier.height(10.dp))
            SettingSwitchRow(
                title = "Ataque Automático Inteligente",
                subtitle = "Dispara automaticamente no inimigo mais próximo ao alcance (botão manual sempre disponível).",
                checked = save.autoAttackEnabled,
                onCheckedChange = onToggleAutoAttack
            )
            Spacer(Modifier.height(10.dp))
            SettingSwitchRow(
                title = "Feedback Tátil (Vibração)",
                subtitle = "Pulso tátil ao usar esquiva e habilidades especiais.",
                checked = save.hapticsEnabled,
                onCheckedChange = onToggleHaptics
            )
            Spacer(Modifier.height(10.dp))
            SettingSwitchRow(
                title = "Efeitos Sonoros (SFX)",
                subtitle = "Sons de golpes, tiros, defesa, poderes, pulos e passos.",
                checked = save.soundEnabled,
                onCheckedChange = onToggleSound
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    CyberPanel(borderColor = CyberBorder) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = VoidBlack,
                    checkedTrackColor = NeonMagenta
                )
            )
        }
    }
}
