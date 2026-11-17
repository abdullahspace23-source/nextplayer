package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR
import dev.anilbeesetti.nextplayer.core.ui.extensions.copy
import dev.anilbeesetti.nextplayer.feature.player.extensions.formatted
import dev.anilbeesetti.nextplayer.feature.player.state.TapGestureState
import dev.anilbeesetti.nextplayer.feature.player.state.VerticalGesture
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun InfoView(
    modifier: Modifier = Modifier,
    info: String,
    textStyle: TextStyle = MaterialTheme.typography.headlineMedium.copy(
        fontWeight = FontWeight.Bold,
    ),
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = info,
            style = textStyle,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Shows the cumulative amount skipped by repeated D-pad left/right seeks while the controls are
 * hidden, along with the resulting position. Fades out shortly after the last seek.
 */
@Composable
internal fun BoxScope.DpadSeekIndicator(visible: Boolean, offsetMs: Long, positionMs: Long) {
    AnimatedVisibility(
        modifier = Modifier.align(Alignment.Center),
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 80)),
        exit = fadeOut(animationSpec = tween(durationMillis = 80)),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.Black.copy(alpha = 0.6f),
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = 20.dp,
                    vertical = 12.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    painter = painterResource(coreUiR.drawable.ic_fast),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(28.dp)
                        .rotate(degrees = if (offsetMs < 0) 180f else 0f),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${if (offsetMs >= 0) "+" else "-"}${abs(
                            n = offsetMs,
                        ).milliseconds.inWholeSeconds}s",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = positionMs.milliseconds.formatted(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun BoxScope.PlayerGestureIndicators(tapGestureState: TapGestureState) {
    DoubleTapIndicator(tapGestureState = tapGestureState)

    AnimatedVisibility(
        modifier = Modifier
            .padding(top = 20.dp)
            .align(Alignment.TopCenter),
        visible = tapGestureState.isLongPressGestureInAction,
        enter = fadeIn(animationSpec = tween(durationMillis = 80)),
        exit = fadeOut(animationSpec = tween(durationMillis = 80)),
    ) {
        LongPressSpeedIndicator(speed = tapGestureState.activeLongPressSpeed)
    }
}

@Composable
private fun LongPressSpeedIndicator(speed: Float) {
    val activeBlue = Color(0xFF2196F3)
    val clampedSpeed = speed.coerceIn(0.25f, 4.0f)
    val fraction = speedToFraction(clampedSpeed)
    val ticks = listOf(0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f)
    val textShadow = Shadow(color = Color.Black.copy(alpha = 0.45f), blurRadius = 2f)

    Column(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .widthIn(max = 300.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier.height(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "0.25x",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.5.sp,
                        shadow = textShadow,
                    ),
                    fontWeight = if (clampedSpeed <= 0.25f) FontWeight.Bold else FontWeight.Medium,
                    color = if (clampedSpeed <= 0.25f) activeBlue else Color.White,
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Layout(
                    modifier = Modifier.fillMaxWidth(),
                    content = {
                        ticks.forEach { tick ->
                            val isSelected = abs(clampedSpeed - tick) < 0.01f
                            Text(
                                text = "${formatSpeed(tick)}x",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    shadow = textShadow,
                                ),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) activeBlue else Color.White,
                            )
                        }
                    },
                ) { measurables, constraints ->
                    val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
                    val maxHeight = placeables.maxOfOrNull { it.height } ?: 0
                    layout(constraints.maxWidth, maxHeight) {
                        placeables.forEachIndexed { index, placeable ->
                            val tickFraction = (index + 1) / 7f
                            val centerX = (constraints.maxWidth * tickFraction).roundToInt()
                            placeable.placeRelative(
                                x = centerX - placeable.width / 2,
                                y = 0,
                            )
                        }
                    }
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp),
                ) {
                    val trackY = size.height / 2f
                    val activeX = size.width * fraction

                    drawLine(
                        color = Color(0xFF808080).copy(alpha = 0.65f),
                        start = Offset(0f, trackY),
                        end = Offset(size.width, trackY),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                    )

                    for (index in ticks.indices) {
                        val tickX = size.width * ((index + 1) / 7f)
                        drawCircle(
                            color = Color.White,
                            radius = 2.dp.toPx(),
                            center = Offset(tickX, trackY),
                        )
                    }

                    drawCircle(
                        color = activeBlue,
                        radius = 5.dp.toPx(),
                        center = Offset(activeX, trackY),
                    )
                }
            }

            Box(
                modifier = Modifier.height(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "4.0x",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.5.sp,
                        shadow = textShadow,
                    ),
                    fontWeight = if (clampedSpeed >= 4.0f) FontWeight.Bold else FontWeight.Medium,
                    color = if (clampedSpeed >= 4.0f) activeBlue else Color.White,
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter = painterResource(coreUiR.drawable.ic_fast),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = "${formatSpeed(clampedSpeed)}x Speed Playing",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.5.sp,
                    shadow = textShadow,
                ),
                fontWeight = FontWeight.Medium,
                color = Color.White,
            )
        }
    }
}

private fun speedToFraction(speed: Float): Float {
    val anchors = floatArrayOf(0.25f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f)
    val clamped = speed.coerceIn(0.25f, 4.0f)
    for (i in 0 until anchors.lastIndex) {
        val start = anchors[i]
        val end = anchors[i + 1]
        if (clamped <= end) {
            val local = (clamped - start) / (end - start)
            return (i + local) / anchors.lastIndex
        }
    }
    return 1f
}

private fun formatSpeed(speed: Float): String {
    val hundredths = (speed * 100f).roundToInt()
    val whole = hundredths / 100
    val frac = hundredths % 100
    return when {
        frac == 0 -> "$whole.0"
        frac % 10 == 0 -> "$whole.${frac / 10}"
        else -> "$whole.${frac.toString().padStart(2, '0')}"
    }
}

@Composable
internal fun BoxScope.PlayerVerticalGestureIndicators(
    activeGesture: VerticalGesture?,
    volumePercentage: Int,
    maxVolumePercentage: Int,
    brightnessPercentage: Int,
) {
    val systemBarsPadding = WindowInsets.systemBars.asPaddingValues()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .displayCutoutPadding()
            .padding(
                systemBarsPadding.copy(
                    top = 0.dp,
                    bottom = 0.dp,
                ),
            )
            .padding(24.dp),
    ) {
        AnimatedVisibility(
            modifier = Modifier.align(Alignment.CenterStart),
            visible = activeGesture == VerticalGesture.VOLUME,
            enter = fadeIn(animationSpec = tween(durationMillis = 60)),
            exit = fadeOut(animationSpec = tween(durationMillis = 60)),
        ) {
            VerticalProgressView(
                value = volumePercentage,
                maxValue = maxVolumePercentage,
                icon = painterResource(coreUiR.drawable.ic_volume),
            )
        }

        AnimatedVisibility(
            modifier = Modifier.align(Alignment.CenterEnd),
            visible = activeGesture == VerticalGesture.BRIGHTNESS,
            enter = fadeIn(animationSpec = tween(durationMillis = 60)),
            exit = fadeOut(animationSpec = tween(durationMillis = 60)),
        ) {
            VerticalProgressView(
                value = brightnessPercentage,
                icon = painterResource(coreUiR.drawable.ic_brightness),
            )
        }
    }
}
