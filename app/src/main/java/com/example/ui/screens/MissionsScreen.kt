package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AchievementDef
import com.example.data.PlayerSaveData
import com.example.ui.components.CyberPanel
import com.example.ui.components.CyberTopBar
import com.example.ui.components.formatTimeMMSS
import com.example.ui.theme.AcidGreen
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack

@Composable
fun MissionsScreen(
    achievements: List<AchievementDef>,
    save: PlayerSaveData,
    metricValue: (AchievementDef, PlayerSaveData) -> Int,
    onClaim: (AchievementDef) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CyberTopBar(title = "MISSÕES & CONQUISTAS", credits = save.credits, onBack = onBack)

        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // Lifetime Telemetry Stats
            CyberPanel(borderColor = NeonCyan.copy(alpha = 0.45f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("TELEMETRIA DE COMBATE", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatMetric("INIMIGOS", "${save.totalKills}")
                        StatMetric("CHEFES", "${save.totalBossesDefeated}")
                        StatMetric("RECORDE", formatTimeMMSS(save.bestSurvivalSeconds))
                        StatMetric("NV MÁX", "${save.highestLevelReached}")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("CONQUISTAS E RECOMPENSAS", style = MaterialTheme.typography.labelLarge, color = NeonAmber)
            Spacer(Modifier.height(8.dp))

            achievements.forEach { ach ->
                val cur = metricValue(ach, save).coerceAtMost(ach.targetValue)
                val claimed = ach.id in save.claimedAchievements
                val completed = cur >= ach.targetValue
                val progress = (cur.toFloat() / ach.targetValue.coerceAtLeast(1)).coerceIn(0f, 1f)

                CyberPanel(
                    borderColor = when {
                        claimed -> AcidGreen.copy(alpha = 0.4f)
                        completed -> NeonAmber
                        else -> CyberBorder
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ach.title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                                Text(ach.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                val bonusSkin = if (ach.rewardSkinId != null) " + Skin ${ach.rewardSkinId.uppercase()}" else ""
                                Text(
                                    text = "Recompensa: +${ach.rewardCredits} CR$bonusSkin",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonMagenta
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            when {
                                claimed -> Text("RESGATADO", style = MaterialTheme.typography.labelMedium, color = AcidGreen)
                                completed -> Button(
                                    onClick = { onClaim(ach) },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonAmber, contentColor = VoidBlack)
                                ) {
                                    Text("RESGATAR", fontWeight = FontWeight.Bold)
                                }
                                else -> Text("$cur / ${ach.targetValue}", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(CyberBorder.copy(alpha = 0.5f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress.coerceAtLeast(0.02f))
                                    .height(6.dp)
                                    .background(if (completed) AcidGreen else NeonCyan)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun StatMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}
