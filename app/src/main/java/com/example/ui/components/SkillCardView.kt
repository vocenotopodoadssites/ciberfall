package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CardCategory
import com.example.data.EvolutionCardDef
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SkillCardView(
    card: EvolutionCardDef,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEquipped: Boolean = false,
    footerActionLabel: String? = null,
    compact: Boolean = false,
    testTagName: String? = null
) {
    val rarityColor = card.rarity.color
    val categoryColor = when (card.category) {
        CardCategory.OFENSIVA -> Color(0xFFEF5350)
        CardCategory.ELEMENTAL -> Color(0xFFAB47BC)
        CardCategory.TECNOLOGIA -> NeonCyan
        CardCategory.DEFESA -> AcidGreen
        CardCategory.MOBILIDADE -> NeonAmber
    }
    val cardShape = CutCornerShape(
        topStart = if (compact) 10.dp else 14.dp,
        topEnd = 4.dp,
        bottomEnd = if (compact) 10.dp else 14.dp,
        bottomStart = 4.dp
    )
    val frameBorder = if (isEquipped) AcidGreen else rarityColor

    Surface(
        onClick = onClick,
        shape = cardShape,
        color = CyberSurface,
        border = BorderStroke(if (isEquipped) 2.dp else 1.5.dp, frameBorder.copy(alpha = 0.88f)),
        shadowElevation = if (isEquipped) 8.dp else 4.dp,
        modifier = modifier
            .aspectRatio(if (compact) 0.58f else 0.67f)
            .then(if (testTagName != null) Modifier.testTag(testTagName) else Modifier)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            rarityColor.copy(alpha = 0.14f),
                            CyberSurface,
                            VoidBlack.copy(alpha = 0.96f)
                        )
                    )
                )
                .padding(if (compact) 6.dp else 9.dp)
        ) {
            // Subtle inner metallic frame line
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 0.8.dp,
                        color = rarityColor.copy(alpha = 0.28f),
                        shape = CutCornerShape(topStart = 8.dp, topEnd = 2.dp, bottomEnd = 8.dp, bottomStart = 2.dp)
                    )
                    .padding(if (compact) 5.dp else 7.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 1. Top Card Header: Rarity gem + Category pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(if (compact) 7.dp else 9.dp)
                                    .clip(CircleShape)
                                    .background(rarityColor)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = card.rarity.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = if (compact) 8.sp else 9.5.sp,
                                    letterSpacing = 0.6.sp
                                ),
                                color = rarityColor,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = categoryColor.copy(alpha = 0.16f),
                            border = BorderStroke(0.8.dp, categoryColor.copy(alpha = 0.55f))
                        ) {
                            Text(
                                text = if (compact) card.category.label.take(4).uppercase() else card.category.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = if (compact) 7.5.sp else 8.5.sp
                                ),
                                color = categoryColor,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = if (compact) 4.dp else 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(if (compact) 4.dp else 6.dp))

                    // 2. Center Artwork Window (Procedural Emblem Illustration)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(if (compact) 0.85f else 0.95f)
                            .clip(CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp))
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        categoryColor.copy(alpha = 0.24f),
                                        rarityColor.copy(alpha = 0.10f),
                                        VoidBlack.copy(alpha = 0.92f)
                                    )
                                )
                            )
                            .border(
                                width = 1.dp,
                                color = categoryColor.copy(alpha = 0.35f),
                                shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize().padding(if (compact) 6.dp else 10.dp)) {
                            drawCardArtwork(card, categoryColor, rarityColor)
                        }

                        // Card Serial Code Tag on top-left corner of artwork
                        Text(
                            text = card.id.removePrefix("card_").let { "#$it" },
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = if (compact) 7.sp else 8.sp),
                            color = TextSecondary.copy(alpha = 0.65f),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(horizontal = 5.dp, vertical = 3.dp)
                        )

                        if (isEquipped) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Equipada no Deck",
                                tint = AcidGreen,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(if (compact) 16.dp else 20.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(if (compact) 5.dp else 7.dp))

                    // 3. Title Banner Ribbon
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = CyberSurfaceElevated,
                        border = BorderStroke(0.8.dp, rarityColor.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = card.name.uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = if (compact) 9.5.sp else 11.5.sp,
                                lineHeight = if (compact) 11.sp else 13.5.sp
                            ),
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = if (compact) 3.dp else 5.dp)
                        )
                    }

                    Spacer(Modifier.height(if (compact) 4.dp else 6.dp))

                    // 4. Inset Description Plaque
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(if (compact) 0.9f else 0.85f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(VoidBlack.copy(alpha = 0.72f))
                            .border(0.7.dp, CyberBorder.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                            .padding(if (compact) 5.dp else 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = card.description,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = if (compact) 8.8.sp else 10.5.sp,
                                lineHeight = if (compact) 11.sp else 13.5.sp
                            ),
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            maxLines = if (compact) 4 else 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.height(if (compact) 4.dp else 6.dp))

                    // 5. Card Footer Seal: Stack Cap + Action Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            repeat(card.maxStacks.coerceAtMost(5)) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 2.dp)
                                        .size(if (compact) 4.dp else 5.dp)
                                        .clip(CircleShape)
                                        .background(rarityColor.copy(alpha = 0.8f))
                                )
                            }
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = "x${card.maxStacks}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = if (compact) 7.5.sp else 9.sp),
                                color = NeonAmber
                            )
                        }

                        val badgeText = when {
                            isEquipped -> "NO DECK"
                            footerActionLabel != null -> footerActionLabel
                            else -> "ESCOLHER"
                        }
                        val badgeColor = when {
                            isEquipped -> AcidGreen
                            footerActionLabel == "CHEIO" -> TextSecondary
                            else -> rarityColor
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = badgeColor.copy(alpha = 0.14f),
                            border = BorderStroke(0.7.dp, badgeColor.copy(alpha = 0.55f))
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = if (compact) 7.sp else 8.5.sp),
                                color = badgeColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = if (compact) 4.dp else 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawCardArtwork(
    card: EvolutionCardDef,
    accent: Color,
    rarityColor: Color
) {
    val cx = size.width * 0.5f
    val cy = size.height * 0.5f
    val r = size.minDimension * 0.42f

    // Subtle tactical grid backdrop inside artwork frame
    val gridColor = accent.copy(alpha = 0.14f)
    for (i in 1..3) {
        val y = size.height * (i / 4f)
        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        val x = size.width * (i / 4f)
        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
    }

    // Outer orbital ring
    drawCircle(
        color = rarityColor.copy(alpha = 0.30f),
        radius = r * 1.05f,
        center = Offset(cx, cy),
        style = Stroke(width = 1.6f)
    )
    drawCircle(
        color = accent.copy(alpha = 0.22f),
        radius = r * 0.78f,
        center = Offset(cx, cy),
        style = Stroke(width = 1.2f)
    )

    val stroke = Stroke(width = (size.minDimension * 0.055f).coerceIn(2f, 4.5f), cap = StrokeCap.Round, join = StrokeJoin.Round)
    when {
        card.effectType.contains("DRONE") -> {
            // Drone core + 4 rotor arms
            drawCircle(accent, radius = r * 0.26f, center = Offset(cx, cy))
            for (deg in listOf(45.0, 135.0, 225.0, 315.0)) {
                val rad = deg * PI / 180.0
                val px = cx + (cos(rad) * r * 0.82f).toFloat()
                val py = cy + (sin(rad) * r * 0.82f).toFloat()
                drawLine(rarityColor, Offset(cx, cy), Offset(px, py), strokeWidth = stroke.width * 0.8f)
                drawCircle(accent.copy(alpha = 0.7f), radius = r * 0.22f, center = Offset(px, py), style = Stroke(width = 2f))
            }
        }
        card.effectType.contains("TURRET") || card.effectType.contains("MINE") -> {
            // Hex turret / proximity mine node
            val path = Path()
            for (i in 0 until 6) {
                val a = (i * 60.0 - 30.0) * PI / 180.0
                val px = cx + (cos(a) * r * 0.75f).toFloat()
                val py = cy + (sin(a) * r * 0.75f).toFloat()
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            drawPath(path, accent.copy(alpha = 0.22f))
            drawPath(path, accent, style = stroke)
            drawCircle(rarityColor, radius = r * 0.25f, center = Offset(cx, cy))
            drawLine(rarityColor, Offset(cx, cy), Offset(cx + r * 0.85f, cy - r * 0.45f), strokeWidth = stroke.width * 1.1f)
        }
        card.effectType.contains("ELECTRIC") || card.effectType.contains("CHAIN") || card.effectType.contains("EMP") -> {
            // High-voltage lightning bolt
            val bolt = Path().apply {
                moveTo(cx + r * 0.18f, cy - r * 0.90f)
                lineTo(cx - r * 0.42f, cy + r * 0.05f)
                lineTo(cx + r * 0.05f, cy + r * 0.05f)
                lineTo(cx - r * 0.22f, cy + r * 0.90f)
                lineTo(cx + r * 0.48f, cy - r * 0.12f)
                lineTo(cx - r * 0.04f, cy - r * 0.12f)
                close()
            }
            drawPath(bolt, accent.copy(alpha = 0.28f))
            drawPath(bolt, rarityColor, style = stroke)
        }
        card.effectType.contains("CRYO") -> {
            // 6-point cryo crystal
            for (i in 0 until 3) {
                val a = (i * 60.0) * PI / 180.0
                val dx = (cos(a) * r * 0.85f).toFloat()
                val dy = (sin(a) * r * 0.85f).toFloat()
                drawLine(accent, Offset(cx - dx, cy - dy), Offset(cx + dx, cy + dy), strokeWidth = stroke.width)
            }
            drawCircle(rarityColor, radius = r * 0.22f, center = Offset(cx, cy))
        }
        card.effectType.contains("FIRE") || card.effectType.contains("INFERNO") || card.effectType.contains("PLASMA") || card.effectType.contains("EXPLOSION") -> {
            // Stylized thermal plasma flame
            val flame = Path().apply {
                moveTo(cx, cy - r * 0.88f)
                quadraticTo(cx + r * 0.72f, cy - r * 0.05f, cx + r * 0.44f, cy + r * 0.62f)
                quadraticTo(cx, cy + r * 0.96f, cx - r * 0.44f, cy + r * 0.62f)
                quadraticTo(cx - r * 0.72f, cy - r * 0.05f, cx, cy - r * 0.88f)
                close()
            }
            drawPath(flame, accent.copy(alpha = 0.30f))
            drawPath(flame, rarityColor, style = stroke)
            drawCircle(NeonAmber, radius = r * 0.24f, center = Offset(cx, cy + r * 0.25f))
        }
        card.category == CardCategory.DEFESA -> {
            // Tactical armored shield crest
            val shield = Path().apply {
                moveTo(cx, cy - r * 0.85f)
                lineTo(cx + r * 0.72f, cy - r * 0.50f)
                lineTo(cx + r * 0.58f, cy + r * 0.36f)
                lineTo(cx, cy + r * 0.88f)
                lineTo(cx - r * 0.58f, cy + r * 0.36f)
                lineTo(cx - r * 0.72f, cy - r * 0.50f)
                close()
            }
            drawPath(shield, accent.copy(alpha = 0.24f))
            drawPath(shield, accent, style = stroke)
            drawLine(rarityColor, Offset(cx, cy - r * 0.40f), Offset(cx, cy + r * 0.42f), strokeWidth = stroke.width)
            drawLine(rarityColor, Offset(cx - r * 0.32f, cy), Offset(cx + r * 0.32f, cy), strokeWidth = stroke.width)
        }
        card.category == CardCategory.MOBILIDADE -> {
            // Twin kinetic chevron speed wings
            for (shift in listOf(-r * 0.30f, r * 0.22f)) {
                val chev = Path().apply {
                    moveTo(cx - r * 0.55f + shift, cy - r * 0.58f)
                    lineTo(cx + r * 0.22f + shift, cy)
                    lineTo(cx - r * 0.55f + shift, cy + r * 0.58f)
                }
                drawPath(chev, accent, style = stroke)
            }
            drawCircle(rarityColor, radius = r * 0.16f, center = Offset(cx + r * 0.48f, cy))
        }
        card.category == CardCategory.TECNOLOGIA -> {
            // Microchip circuit module
            val boxSize = r * 1.15f
            drawRoundRect(
                color = accent.copy(alpha = 0.24f),
                topLeft = Offset(cx - boxSize * 0.5f, cy - boxSize * 0.5f),
                size = Size(boxSize, boxSize),
                cornerRadius = CornerRadius(6f, 6f)
            )
            drawRoundRect(
                color = accent,
                topLeft = Offset(cx - boxSize * 0.5f, cy - boxSize * 0.5f),
                size = Size(boxSize, boxSize),
                cornerRadius = CornerRadius(6f, 6f),
                style = stroke
            )
            drawCircle(rarityColor, radius = r * 0.22f, center = Offset(cx, cy))
        }
        else -> {
            // OFENSIVA: Precision crosshair + twin crossed energy blades
            drawLine(accent, Offset(cx - r * 0.72f, cy + r * 0.72f), Offset(cx + r * 0.72f, cy - r * 0.72f), strokeWidth = stroke.width)
            drawLine(rarityColor, Offset(cx - r * 0.72f, cy - r * 0.72f), Offset(cx + r * 0.72f, cy + r * 0.72f), strokeWidth = stroke.width)
            drawCircle(accent, radius = r * 0.45f, center = Offset(cx, cy), style = Stroke(width = 2f))
            drawCircle(NeonAmber, radius = r * 0.14f, center = Offset(cx, cy))
        }
    }
}
