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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.CharacterClassDef
import com.example.data.PlayerSaveData
import com.example.data.SkinDef
import com.example.engine3d.CharacterTurntable3D
import com.example.ui.components.CreditsBadge
import com.example.ui.components.formatTimeMMSS
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceElevated
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack

@Composable
fun MainMenuScreen(
    save: PlayerSaveData,
    accountLevel: Int,
    selectedClass: CharacterClassDef,
    equippedSkin: SkinDef,
    onPlay: () -> Unit,
    onNavigateCharacters: () -> Unit,
    onNavigateDeck: () -> Unit,
    onNavigateShop: () -> Unit,
    onNavigateUpgrades: () -> Unit,
    onNavigateMissions: () -> Unit,
    onNavigateSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
    ) {
        // 1. Live 3D Character Lobby Stage (with atmospheric rain, silhouettes & class VFX)
        CharacterTurntable3D(
            classDef = selectedClass,
            skinDef = equippedSkin,
            showLobbyAtmosphere = true,
            modifier = Modifier.fillMaxSize()
        )

        // Subtle vignette gradients top & bottom for crisp legibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to VoidBlack.copy(alpha = 0.72f),
                        0.22f to Color.Transparent,
                        0.68f to Color.Transparent,
                        0.92f to VoidBlack.copy(alpha = 0.90f),
                        1.0f to VoidBlack
                    )
                )
        )

        // 2. Foreground Layout: Top Header (Level + Coins) -> Center 3D Character -> Bottom Controls & Nav Bar
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Telemetry Bar: Player Level, Coins, Missions & Settings
            Row(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Account Level & Rank
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CyberSurface.copy(alpha = 0.88f),
                    border = BorderStroke(1.dp, CyberBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = NeonCyan.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "NV $accountLevel",
                                style = MaterialTheme.typography.labelLarge,
                                color = NeonCyan,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "NEONFALL",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "RECORDE ${formatTimeMMSS(save.bestSurvivalSeconds)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                // Right Cluster: Credits + Missions + Settings
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CreditsBadge(credits = save.credits)

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = CyberSurface.copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, CyberBorder)
                    ) {
                        IconButton(
                            onClick = onNavigateMissions,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("menu_missions")
                        ) {
                            Icon(Icons.Filled.EmojiEvents, contentDescription = "Missões e Conquistas", tint = NeonAmber)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = CyberSurface.copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, CyberBorder)
                    ) {
                        IconButton(
                            onClick = onNavigateSettings,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("menu_settings")
                        ) {
                            Icon(Icons.Filled.Settings, contentDescription = "Configurações", tint = TextPrimary)
                        }
                    }
                }
            }

            // Center Open 3D Character Stage (drag to rotate 360°)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "ARRASTE PARA GIRAR 360°",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp)
                )
            }

            // Operator Info Strip + Primary Play Button above Bottom Menu Bar
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CyberSurface.copy(alpha = 0.86f),
                    border = BorderStroke(1.dp, CyberBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${selectedClass.name}  •  ${equippedSkin.name.uppercase()}",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary
                            )
                            Text(
                                text = selectedClass.role,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        Surface(
                            onClick = onNavigateDeck,
                            shape = RoundedCornerShape(8.dp),
                            color = CyberSurfaceElevated,
                            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "DECK ${save.equippedDeckCardIds.size}/12",
                                style = MaterialTheme.typography.labelMedium,
                                color = NeonCyan,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = onPlay,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("play_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = VoidBlack
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "INICIAR MISSÃO",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Grounded Bottom Navigation Bar (Início, Heróis, Deck, Loja, Melhorias)
            NavigationBar(
                containerColor = CyberSurface.copy(alpha = 0.96f),
                contentColor = TextPrimary,
                windowInsets = WindowInsets.navigationBars,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Início") },
                    label = { Text("Início", style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NeonCyan,
                        selectedTextColor = NeonCyan,
                        indicatorColor = NeonCyan.copy(alpha = 0.14f),
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateCharacters,
                    modifier = Modifier.testTag("menu_characters"),
                    icon = { Icon(Icons.Filled.Person, contentDescription = "Heróis") },
                    label = { Text("Heróis", style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationBarItemDefaults.colors(
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateDeck,
                    modifier = Modifier.testTag("menu_deck"),
                    icon = { Icon(Icons.Filled.Layers, contentDescription = "Deck de Cartas") },
                    label = { Text("Deck", style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationBarItemDefaults.colors(
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateShop,
                    modifier = Modifier.testTag("menu_shop"),
                    icon = { Icon(Icons.Filled.ShoppingBag, contentDescription = "Loja") },
                    label = { Text("Loja", style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationBarItemDefaults.colors(
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateUpgrades,
                    modifier = Modifier.testTag("menu_upgrades"),
                    icon = { Icon(Icons.Filled.Memory, contentDescription = "Melhorias") },
                    label = { Text("Melhorias", style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationBarItemDefaults.colors(
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )
            }
        }
    }
}
