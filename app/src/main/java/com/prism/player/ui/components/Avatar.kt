package com.prism.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Gradient palette for profile avatars; a profile stores an index into this list. */
object Avatars {
    val palette: List<Pair<Color, Color>> = listOf(
        Color(0xFF7DD3FC) to Color(0xFF6366F1),
        Color(0xFFF472B6) to Color(0xFFEF4444),
        Color(0xFFFBBF24) to Color(0xFFF97316),
        Color(0xFF34D399) to Color(0xFF0EA5E9),
        Color(0xFFA78BFA) to Color(0xFFEC4899),
        Color(0xFF22D3EE) to Color(0xFF10B981),
        Color(0xFFFB7185) to Color(0xFFA855F7),
        Color(0xFFE2E8F0) to Color(0xFF64748B),
    )

    fun brush(index: Int): Brush {
        val (a, b) = palette[((index % palette.size) + palette.size) % palette.size]
        return Brush.linearGradient(listOf(a, b))
    }
}

/** Round gradient avatar showing the profile's first letter. */
@Composable
fun Avatar(name: String, avatar: Int, size: Dp, modifier: Modifier = Modifier, focused: Boolean = false) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Avatars.brush(avatar))
            .border(if (focused) 4.dp else 2.dp, if (focused) Color.White else Color.White.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            style = TextStyle(fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold),
            color = Color.White,
        )
    }
}
