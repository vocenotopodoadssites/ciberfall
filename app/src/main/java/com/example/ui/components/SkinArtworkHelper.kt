package com.example.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.SkinDef
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack

@DrawableRes
fun resolveSkinPreviewDrawable(drawableKey: String?, fallbackClassId: String? = null): Int? {
    return when (drawableKey) {
        "skin_kitbash_berserker" -> R.drawable.skin_kitbash_berserker
        "skin_kitbash_engenheiro" -> R.drawable.skin_kitbash_engenheiro
        "skin_kitbash_atirador" -> R.drawable.skin_kitbash_atirador
        "skin_kitbash_tecnomago" -> R.drawable.skin_kitbash_tecnomago
        "skin_kitbash_hacker" -> R.drawable.skin_kitbash_hacker
        "skin_kitbash_guardiao" -> R.drawable.skin_kitbash_guardiao
        "skin_kitbash_cacador" -> R.drawable.skin_kitbash_cacador
        "skin_sheet_berserker_in12" -> R.drawable.skin_sheet_berserker_in12
        "skin_sheet_engenheiro_industrial" -> R.drawable.skin_sheet_engenheiro_industrial
        "skin_sheet_atirador_espectro" -> R.drawable.skin_sheet_atirador_espectro
        "skin_sheet_tecnomago_arcano" -> R.drawable.skin_sheet_tecnomago_arcano
        "skin_sheet_hacker_virus" -> R.drawable.skin_sheet_hacker_virus
        "skin_sheet_guardiao_bastiao" -> R.drawable.skin_sheet_guardiao_bastiao
        else -> when (fallbackClassId) {
            "berserker" -> R.drawable.skin_kitbash_berserker
            "engenheiro" -> R.drawable.skin_kitbash_engenheiro
            "atirador" -> R.drawable.skin_kitbash_atirador
            "tecnomago" -> R.drawable.skin_kitbash_tecnomago
            "hacker" -> R.drawable.skin_kitbash_hacker
            "guardiao" -> R.drawable.skin_kitbash_guardiao
            "cacador" -> R.drawable.skin_kitbash_cacador
            else -> null
        }
    }
}

@Composable
fun SkinBlueprintDialog(
    className: String,
    skin: SkinDef,
    @DrawableRes drawableRes: Int,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CyberSurface,
            border = BorderStroke(1.5.dp, skin.tier.color),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$className • ${skin.name}",
                            style = MaterialTheme.typography.titleMedium,
                            color = skin.tier.color,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Folha de Referência & Peças Separadas",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text("FECHAR", color = NeonCyan, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Image(
                    painter = painterResource(id = drawableRes),
                    contentDescription = "Blueprint da skin ${skin.name}",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = VoidBlack.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = skin.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}
