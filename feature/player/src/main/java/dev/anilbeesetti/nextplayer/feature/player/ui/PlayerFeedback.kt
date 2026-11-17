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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
            .padding(top = 24.dp)
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
    val accentColor = Color(0xFF3EA6FF)
    val clampedSpeed = speed.coerceIn(0.25f, 4.0f)
    val fraction = ((clampedSpeed - 0.25f) / 3.75f).coerceIn(0f, 1f)
    val ticks = listOf(0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 3.5f)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.6f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .widthIn(min = 240.dp, max = 320.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "0.25x",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (clampedSpeed <= 0.25f) FontWeight.Bold else FontWeight.Medium,
                    color = if (clampedSpeed <= 0.25f) accentColor else Color.White.copy(alpha = 0.8f),
                )
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(20.dp),
                ) {
                    val trackY = size.height / 2f
                    val activeX = size.width * fraction

                    drawLine(
                        color = Color.White.copy(alpha = 0.3f),
                        start = Offset(0f, trackY),
                        end = Offset(size.width, trackY),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = accentColor,
                        start = Offset(0f, trackY),
                        end = Offset(activeX, trackY),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                    )

                    ticks.forEach { tick ->
                        val tickFraction = (tick - 0.25f) / 3.75f
                        val tickX = size.width * tickFraction
                        drawLine(
                            color = if (tickFraction <= fraction) Color.White else Color.White.copy(alpha = 0.55f),
                            start = Offset(tickX, trackY - 4.dp.toPx()),
                            end = Offset(tickX, trackY + 4.dp.toPx()),
                            strokeWidth = 1.5.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }

                    drawCircle(
                        color = accentColor,
                        radius = 6.5.dp.toPx(),
                        center = Offset(activeX, trackY),
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3.5.dp.toPx(),
                        center = Offset(activeX, trackY),
                    )
                }
                Text(
                    text = "4.0x",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (clampedSpeed >= 4.0f) FontWeight.Bold else FontWeight.Medium,
                    color = if (clampedSpeed >= 4.0f) accentColor else Color.White.copy(alpha = 0.8f),
                )
            }

            Text(
                text = ">> ${formatSpeed(clampedSpeed)}x Speed Playing",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
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
