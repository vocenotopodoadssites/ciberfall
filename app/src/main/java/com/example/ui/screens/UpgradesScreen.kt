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
import com.example.data.PermanentUpgradeDef
import com.example.data.PlayerSaveData
import com.example.ui.components.CyberPanel
import com.example.ui.components.CyberTopBar
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack

@Composable
fun UpgradesScreen(
    upgrades: List<PermanentUpgradeDef>,
    save: PlayerSaveData,
    onPurchase: (PermanentUpgradeDef) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CyberTopBar(title = "MELHORIAS", credits = save.credits, onBack = onBack)

        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Implantes cibernéticos permanentes aplicados a todas as suas classes.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(12.dp))

            upgrades.forEach { upg ->
                val curLevel = save.upgradeLevels[upg.id] ?: 0
                val isMax = curLevel >= upg.maxLevel
                val cost = upg.baseCost + curLevel * upg.costStep
                val canAfford = save.credits >= cost && !isMax

                CyberPanel(
                    borderColor = ElectricViolet.copy(alpha = 0.5f),
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
                                Text(upg.name, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                                Text(upg.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            }
                            Spacer(Modifier.width(12.dp))
                            Button(
                                onClick = { onPurchase(upg) },
                                enabled = canAfford,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isMax) CyberBorder else NeonCyan,
                                    contentColor = VoidBlack
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = if (isMax) "MÁX" else "$cost CR",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (pip in 1..upg.maxLevel) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(if (pip <= curLevel) NeonCyan else CyberBorder)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "NV $curLevel/${upg.maxLevel} (${upg.bonusPerLevelLabel})",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricViolet
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
