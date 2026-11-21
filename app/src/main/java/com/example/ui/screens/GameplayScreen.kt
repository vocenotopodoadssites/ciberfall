package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.example.data.EvolutionCardDef
import com.example.data.GraphicsQuality
import com.example.data.PlayerSaveData
import com.example.engine3d.GameSimulation
import com.example.engine3d.HudSnapshot
import com.example.engine3d.Neonfall3DViewport
import com.example.engine3d.RunStatus
import com.example.ui.components.SkillCardView
import com.example.ui.components.formatTimeMMSS
import com.example.ui.theme.AcidGreen
import com.example.ui.theme.CrimsonDanger
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack
import kotlin.math.sqrt

@Composable
fun GameplayScreen(
    sim: GameSimulation,
    hud: HudSnapshot,
    save: PlayerSaveData,
    onHudTick: (HudSnapshot) -> Unit,
    onTogglePause: () -> Unit,
    onSelectCard: (EvolutionCardDef) -> Unit,
    onUpdateQuality: (GraphicsQuality) -> Unit,
    onUpdateCameraDist: (Float) -> Unit,
    onToggleSound: (Boolean) -> Unit = {},
    onPlayAgain: () -> Unit,
    onExitToMenu: () -> Unit
) {
    BackHandler {
        if (hud.status == RunStatus.PLAYING || hud.status == RunStatus.PAUSED) {
            onTogglePause()
        } else if (hud.status == RunStatus.VICTORY || hud.status == RunStatus.DEFEAT) {
            onExitToMenu()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(VoidBlack)) {
        // 1. Live 3D OpenGL ES 3.0 Isometric Arena Viewport
        Neonfall3DViewport(
            sim = sim,
            onHudTick = onHudTick,
            modifier = Modifier.fillMaxSize()
        )

        // 1b. Brief Crimson Player Hit Flash (no dark screen overlay during boss fights)
        if (hud.playerDamageFlash > 0.05f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            0.55f to Color.Transparent,
                            1.0f to CrimsonDanger.copy(alpha = 0.36f * hud.playerDamageFlash)
                        )
                    )
            )
        }

        // 1c. Projected Floating Combat Damage & Crit Popups
        if (hud.damagePopups.isNotEmpty()) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val wPx = constraints.maxWidth.toFloat()
                val hPx = constraints.maxHeight.toFloat()
                for (popup in hud.damagePopups) {
                    val px = (popup.normX * wPx - 28f).roundToInt()
                    val py = (popup.normY * hPx - 14f).roundToInt()
                    val col = when {
                        popup.isPlayerHit -> CrimsonDanger
                        popup.isCrit -> NeonAmber
                        else -> NeonCyan
                    }
                    Text(
                        text = popup.text,
                        fontSize = if (popup.isCrit) 16.sp else 13.sp,
                        fontWeight = if (popup.isCrit || popup.isPlayerHit) FontWeight.ExtraBold else FontWeight.Bold,
                        color = col.copy(alpha = popup.alpha),
                        modifier = Modifier.offset { IntOffset(px, py) }
                    )
                }
            }
        }

        // 2. Left-half Free Floating Virtual Joystick Zone (spawns wherever you touch!)
        if (hud.status == RunStatus.PLAYING) {
            FloatingVirtualJoystick(
                onMove = { x, y ->
                    sim.moveInputX = x
                    sim.moveInputY = y
                },
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.5f)
                    .align(Alignment.CenterStart)
                    .testTag("virtual_joystick")
            )
        }

        // 3. Right-half Touch Camera Orbit Zone
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .align(Alignment.CenterEnd)
                .pointerInput(sim) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        sim.rotateCamera(-dragAmount.x * 0.25f, dragAmount.y * 0.16f)
                    }
                }
        )

        // 4. Tactical Combat HUD Overlay
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top HUD Bar
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = VoidBlack.copy(alpha = 0.74f),
                        border = BorderStroke(1.dp, CyberBorder),
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "NV ${hud.level} • ${sim.classDef.name}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = NeonCyan
                                )
                                Text(
                                    text = "${hud.hp.toInt()}/${hud.maxHp.toInt()} HP",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (hud.hp < hud.maxHp * 0.3f) CrimsonDanger else AcidGreen
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            HudProgressBar(
                                fraction = hud.hp / hud.maxHp.coerceAtLeast(1f),
                                color = if (hud.hp < hud.maxHp * 0.3f) CrimsonDanger else AcidGreen,
                                heightDp = 6
                            )
                            Spacer(Modifier.height(3.dp))
                            HudProgressBar(
                                fraction = hud.shield / hud.maxShield.coerceAtLeast(1f),
                                color = NeonCyan,
                                heightDp = 4
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("XP ${hud.xp}/${hud.xpToNext}", style = MaterialTheme.typography.labelSmall, color = ElectricViolet)
                                Text("+${hud.creditsEarned} CR", style = MaterialTheme.typography.labelSmall, color = NeonAmber)
                            }
                            Spacer(Modifier.height(2.dp))
                            HudProgressBar(
                                fraction = hud.xp.toFloat() / hud.xpToNext.coerceAtLeast(1),
                                color = ElectricViolet,
                                heightDp = 4
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = VoidBlack.copy(alpha = 0.74f),
                            border = BorderStroke(1.dp, CyberBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = formatTimeMMSS(hud.elapsedSeconds.toInt()),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "FASE ${hud.stageNumber} • ${hud.stageName.uppercase()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonCyan
                                )
                                Text(
                                    text = "ONDA ${hud.waveNumber} • AMEAÇA ${hud.difficultyTier} • ${hud.kills} ABATES",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonAmber
                                )
                                Text(
                                    text = if (hud.activePetName != null) "${hud.weatherLabel.uppercase()} • ${hud.activePetName.uppercase()}" else hud.weatherLabel.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (hud.bossDuelActive) CrimsonDanger else TextSecondary
                                )
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = VoidBlack.copy(alpha = 0.78f),
                            border = BorderStroke(1.dp, CyberBorder)
                        ) {
                            IconButton(
                                onClick = onTogglePause,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("pause_button")
                            ) {
                                Icon(Icons.Filled.Pause, contentDescription = "Pausar", tint = TextPrimary)
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = hud.statusBannerText != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = VoidBlack.copy(alpha = 0.78f),
                            border = BorderStroke(1.dp, NeonAmber.copy(alpha = 0.85f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Filled.Warning, contentDescription = null, tint = NeonAmber, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = hud.statusBannerText.orEmpty(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Row: Compact Boss HP bar (when active) or subtle joystick hint on left, Action Cluster on right
            if (hud.status == RunStatus.PLAYING) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    if (hud.bossName != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = VoidBlack.copy(alpha = 0.78f),
                            border = BorderStroke(1.dp, CrimsonDanger.copy(alpha = 0.9f)),
                            modifier = Modifier
                                .widthIn(max = 215.dp)
                                .padding(end = 8.dp, bottom = 2.dp)
                                .testTag("boss_health_bar")
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${hud.bossName}${if (hud.bossPhase2) " F2" else ""}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = CrimsonDanger,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "${hud.bossHp.toInt()}/${hud.bossMaxHp.toInt()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NeonAmber
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                HudProgressBar(
                                    fraction = hud.bossHp / hud.bossMaxHp.coerceAtLeast(1f),
                                    color = if (hud.bossPhase2) NeonMagenta else CrimsonDanger,
                                    heightDp = 5
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "TOQUE À ESQUERDA PARA MOVER",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary.copy(alpha = 0.45f),
                            modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CombatActionButton(
                                label = "ESQUIVA",
                                cooldownLeft = hud.dodgeCooldown,
                                cooldownMax = hud.dodgeMaxCooldown,
                                accent = NeonCyan,
                                tag = "dodge_button",
                                onClick = { sim.triggerDodge() }
                            )
                            CombatActionButton(
                                label = "SKILL",
                                cooldownLeft = hud.activeCooldown,
                                cooldownMax = hud.activeMaxCooldown,
                                accent = NeonMagenta,
                                tag = "active_skill_button",
                                onClick = { sim.triggerActiveAbility() }
                            )
                        }
                        Surface(
                            onClick = { sim.triggerManualAttack() },
                            shape = RoundedCornerShape(12.dp),
                            color = CyberSurface.copy(alpha = 0.85f),
                            border = BorderStroke(1.2.dp, NeonAmber),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("manual_fire_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Bolt, contentDescription = "Atacar", tint = NeonAmber, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("ATACAR", style = MaterialTheme.typography.labelLarge, color = NeonAmber, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        if (hud.status == RunStatus.LEVEL_UP) {
            LevelUpCardsOverlay(
                level = hud.level,
                choices = hud.levelUpChoices,
                onSelectCard = onSelectCard
            )
        }

        if (hud.status == RunStatus.PAUSED) {
            PauseOverlay(
                save = save,
                onResume = onTogglePause,
                onUpdateQuality = onUpdateQuality,
                onUpdateCameraDist = onUpdateCameraDist,
                onToggleSound = onToggleSound,
                onExit = onExitToMenu
            )
        }

        if (hud.status == RunStatus.VICTORY || hud.status == RunStatus.DEFEAT) {
            RunEndOverlay(
                victory = hud.status == RunStatus.VICTORY,
                hud = hud,
                onPlayAgain = onPlayAgain,
                onExitToMenu = onExitToMenu
            )
        }
    }
}

/**
 * Dynamic Floating Virtual Joystick:
 * Appears wherever the user places their thumb on the left half of the screen,
 * tracks movement smoothly in 360 degrees, and disappears on release.
 */
@Composable
private fun FloatingVirtualJoystick(
    onMove: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var origin by remember { mutableStateOf<Offset?>(null) }
    var currentPos by remember { mutableStateOf<Offset?>(null) }
    val maxRadiusPx = 125f

    Box(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                origin = down.position
                currentPos = down.position
                onMove(0f, 0f)

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) {
                        origin = null
                        currentPos = null
                        onMove(0f, 0f)
                        break
                    }
                    change.consume()
                    val start = origin ?: down.position
                    val diff = change.position - start
                    val dist = sqrt(diff.x * diff.x + diff.y * diff.y)
                    val clamped = if (dist > maxRadiusPx) {
                        Offset(diff.x / dist * maxRadiusPx, diff.y / dist * maxRadiusPx)
                    } else {
                        diff
                    }
                    currentPos = start + clamped
                    onMove(clamped.x / maxRadiusPx, -clamped.y / maxRadiusPx)
                }
            }
        }
    ) {
        val base = origin
        val knob = currentPos
        if (base != null && knob != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = VoidBlack.copy(alpha = 0.45f),
                    radius = 74f,
                    center = base
                )
                drawCircle(
                    color = NeonCyan.copy(alpha = 0.55f),
                    radius = 74f,
                    center = base,
                    style = Stroke(width = 2.5f)
                )
                drawCircle(
                    color = NeonCyan.copy(alpha = 0.85f),
                    radius = 30f,
                    center = knob
                )
            }
        }
    }
}

@Composable
private fun HudProgressBar(fraction: Float, color: Color, heightDp: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(CyberBorder.copy(alpha = 0.6f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(heightDp.dp)
                .background(color)
        )
    }
}

@Composable
private fun CombatActionButton(
    label: String,
    cooldownLeft: Float,
    cooldownMax: Float,
    accent: Color,
    tag: String,
    onClick: () -> Unit
) {
    val ready = cooldownLeft <= 0.05f
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (ready) CyberSurface.copy(alpha = 0.88f) else CyberSurface.copy(alpha = 0.65f),
        border = BorderStroke(1.5.dp, if (ready) accent else CyberBorder),
        modifier = Modifier
            .size(64.dp)
            .testTag(tag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (!ready && cooldownMax > 0f) {
                val frac = (cooldownLeft / cooldownMax).coerceIn(0f, 1f)
                Canvas(modifier = Modifier.fillMaxSize().padding(3.dp)) {
                    drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = 360f * (1f - frac),
                        useCenter = false,
                        style = Stroke(width = 4.5f)
                    )
                }
            }
            Text(
                text = if (ready) label else "%.1fs".format(cooldownLeft),
                style = MaterialTheme.typography.labelSmall,
                color = if (ready) accent else TextSecondary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun LevelUpCardsOverlay(
    level: Int,
    choices: List<EvolutionCardDef>,
    onSelectCard: (EvolutionCardDef) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack.copy(alpha = 0.88f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        val isCompactHand = maxWidth < 540.dp
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "NÍVEL $level ALCANÇADO",
                style = MaterialTheme.typography.headlineLarge,
                color = NeonCyan,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Escolha 1 Carta do seu Deck de Build",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (isCompactHand) 8.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                choices.forEachIndexed { idx, card ->
                    SkillCardView(
                        card = card,
                        compact = isCompactHand,
                        footerActionLabel = "ESCOLHER",
                        testTagName = "evolution_card_$idx",
                        onClick = { onSelectCard(card) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PauseOverlay(
    save: PlayerSaveData,
    onResume: () -> Unit,
    onUpdateQuality: (GraphicsQuality) -> Unit,
    onUpdateCameraDist: (Float) -> Unit,
    onToggleSound: (Boolean) -> Unit,
    onExit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack.copy(alpha = 0.86f))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CyberSurface,
            border = BorderStroke(1.dp, CyberBorder),
            modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SISTEMA PAUSADO", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                Spacer(Modifier.height(14.dp))

                Text("DISTÂNCIA ISOMÉTRICA: ${"%.1f".format(save.cameraDistance)}m", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Slider(
                    value = save.cameraDistance.coerceIn(11.0f, 32.0f),
                    onValueChange = onUpdateCameraDist,
                    valueRange = 11.0f..32.0f,
                    colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
                )

                Text("GRÁFICOS", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GraphicsQuality.entries.forEach { q ->
                        FilterChip(
                            selected = save.graphicsQuality == q,
                            onClick = { onUpdateQuality(q) },
                            label = { Text(q.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("EFEITOS SONOROS (SFX)", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                    Switch(
                        checked = save.soundEnabled,
                        onCheckedChange = onToggleSound,
                        colors = SwitchDefaults.colors(checkedThumbColor = VoidBlack, checkedTrackColor = NeonCyan)
                    )
                }

                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onResume,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = VoidBlack)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("CONTINUAR", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onExit,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    border = BorderStroke(1.dp, CrimsonDanger)
                ) {
                    Text("ENCERRAR INCURSÃO", color = CrimsonDanger)
                }
            }
        }
    }
}

@Composable
private fun RunEndOverlay(
    victory: Boolean,
    hud: HudSnapshot,
    onPlayAgain: () -> Unit,
    onExitToMenu: () -> Unit
) {
    val accent = if (victory) AcidGreen else CrimsonDanger
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(VoidBlack.copy(alpha = 0.88f), accent.copy(alpha = 0.10f), VoidBlack.copy(alpha = 0.94f))
                )
            )
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CyberSurface,
            border = BorderStroke(1.5.dp, accent),
            modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (victory) "VITÓRIA: TITAN ZERO DESTRUÍDO!" else "SISTEMA OFFLINE",
                    style = MaterialTheme.typography.headlineMedium,
                    color = accent,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (victory) "As máquinas recuaram do setor Neo-Vértice." else "Seu exoesqueleto sofreu dano crítico na arena.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(18.dp))

                SummaryRow("TEMPO SOBREVIVIDO", formatTimeMMSS(hud.elapsedSeconds.toInt()))
                SummaryRow("NÍVEL ALCANÇADO", "${hud.level}")
                SummaryRow("MÁQUINAS DESTRUÍDAS", "${hud.kills}")
                SummaryRow("CHEFES DERROTADOS", "${hud.bossesDefeatedInRun}")
                SummaryRow("CRÉDITOS COLETADOS", "+${hud.creditsEarned + (hud.elapsedSeconds.toInt() / 12) + hud.bossesDefeatedInRun * 60} CR")

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onPlayAgain,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = VoidBlack)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("JOGAR NOVAMENTE", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onExitToMenu,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    border = BorderStroke(1.dp, CyberBorder)
                ) {
                    Text("MENU PRINCIPAL", color = TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.labelLarge, color = NeonAmber, fontWeight = FontWeight.Bold)
    }
}
