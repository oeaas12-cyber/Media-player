package com.prism.player.ui.components

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.prism.player.ui.theme.Palette

/** Frosted glass surface: translucent gradient fill with a light rim. */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(22.dp),
    strength: Float = 1f,
    highlighted: Boolean = false,
): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = (if (highlighted) 0.30f else 0.13f) * strength),
                Color.White.copy(alpha = (if (highlighted) 0.18f else 0.05f) * strength),
            )
        )
    )
    .border(
        width = if (highlighted) 2.dp else 1.dp,
        brush = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (highlighted) 0.95f else 0.32f),
                Color.White.copy(alpha = if (highlighted) 0.55f else 0.06f),
            )
        ),
        shape = shape,
    )

/** Full-screen blurred artwork with colour glow behind every screen. */
@Composable
fun GlassBackdrop(imageUrl: String?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Palette.Background)) {
        // Ambient glow so the glass reads even without artwork.
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    listOf(Palette.AccentAlt.copy(alpha = 0.28f), Color.Transparent),
                    center = Offset(0f, 0f), radius = 1400f,
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    listOf(Palette.Accent.copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(2200f, 1400f), radius = 1600f,
                )
            )
        )
        val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        Crossfade(targetState = imageUrl, animationSpec = tween(700), label = "backdrop") { url ->
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (canBlur) Modifier.blur(40.dp) else Modifier)
                        .alpha(if (canBlur) 0.75f else 0.35f),
                )
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.45f), Palette.Background.copy(alpha = 0.92f))
                )
            )
        )
    }
}

/** Focus/press feedback shared by every interactive glass element (D-pad and touch). */
@Composable
fun Modifier.glassClickable(
    interaction: MutableInteractionSource,
    focusScale: Float = 1.06f,
    onClick: () -> Unit,
): Modifier {
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        when {
            pressed -> 0.96f
            focused -> focusScale
            else -> 1f
        }, label = "scale"
    )
    return this
        .scale(scale)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

@Composable
fun GlassButton(
    text: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(50)
    val bg = if (primary || focused) Modifier.clip(shape).background(Color.White.copy(alpha = if (focused) 1f else 0.9f))
    else Modifier.glass(shape)
    val content = if (primary || focused) Color(0xFF0B1020) else Palette.TextPrimary
    Row(
        modifier
            .glassClickable(interaction, onClick = onClick)
            .then(bg)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Text(text, color = content, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier
            .glassClickable(interaction, focusScale = 1.12f, onClick = onClick)
            .size(size)
            .then(
                if (focused) Modifier.clip(CircleShape).background(Color.White)
                else Modifier.glass(CircleShape)
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, contentDescription,
            tint = if (focused) Color(0xFF0B1020) else Palette.TextPrimary,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

@Composable
fun GlassChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .glassClickable(interaction, onClick = onClick)
            .then(
                when {
                    focused -> Modifier.clip(shape).background(Color.White)
                    selected -> Modifier.glass(shape, strength = 2.2f)
                    else -> Modifier.glass(shape, strength = 0.7f)
                }
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                focused -> Color(0xFF0B1020)
                selected -> Palette.TextPrimary
                else -> Palette.TextSecondary
            },
        )
    }
}
