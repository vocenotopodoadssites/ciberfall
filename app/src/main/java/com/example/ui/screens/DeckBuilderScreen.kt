package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.CardCategory
import com.example.data.EvolutionCardDef
import com.example.data.PlayerSaveData
import com.example.ui.components.CyberPanel
import com.example.ui.components.CyberTopBar
import com.example.ui.components.SkillCardView
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberSurfaceElevated
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack

@Composable
fun DeckBuilderScreen(
    cards: List<EvolutionCardDef>,
    save: PlayerSaveData,
    onToggleCard: (String) -> Unit,
    onApplyPreset: (CardCategory?) -> Unit,
    onBack: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf<CardCategory?>(null) }
    val filteredCards = remember(cards, selectedCategory) {
        if (selectedCategory == null) cards else cards.filter { it.category == selectedCategory }
    }
    val equippedCount = save.equippedDeckCardIds.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CyberTopBar(title = "DECK DE BUILD (${cards.size})", credits = save.credits, onBack = onBack)

        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .padding(horizontal = 14.dp)
        ) {
            // Active Deck Status Banner
            CyberPanel(borderColor = NeonCyan.copy(alpha = 0.45f)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "DECK ATIVO: $equippedCount / 12 CARTAS",
                                style = MaterialTheme.typography.titleLarge,
                                color = NeonCyan
                            )
                            Text(
                                text = "Monte sua build de habilidades (mín. 6, máx. 12 cartas). Apenas cartas equipadas aparecem ao subir de nível.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            OutlinedButton(
                                onClick = { onApplyPreset(CardCategory.OFENSIVA) },
                                border = BorderStroke(1.dp, CyberBorder),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("Preset DPS", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                            }
                        }
                        item {
                            OutlinedButton(
                                onClick = { onApplyPreset(CardCategory.TECNOLOGIA) },
                                border = BorderStroke(1.dp, CyberBorder),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("Preset Drones/Tech", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                            }
                        }
                        item {
                            OutlinedButton(
                                onClick = { onApplyPreset(CardCategory.ELEMENTAL) },
                                border = BorderStroke(1.dp, CyberBorder),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("Preset Elemental", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                            }
                        }
                        item {
                            OutlinedButton(
                                onClick = { onApplyPreset(CardCategory.DEFESA) },
                                border = BorderStroke(1.dp, CyberBorder),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("Preset Tanque", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Category Filter Chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("Todas (${cards.size})", style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                            selectedLabelColor = NeonCyan,
                            containerColor = CyberSurfaceElevated
                        )
                    )
                }
                items(CardCategory.entries) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat.label, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                            selectedLabelColor = NeonCyan,
                            containerColor = CyberSurfaceElevated
                        )
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 158.dp),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(filteredCards, key = { it.id }) { card ->
                    val equipped = card.id in save.equippedDeckCardIds
                    SkillCardView(
                        card = card,
                        isEquipped = equipped,
                        footerActionLabel = if (equippedCount >= 12) "CHEIO" else "EQUIPAR",
                        compact = false,
                        testTagName = "deck_card_${card.id}",
                        onClick = { onToggleCard(card.id) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
