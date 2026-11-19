package com.example.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The living backdrop: a midnight canvas with slow aurora lights drifting behind
 * every frosted panel of the app.
 */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(21000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift"
    )
    val drift2 by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(27000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift2"
    )

    val canvas = MaterialTheme.colorScheme.background
    Box(modifier = modifier.fillMaxSize().background(canvas)) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(AuroraNebula, canvas, AuroraHorizon, canvas)
                )
            )

            val w = size.width
            val h = size.height
            val big = size.minDimension * 0.95f

            // Violet light, upper right.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(AuroraViolet.copy(alpha = 0.42f), Color.Transparent),
                    center = Offset(w * (0.82f - 0.10f * drift), h * (0.10f + 0.06f * drift)),
                    radius = big
                ),
                radius = big,
                center = Offset(w * (0.82f - 0.10f * drift), h * (0.10f + 0.06f * drift))
            )

            // Cyan light, upper left.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(AuroraCyan.copy(alpha = 0.30f), Color.Transparent),
                    center = Offset(w * (0.12f + 0.08f * drift2), h * (0.26f + 0.05f * drift2)),
                    radius = big * 0.9f
                ),
                radius = big * 0.9f,
                center = Offset(w * (0.12f + 0.08f * drift2), h * (0.26f + 0.05f * drift2))
            )

            // Emerald light, low left.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(AuroraTeal.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(w * (0.20f + 0.10f * drift), h * (0.88f - 0.06f * drift)),
                    radius = big * 0.85f
                ),
                radius = big * 0.85f,
                center = Offset(w * (0.20f + 0.10f * drift), h * (0.88f - 0.06f * drift))
            )

            // Rose light, low right.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(AuroraRose.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(w * (0.92f - 0.08f * drift2), h * (0.78f + 0.07f * drift2)),
                    radius = big * 0.75f
                ),
                radius = big * 0.75f,
                center = Offset(w * (0.92f - 0.08f * drift2), h * (0.78f + 0.07f * drift2))
            )

            // Fine top sheen so the glass bars catch light.
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0x14FFFFFF), Color.Transparent),
                    startY = 0f,
                    endY = h * 0.35f
                )
            )
        }
        content()
    }
}

/** A frosted panel with a light-catching top edge. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 26.dp,
    tint: Color = Color.White,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(corner)
    Column(
        modifier = modifier
            .shadow(18.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        tint.copy(alpha = 0.13f),
                        tint.copy(alpha = 0.05f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0x47FFFFFF), Color(0x0DFFFFFF))
                ),
                shape = shape
            )
            .padding(contentPadding),
        content = content
    )
}

/** Small translucent chip used for statuses and tags. */
@Composable
fun GlassChip(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.32f), CircleShape)
            .padding(horizontal = 11.dp, vertical = 5.dp)
    ) {
        Text(text, color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Section heading with a gradient accent bar. */
@Composable
fun GlassSectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 18.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(AuroraCyan, AuroraViolet)))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

/** The floating glass app bar: gradient monogram + title + soft version chip. */
@Composable
fun KrotyTopBar(version: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .shadow(20.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black, spotColor = Color.Black)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.horizontalGradient(listOf(Color(0x1FFFFFFF), Color(0x0DFFFFFF))))
            .border(
                1.dp,
                Brush.horizontalGradient(listOf(Color(0x40FFFFFF), Color(0x0AFFFFFF))),
                RoundedCornerShape(22.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .shadow(12.dp, RoundedCornerShape(13.dp), spotColor = AuroraViolet)
                .clip(RoundedCornerShape(13.dp))
                .background(Brush.linearGradient(listOf(AuroraViolet, AuroraCyan))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "K",
                fontWeight = FontWeight.Black,
                fontSize = 19.sp,
                color = Color(0xFF06101F)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "كروتي نت",
                style = TextStyle(
                    brush = Brush.horizontalGradient(listOf(Color.White, AuroraCyan)),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp
                )
            )
            Text(
                text = "Kroty Pro • مدير كروت الهوت سبوت",
                fontSize = 10.sp,
                color = InkMuted
            )
        }
        GlassChip(text = "v$version", accent = AuroraCyan)
    }
}

data class KrotyNavItem(val label: String, val icon: ImageVector)

/** Floating glass tab bar with a gradient pill on the active tab. */
@Composable
fun KrotyGlassNavBar(
    items: List<KrotyNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .shadow(24.dp, RoundedCornerShape(26.dp), ambientColor = Color.Black, spotColor = Color.Black)
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.verticalGradient(listOf(Color(0x2BFFFFFF), Color(0x14FFFFFF))))
            .border(
                1.dp,
                Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x0AFFFFFF))),
                RoundedCornerShape(26.dp)
            )
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            val shape = RoundedCornerShape(20.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .then(
                        if (selected) {
                            Modifier
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            AuroraCyan.copy(alpha = 0.26f),
                                            AuroraViolet.copy(alpha = 0.20f)
                                        )
                                    )
                                )
                                .border(1.dp, Color(0x33FFFFFF), shape)
                        } else Modifier
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(index) }
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = if (selected) Color.White else InkMuted,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = item.label,
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) Color.White else InkMuted
                )
            }
        }
    }
}
