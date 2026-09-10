package com.tutelopezmusic.stagekeyslive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class FxKnobType(val label: String, val color: Color, val defaultValue: Float) {
    REVERB("REV", Color(0xFFA855F7), 0.0f),      // Violet / Purple
    CHORUS("CHO", Color(0xFF2DD4BF), 0.0f),      // Green / Teal
    TONE("TONE", Color(0xFFFB7185), 1.0f)        // Orange / Coral
}

/**
 * High-performance throttler ensuring native calls never exceed ~30 updates/sec (~33ms)
 * while always guaranteeing delivery of the final settled value.
 */
class EngineCcThrottler(
    private val intervalMs: Long = 33L
) {
    private var lastSentTimeMs: Long = 0L
    private var pendingTask: Job? = null

    fun sendThrottled(
        coroutineScope: kotlinx.coroutines.CoroutineScope,
        value: Float,
        action: (Float) -> Unit
    ) {
        val now = System.currentTimeMillis()
        if (now - lastSentTimeMs >= intervalMs) {
            lastSentTimeMs = now
            pendingTask?.cancel()
            pendingTask = null
            action(value)
        } else {
            pendingTask?.cancel()
            pendingTask = coroutineScope.launch {
                val delayTime = intervalMs - (System.currentTimeMillis() - lastSentTimeMs)
                if (delayTime > 0) delay(delayTime)
                lastSentTimeMs = System.currentTimeMillis()
                action(value)
            }
        }
    }
}

@Composable
fun rememberEngineCcThrottler(): EngineCcThrottler {
    return remember { EngineCcThrottler() }
}

@Composable
fun MainstageRotaryKnob(
    value: Float, // 0.0f .. 1.0f
    onValueChange: (Float) -> Unit,
    type: FxKnobType,
    modifier: Modifier = Modifier,
    isMidiMapped: Boolean = false,
    knobSize: Dp = 48.dp,
    showLabel: Boolean = true
) {
    var internalValue by remember(value) { mutableStateOf(value.coerceIn(0f, 1f)) }
    val displayPercent = (internalValue * 100f).roundToInt().coerceIn(0, 100)

    Column(
        modifier = modifier.width(knobSize),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(knobSize)
                .pointerInput(type.defaultValue) {
                    detectTapGestures(
                        onDoubleTap = {
                            val def = type.defaultValue
                            internalValue = def
                            onValueChange(def)
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            // Vertical drag: dragging UP increases value, dragging DOWN decreases
                            // ~0.8 units per dp (0.008f normalized)
                            val delta = -dragAmount.y * 0.008f
                            val newVal = (internalValue + delta).coerceIn(0f, 1f)
                            if (newVal != internalValue) {
                                internalValue = newVal
                                onValueChange(newVal)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Knob Canvas (Background ring + Colored active arc)
            Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                val strokeWidth = 3.5.dp.toPx()
                val diameter = size.minDimension - strokeWidth
                val topLeft = Offset(
                    (size.width - diameter) / 2f,
                    (size.height - diameter) / 2f
                )
                val arcSize = Size(diameter, diameter)

                // 270 degrees total sweep: from 135 deg to 405 deg
                val startAngle = 135f
                val maxSweep = 270f
                val activeSweep = maxSweep * internalValue.coerceIn(0f, 1f)

                // Base neutral background ring
                drawArc(
                    color = Color(0xFF2A2D3A),
                    startAngle = startAngle,
                    sweepAngle = maxSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Active Colored Arc
                if (activeSweep > 0.5f) {
                    drawArc(
                        color = type.color,
                        startAngle = startAngle,
                        sweepAngle = activeSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }

            // Numeric display inside knob
            Text(
                text = "$displayPercent",
                color = if (displayPercent > 0) TextLight else TextDark,
                fontSize = if (knobSize < 42.dp) 8.sp else 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            // MIDI Mapped Indicator Dot (Top-Right corner)
            if (isMidiMapped) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 1.dp, y = (-1).dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF59E0B)) // Warm Gold
                )
            }
        }

        // 3-letter label
        if (showLabel) {
            Text(
                text = type.label,
                color = type.color.copy(alpha = 0.85f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
