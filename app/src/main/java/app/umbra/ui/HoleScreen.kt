package app.umbra.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import app.umbra.ui.theme.*
import kotlin.math.min

@Composable
fun HoleScreen(
    state: UmbraState,
    onHolePress: () -> Unit,
    onLibraryOpen: () -> Unit,
    onAdsPress: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ring")
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.85f, label = "ringAlpha",
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.04f, label = "pulseScale",
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .systemBarsPadding(),
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InfoButton()
            if (state.premium?.isActive != true) {
                AdsButton(onClick = onAdsPress)
            }
        }

        // App title
        Text(
            text = "U  M  B  R  A",
            color = White.copy(alpha = 0.6f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 8.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 68.dp),
        )

        // Center hole
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HoleButton(
                busy = state.busy,
                phase = state.phase,
                ringAlpha = if (state.busy) 1f else ringAlpha,
                pulseScale = if (state.busy) 1f else pulseScale,
                errorMessage = state.errorMessage,
                onClick = onHolePress,
            )
        }

        // Bottom — VIDEOS label
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onLibraryOpen,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DotMatrix()
            Text(
                text = "V  I  D  E  O  S",
                color = Gold,
                fontSize = 10.sp,
                letterSpacing = 4.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun HoleButton(
    busy: Boolean,
    phase: DownloadPhase?,
    ringAlpha: Float,
    pulseScale: Float,
    errorMessage: String?,
    onClick: () -> Unit,
) {
    val spinAngle by if (busy) {
        rememberInfiniteTransition(label = "spin").animateFloat(
            0f, 360f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "spin"
        )
    } else remember { mutableStateOf(0f) }

    val holeSize = 220.dp

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(holeSize)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !busy,
                onClick = onClick,
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val cy = size.height / 2
            val radius = size.minDimension / 2

            // Radial gradient fill — the "black hole"
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF2A2A2A), Color(0xFF0A0A0A), Color(0xFF000000)),
                    center = Offset(cx * 0.85f, cy * 0.75f),
                    radius = radius * 1.2f,
                ),
                radius = radius * (if (busy) pulseScale else 1f),
                center = Offset(cx, cy),
            )

            // Outer ring
            if (busy) {
                drawArc(
                    color = White.copy(alpha = 0.7f),
                    startAngle = spinAngle,
                    sweepAngle = 260f,
                    useCenter = false,
                    topLeft = Offset(cx - radius + 2, cy - radius + 2),
                    size = Size((radius - 2) * 2, (radius - 2) * 2),
                    style = Stroke(width = 1.5f, cap = StrokeCap.Round),
                )
            } else {
                drawCircle(
                    color = White.copy(alpha = ringAlpha * 0.5f),
                    radius = radius - 1,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1f),
                )
            }
        }

        // Status text inside hole
        val statusText = when {
            errorMessage != null -> errorMessage
            phase == DownloadPhase.RESOLVE -> "Bağlantı çözümleniyor…"
            phase == DownloadPhase.DOWNLOAD -> "İndiriliyor…"
            phase == DownloadPhase.DONE -> "İndirildi ✓"
            else -> null
        }
        val statusColor = when {
            errorMessage != null -> RedErr
            phase == DownloadPhase.DONE -> GreenOk
            else -> WhiteMuted
        }
        if (statusText != null) {
            Text(
                text = statusText,
                color = statusColor,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun DotMatrix() {
    Canvas(modifier = Modifier.size(44.dp, 36.dp)) {
        val dotR = 2.5f
        val cols = 7
        val rows = 5
        val spacingX = size.width / (cols - 1)
        val spacingY = size.height / (rows - 1)
        val cx = size.width / 2
        val cy = size.height / 2
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val x = col * spacingX
                val y = row * spacingY
                val dist = kotlin.math.sqrt(((x - cx) * (x - cx) + (y - cy) * (y - cy)).toDouble()).toFloat()
                val maxDist = kotlin.math.sqrt((cx * cx + cy * cy).toDouble()).toFloat()
                val alpha = (1f - dist / maxDist).coerceIn(0.2f, 1f)
                drawCircle(
                    color = Gold.copy(alpha = alpha),
                    radius = dotR,
                    center = Offset(x, y),
                )
            }
        }
    }
}

@Composable
private fun InfoButton() {
    Canvas(modifier = Modifier.size(32.dp)) {
        drawCircle(color = White.copy(0.15f), radius = size.minDimension / 2, style = Stroke(1.2f))
        drawCircle(color = White.copy(0.5f), radius = 2.2f, center = Offset(size.width / 2, size.height * 0.3f))
        drawLine(
            color = White.copy(0.5f),
            start = Offset(size.width / 2, size.height * 0.47f),
            end = Offset(size.width / 2, size.height * 0.72f),
            strokeWidth = 2f, cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun AdsButton(onClick: () -> Unit) {
    Canvas(
        modifier = Modifier
            .size(36.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        drawCircle(color = White.copy(0.15f), radius = size.minDimension / 2, style = Stroke(1.2f))
        val cx = size.width / 2
        val cy = size.height / 2
        val r = size.minDimension * 0.32f
        drawLine(
            color = White.copy(0.55f),
            start = Offset(cx - r, cy - r), end = Offset(cx + r, cy + r),
            strokeWidth = 1.8f, cap = StrokeCap.Round,
        )
        drawLine(
            color = White.copy(0.55f),
            start = Offset(cx + r, cy - r), end = Offset(cx - r, cy + r),
            strokeWidth = 1.8f, cap = StrokeCap.Round,
        )
    }
}
