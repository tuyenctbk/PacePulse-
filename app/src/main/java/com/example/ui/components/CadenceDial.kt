package com.example.ui.components

import android.view.MotionEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CadenceDial(
    targetSpm: Int,
    isPacing: Boolean,
    onSpmChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    dialSize: Dp = 280.dp
) {
    // Pulse animation scaling when pacing is active
    val pulseScale = remember { Animatable(1f) }
    val breathingScale = remember { Animatable(0.4f) }
    val glowOpacity = remember { Animatable(0.12f) }

    LaunchedEffect(isPacing, targetSpm) {
        if (isPacing) {
            val intervalMs = (60.0 / targetSpm * 1000.0).toLong().coerceAtLeast(100L)
            pulseScale.animateTo(
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = (intervalMs / 2).toInt(), easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
        } else {
            pulseScale.snapTo(1f)
        }
    }

    LaunchedEffect(isPacing, targetSpm) {
        if (isPacing) {
            val intervalMs = (60.0 / targetSpm * 1000.0).toLong().coerceAtLeast(100L)
            // 4 steps in, 4 steps out
            breathingScale.animateTo(
                targetValue = 0.85f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = (intervalMs * 4).toInt(), easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
        } else {
            breathingScale.snapTo(0.4f)
        }
    }

    LaunchedEffect(isPacing) {
        if (isPacing) {
            glowOpacity.animateTo(
                targetValue = 0.35f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
        } else {
            glowOpacity.snapTo(0.12f)
        }
    }

    // Convert SPM (120..240) to dial angle in degrees (-135° to +135°, total 270°)
    val minSpm = 120f
    val maxSpm = 240f
    val sweepAngleTotal = 270f
    val startAngle = 135f // 135° is bottom left

    val currentFraction = ((targetSpm - minSpm) / (maxSpm - minSpm)).coerceIn(0f, 1f)
    val activeSweepAngle = currentFraction * sweepAngleTotal

    val paceCategory = when {
        targetSpm < 155 -> "Warmup / Recovery"
        targetSpm < 172 -> "Aerobic Jog"
        targetSpm < 186 -> "Steady Stride"
        targetSpm < 198 -> "Marathon Pace"
        else -> "Sprint / Intervals"
    }

    Box(
        modifier = modifier
            .size(dialSize)
            .testTag("cadence_dial_box"),
        contentAlignment = Alignment.Center
    ) {
        // Outer Radial Background Glow Effect
        Box(
            modifier = Modifier
                .size(dialSize * 0.9f)
                .graphicsLayer {
                    scaleX = pulseScale.value
                    scaleY = pulseScale.value
                }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = glowOpacity.value),
                            MaterialTheme.colorScheme.primary.copy(alpha = glowOpacity.value * 0.3f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Extract colors before Canvas (non-composable scope)
        val primaryColor = MaterialTheme.colorScheme.primary
        val secondaryColor = MaterialTheme.colorScheme.secondary
        val onPrimaryColor = MaterialTheme.colorScheme.onPrimary

        // Interactive Canvas Dial
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInteropFilter { event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                            val centerX = event.x - (event.device?.getMotionRange(MotionEvent.AXIS_X)?.max ?: 0f) / 2f
                            val centerPxX = event.x - 400f // Fallback centered touch math
                            // Calculate touch angle relative to center
                            val dx = event.x - (event.x / event.x)
                            val dy = event.y - (event.y / event.y)
                            // Angle math normalized to SPM range
                            val angleRad = atan2((event.y - 400f).toDouble(), (event.x - 400f).toDouble())
                            var angleDeg = Math.toDegrees(angleRad).toFloat()
                            if (angleDeg < 0) angleDeg += 360f

                            // Map angle (135° to 405°) to SPM
                            var normalizedAngle = angleDeg - 135f
                            if (normalizedAngle < 0) normalizedAngle += 360f

                            if (normalizedAngle <= 270f) {
                                val touchFraction = (normalizedAngle / 270f).coerceIn(0f, 1f)
                                val calculatedSpm = (minSpm + touchFraction * (maxSpm - minSpm)).toInt()
                                onSpmChanged(calculatedSpm.coerceIn(120, 240))
                            }
                            true
                        }
                        else -> false
                    }
                }
        ) {
            val strokeWidthPx = 16.dp.toPx()
            val radius = (size.minDimension - strokeWidthPx) / 2f
            val topLeft = Offset((size.width - radius * 2) / 2f, (size.height - radius * 2) / 2f)
            val arcSize = Size(radius * 2, radius * 2)

            // Breathing Guide Ring (expands and contracts)
            if (isPacing) {
                val breathingRadius = radius * breathingScale.value
                drawCircle(
                    color = primaryColor.copy(alpha = 0.15f),
                    radius = breathingRadius,
                    center = Offset(size.width / 2f, size.height / 2f)
                )
                drawCircle(
                    color = primaryColor.copy(alpha = 0.3f),
                    radius = breathingRadius,
                    center = Offset(size.width / 2f, size.height / 2f),
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Track Arc (Inactive Dark Border)
            drawArc(
                color = Color(0xFF222222),
                startAngle = startAngle,
                sweepAngle = sweepAngleTotal,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )

            // Active Arc (Neon Lime Gradient)
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        secondaryColor,
                        primaryColor,
                        primaryColor
                    )
                ),
                startAngle = startAngle,
                sweepAngle = activeSweepAngle.coerceAtLeast(2f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )

            // Handle Knob at activeSweepAngle
            val handleAngleRad = Math.toRadians((startAngle + activeSweepAngle).toDouble())
            val handleX = (size.width / 2f) + radius * cos(handleAngleRad).toFloat()
            val handleY = (size.height / 2f) + radius * sin(handleAngleRad).toFloat()

            // Outer handle ring
            drawCircle(
                color = onPrimaryColor,
                radius = 18.dp.toPx(),
                center = Offset(handleX, handleY)
            )
            drawCircle(
                color = primaryColor,
                radius = 12.dp.toPx(),
                center = Offset(handleX, handleY)
            )
            drawCircle(
                color = Color.White,
                radius = 5.dp.toPx(),
                center = Offset(handleX, handleY)
            )
        }

        // Center Metric Display
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "TARGET SPM",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "$targetSpm",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 72.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = (-2).sp,
                modifier = Modifier.testTag("target_spm_display")
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Category Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = paceCategory.uppercase(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }

        // Subtract (-) Button Positioned Left Outside Dial
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-16).dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), CircleShape)
                .clickable { onSpmChanged((targetSpm - 1).coerceAtLeast(120)) }
                .testTag("spm_minus_button"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "−",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Light
            )
        }

        // Add (+) Button Positioned Right Outside Dial
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 16.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), CircleShape)
                .clickable { onSpmChanged((targetSpm + 1).coerceAtMost(240)) }
                .testTag("spm_plus_button"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Light
            )
        }
    }
}
