package com.tutelopezmusic.stagekeyslive

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.*
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// PRO MIXER SYSTEM (FASE 3)
// ─────────────────────────────────────────────────────────────────────────────

object FaderMath {
    // V(-50 dB) = 10^(-50/20) = 10^(-2.5) ~= 0.00316227766f
    val V_AT_MIN_50_DB: Float = 10f.pow(-2.5f)

    const val CAP_HEIGHT_FLOAT = 36f
    val CAP_HEIGHT_DP: Dp = 36.dp

    /**
     * Retorna la coordenada Y del centro de la tapa para una posición p en [0..1]
     * dada la altura del track y la altura de la tapa:
     * yCentro(p) = top + capH/2 + (1 - p) * (trackH - capH)
     */
    fun yCentro(p: Float, trackHeight: Float, capHeight: Float = CAP_HEIGHT_FLOAT, top: Float = 0f): Float {
        val usable = (trackHeight - capHeight).coerceAtLeast(0f)
        return top + (capHeight / 2f) + (1f - p.coerceIn(0f, 1f)) * usable
    }

    fun yCentroDp(p: Float, trackHeight: Dp, capHeight: Dp = CAP_HEIGHT_DP, top: Dp = 0.dp): Dp {
        val usable = (trackHeight - capHeight).coerceAtLeast(0.dp)
        return top + (capHeight / 2) + (usable * (1f - p.coerceIn(0f, 1f)))
    }

    /**
     * Convierte la posición física del fader p en [0.0, 1.0] a Ganancia de volumen V en [0.0, 1.0].
     * - Para p >= 1/6: dB = -50 + (p - 1/6) / (5/6) * 50
     * - Para p < 1/6: V baja linealmente desde V(-50 dB) hasta 0 en p=0 (zona -inf).
     */
    fun positionToVolume(p: Float): Float {
        val clampedP = p.coerceIn(0f, 1f)
        return if (clampedP >= 1f / 6f) {
            val db = -50f + (clampedP - (1f / 6f)) / (5f / 6f) * 50f
            10f.pow(db / 20f).coerceIn(0f, 1f)
        } else {
            (clampedP / (1f / 6f)) * V_AT_MIN_50_DB
        }
    }

    /**
     * Convierte la Ganancia de volumen V en [0.0, 1.0] a la posición del fader p en [0.0, 1.0].
     */
    fun volumeToPosition(v: Float): Float {
        val clampedV = v.coerceIn(0f, 1f)
        return when {
            clampedV <= 0.00001f -> 0f
            clampedV < V_AT_MIN_50_DB -> (clampedV / V_AT_MIN_50_DB) * (1f / 6f)
            else -> {
                val db = 20f * log10(clampedV)
                ((1f / 6f) + (5f / 6f) * ((db + 50f) / 50f)).coerceIn(0f, 1f)
            }
        }
    }

    /**
     * Calcula la lectura en dB a partir de V [0.0, 1.0].
     */
    fun volumeToDb(v: Float): Float {
        return if (v <= 0.00001f) Float.NEGATIVE_INFINITY else (20f * log10(v.coerceIn(0f, 1f)))
    }

    /**
     * Formatea el valor de dB para lectura en pantalla (ej: "0.0 dB", "-12.4 dB", "-∞ dB").
     */
    fun formatDb(v: Float): String {
        return if (v <= 0.00001f) {
            "-∞ dB"
        } else {
            val db = 20f * log10(v.coerceIn(0f, 1f))
            if (kotlin.math.abs(db) < 0.05f) {
                "0.0 dB"
            } else {
                val rounded = (db * 10).toInt() / 10f
                "$rounded dB"
            }
        }
    }
}

/**
 * Medidor VU profesional de 32 segmentos (-60 dBFS a 0 dBFS).
 * Lee [levelProvider] estrictamente durante la fase de dibujo para evitar recomposiciones por frame.
 */
@Composable
fun ProMeter(
    levelProvider: () -> Float,
    modifier: Modifier = Modifier,
    segmentsCount: Int = 32,
    inactiveColor: Color = MeterSegmentOff,
    onClipTriggered: () -> Unit = {}
) {
    // Calculamos caída de pico en fase de dibujo
    var peakLevel by remember { mutableFloatStateOf(0f) }
    var peakHoldUntil by remember { mutableLongStateOf(0L) }

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(MeterWellBg)
            .border(0.5.dp, BorderHairline, RoundedCornerShape(3.dp))
    ) {
        val currentLevel = levelProvider().coerceIn(0f, 1f)
        val now = System.currentTimeMillis()

        if (currentLevel >= 0.999f) {
            onClipTriggered()
        }

        if (currentLevel >= peakLevel) {
            peakLevel = currentLevel
            peakHoldUntil = now + 1200L // 1.2 segundos de retención
        } else if (now > peakHoldUntil) {
            peakLevel = (peakLevel - 0.04f).coerceAtLeast(currentLevel)
        }

        val totalHeight = size.height
        val totalWidth = size.width
        val gap = 1.5f
        val segH = (totalHeight - (segmentsCount - 1) * gap) / segmentsCount
        val activeSegments = (currentLevel * segmentsCount).toInt().coerceIn(0, segmentsCount)
        val peakSegment = (peakLevel * segmentsCount).toInt().coerceIn(0, segmentsCount - 1)

        for (i in 0 until segmentsCount) {
            val segIdx = segmentsCount - 1 - i // de abajo hacia arriba (0 = fondo, 31 = pico)
            val top = i * (segH + gap)

            // Color del segmento según zona dBFS
            // 0..21 (-60 a -12 dBFS) -> Verde StateOk
            // 22..27 (-12 a -3 dBFS)  -> Amarillo StateSolo
            // 28..31 (-3 a 0 dBFS)    -> Rojo StateClip
            val activeColor = when {
                segIdx >= 28 -> StateClip
                segIdx >= 22 -> StateSolo
                else -> StateOk
            }

            val isLit = segIdx < activeSegments || (segIdx == peakSegment && peakLevel > 0.02f)
            val color = if (isLit) activeColor else inactiveColor

            drawRoundRect(
                color = color,
                topLeft = Offset(0f, top),
                size = Size(totalWidth, segH),
                cornerRadius = CornerRadius(1f, 1f)
            )
        }
    }
}

/**
 * Rastreador de dimensiones y límites de faders para validación automática en pruebas.
 */
object FaderBoundsTracker {
    data class Entry(val tag: String, val bounds: Rect, val usableTravelDp: Float)
    val entries = mutableListOf<Entry>()
    var enabled: Boolean = false

    fun record(tag: String, bounds: Rect, usableTravelDp: Float) {
        if (enabled) {
            entries.add(Entry(tag, bounds, usableTravelDp))
        }
    }

    fun clear() {
        entries.clear()
    }
}

/**
 * Fader profesional con ley lineal en dB, marcas equiespaciadas, imán de 0 dB,
 * doble toque para valor por defecto y tapa lavanda de consola de estudio.
 * Utiliza exactamente la misma función yCentro(p) para marcas, tapa y escala.
 */
@Composable
fun ProFader(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    defaultVolume: Float = 0.8f,
    accentColor: Color = AccentLavender,
    capHeight: Dp = FaderMath.CAP_HEIGHT_DP,
    faderTag: String = "fader"
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    var wasSnapped by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .onGloballyPositioned { coords ->
                if (FaderBoundsTracker.enabled) {
                    val bounds = coords.boundsInRoot()
                    val usableTravelDp = with(density) { (coords.size.height.toDp() - capHeight).value }
                    FaderBoundsTracker.record(faderTag, bounds, usableTravelDp)
                }
            }
            .clip(RoundedCornerShape(8.dp))
            .background(MeterWellBg)
            .border(1.dp, BorderHairline, RoundedCornerShape(8.dp))
            .pointerInput(defaultVolume) {
                detectTapGestures(
                    onDoubleTap = {
                        onVolumeChange(defaultVolume)
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val capHeightPx = capHeight.toPx()
                    val usablePx = (size.height - capHeightPx).coerceAtLeast(1f)
                    val clampedY = change.position.y.coerceIn(capHeightPx / 2f, size.height - capHeightPx / 2f)
                    val pos = 1f - ((clampedY - capHeightPx / 2f) / usablePx)
                    val rawVol = FaderMath.positionToVolume(pos)
                    val rawDb = FaderMath.volumeToDb(rawVol)

                    // Imán suave de ±0.5 dB alrededor de 0 dB (pos = 1.0f)
                    val effectiveVol = if (rawDb in -0.5f..0.5f && pos > 0.95f) {
                        if (!wasSnapped) {
                            triggerHapticFeedback()
                            wasSnapped = true
                        }
                        1.0f
                    } else {
                        wasSnapped = false
                        rawVol
                    }
                    onVolumeChange(effectiveVol)
                }
            },
        contentAlignment = Alignment.TopCenter
    ) {
        val faderPosition = FaderMath.volumeToPosition(volume)
        val usableTravel = (maxHeight - capHeight).coerceAtLeast(0.dp)
        val capOffset = usableTravel * (1f - faderPosition)

        // Dibujar riel y marcas laterales sincronizadas con yCentro(p)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val capHeightPx = capHeight.toPx()
            val totalH = size.height
            val centerX = size.width / 2f
            val slotWidthPx = 4.dp.toPx()
            val slotLeft = centerX - slotWidthPx / 2f
            val slotRight = centerX + slotWidthPx / 2f
            val topSlot = capHeightPx / 2f
            val bottomSlot = totalH - capHeightPx / 2f

            // 1. Riel hundido central
            drawRoundRect(
                color = SurfaceGroove,
                topLeft = Offset(slotLeft, topSlot),
                size = Size(slotWidthPx, (bottomSlot - topSlot).coerceAtLeast(1f)),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
            drawRoundRect(
                color = MeterSegmentOff,
                topLeft = Offset(slotLeft, topSlot),
                size = Size(slotWidthPx, (bottomSlot - topSlot).coerceAtLeast(1f)),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                style = Stroke(width = 0.5.dp.toPx())
            )

            // 2. Marcas mayores a ambos lados de la ranura: 0, -10, -20, -30, -40, -50, -∞
            val majorFractions = listOf(
                1f to true,       // 0 dB
                5f / 6f to false, // -10 dB
                4f / 6f to false, // -20 dB
                3f / 6f to false, // -30 dB
                2f / 6f to false, // -40 dB
                1f / 6f to false, // -50 dB
                0f to false       // -∞
            )
            for ((p, isZeroDb) in majorFractions) {
                val y = FaderMath.yCentro(p, totalH, capHeightPx)
                val tickColor = if (isZeroDb) AccentLavender.copy(alpha = 0.95f) else TextSecondary.copy(alpha = 0.5f)
                val strokeW = if (isZeroDb) 1.5.dp.toPx() else 1.dp.toPx()
                val tickLen = 6.dp.toPx()

                // Marca izquierda
                drawLine(
                    color = tickColor,
                    start = Offset(slotLeft - 2.dp.toPx(), y),
                    end = Offset(slotLeft - 2.dp.toPx() - tickLen, y),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
                // Marca derecha
                drawLine(
                    color = tickColor,
                    start = Offset(slotRight + 2.dp.toPx(), y),
                    end = Offset(slotRight + 2.dp.toPx() + tickLen, y),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
            }

            // 3. Marcas menores sutiles cada 5 dB
            for (dbStep in -45..-5 step 10) {
                val p = (1f / 6f) + (5f / 6f) * ((dbStep + 50f) / 50f)
                val y = FaderMath.yCentro(p, totalH, capHeightPx)
                val tickLen = 3.5.dp.toPx()

                drawLine(
                    color = BorderHairline.copy(alpha = 0.7f),
                    start = Offset(slotLeft - 2.dp.toPx(), y),
                    end = Offset(slotLeft - 2.dp.toPx() - tickLen, y),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = BorderHairline.copy(alpha = 0.7f),
                    start = Offset(slotRight + 2.dp.toPx(), y),
                    end = Offset(slotRight + 2.dp.toPx() + tickLen, y),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        // Tapa ergonómica de consola (Fader Cap)
        Box(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .height(capHeight)
                .offset(y = capOffset)
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(5.dp), ambientColor = Color.Black, spotColor = Color.Black)
                .clip(RoundedCornerShape(5.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accentColor.copy(alpha = 0.95f),
                            accentColor.copy(alpha = 0.70f)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Línea indicadora central blanca: coincide con yCentro(p)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .background(Color.White)
            )
        }
    }
}

/**
 * Escala de marcas dB equiespaciadas: 0, -10, -20, -30, -40, -50, -∞
 * Utiliza exactamente la misma función yCentro(p) para alineación perfecta con la tapa.
 */
@Composable
fun FaderScale(
    modifier: Modifier = Modifier,
    capHeight: Dp = FaderMath.CAP_HEIGHT_DP
) {
    val marks = listOf(
        1.0f to "0",
        5f / 6f to "-10",
        4f / 6f to "-20",
        3f / 6f to "-30",
        2f / 6f to "-40",
        1f / 6f to "-50",
        0.0f to "-∞"
    )

    BoxWithConstraints(modifier = modifier) {
        val totalH = maxHeight
        marks.forEach { (fraction, label) ->
            val yCenter = FaderMath.yCentroDp(fraction, totalH, capHeight)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val yPos = (yCenter.toPx() - (placeable.height / 2f)).roundToInt()
                        layout(placeable.width, placeable.height) {
                            placeable.placeRelative(0, yPos)
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = label,
                    color = if (fraction == 1.0f) AccentLavender else TextTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Default
                )
                Spacer(Modifier.width(3.dp))
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(1.dp)
                        .background(if (fraction == 1.0f) AccentLavender else BorderHairline)
                )
            }
        }
    }
}

/**
 * Perilla rotativa compacta para Pan y envíos de efectos con doble tap para centrar.
 */
@Composable
fun RotaryKnob(
    value: Float, // 0.0f .. 1.0f (0.5 es Centro)
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    accentColor: Color = AccentPrimary
) {
    var internalValue by remember(value) { mutableStateOf(value.coerceIn(0f, 1f)) }

    Box(
        modifier = modifier
            .size(size)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        internalValue = 0.5f
                        onValueChange(0.5f)
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    // Arrastre vertical: arriba aumenta (hacia R), abajo disminuye (hacia L)
                    val delta = -dragAmount.y * 0.006f
                    val newVal = (internalValue + delta).coerceIn(0f, 1f)
                    if (newVal != internalValue) {
                        internalValue = newVal
                        onValueChange(newVal)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(2.dp)) {
            val strokeWidth = 2.5.dp.toPx()
            val diameter = this.size.minDimension - strokeWidth
            val topLeft = Offset(
                (this.size.width - diameter) / 2f,
                (this.size.height - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)

            // Pista de fondo
            drawArc(
                color = BorderHairline,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Barrido de Pan desde el centro (270 grados = 12 en punto)
            val panOffset = (internalValue - 0.5f) // -0.5 .. +0.5
            val sweep = panOffset * 270f // -135 .. +135

            if (kotlin.math.abs(sweep) > 1.5f) {
                drawArc(
                    color = accentColor,
                    startAngle = 270f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            } else {
                // Pequeña marca central en reposo
                drawArc(
                    color = accentColor,
                    startAngle = 268f,
                    sweepAngle = 4f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth + 0.5f, cap = StrokeCap.Round)
                )
            }

            // Cuerpo interior del knob
            val innerRadius = (diameter / 2f) - strokeWidth - 1.5.dp.toPx()
            if (innerRadius > 0) {
                drawCircle(
                    color = SurfaceCard,
                    radius = innerRadius,
                    center = center
                )
                // Indicador apuntando hacia el ángulo actual
                val currentAngleRad = Math.toRadians((270f + sweep).toDouble())
                val tickStart = Offset(
                    (center.x + (innerRadius * 0.35f) * kotlin.math.cos(currentAngleRad)).toFloat(),
                    (center.y + (innerRadius * 0.35f) * kotlin.math.sin(currentAngleRad)).toFloat()
                )
                val tickEnd = Offset(
                    (center.x + (innerRadius * 0.85f) * kotlin.math.cos(currentAngleRad)).toFloat(),
                    (center.y + (innerRadius * 0.85f) * kotlin.math.sin(currentAngleRad)).toFloat()
                )
                drawLine(
                    color = TextPrimary,
                    start = tickStart,
                    end = tickEnd,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/**
 * Channel Strip profesional con anatomía completa:
 * 1. Pastilla de nombre con color de identidad
 * 2. Selector de preset / SF2
 * 3. Botón FX
 * 4. Perilla Pan / Reverb
 * 5. Botones M / S
 * 6. Fader vertical + Escala de dB + ProMeter
 * 7. Lectura numérica dB y CLIP latch
 */
@Composable
fun ProChannelStrip(
    channel: ChannelStripState,
    levelProvider: () -> Float,
    onVolumeChange: (Float) -> Unit,
    onMuteToggle: () -> Unit,
    onSoloToggle: () -> Unit,
    onGearClick: () -> Unit,
    onFxClick: () -> Unit,
    onPanChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isCondensed: Boolean = false,
    isMidiMapped: Boolean = false
) {
    val channelColor = parseColorHex(channel.colorHex)
    var isClipped by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(listOf(StripTop, StripBottom))
            )
            .border(1.dp, BorderHairline, RoundedCornerShape(14.dp))
            .padding(6.dp)
    ) {
        val stripWidth = maxWidth
        val stripHeight = maxHeight
        val isCompact = isCondensed || stripWidth < 90.dp || stripHeight < 320.dp
        val capH = if (isCompact) 24.dp else FaderMath.CAP_HEIGHT_DP

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (isCompact) 2.dp else 4.dp)
        ) {
            // 1. Pastilla de Nombre con color de canal
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isCompact) 24.dp else 28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(channelColor)
                    .clickable(onClick = onGearClick)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = channel.name,
                    color = BgDark,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isMidiMapped) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(StateSolo)
                    )
                    Spacer(Modifier.width(2.dp))
                }
                Icon(
                    TablerIcons.Settings,
                    contentDescription = "Configuración",
                    tint = BgDark.copy(alpha = 0.8f),
                    modifier = Modifier.size(11.dp)
                )
            }

            if (!isCompact) {
                // 2. Selector de Sonido / SF2
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(SurfaceCard)
                        .border(1.dp, BorderHairline, RoundedCornerShape(6.dp))
                        .clickable(onClick = onGearClick)
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = channel.sf2Name.substringBefore(".sf2"),
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 3. Botón FX
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(SurfaceCard)
                        .border(1.dp, BorderHairline, RoundedCornerShape(6.dp))
                        .clickable(onClick = onFxClick)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "FX",
                        color = AccentLavender,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${(channel.reverbSend * 100).toInt()}%",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                // 4. Perilla Pan con L·C·R
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    RotaryKnob(
                        value = channel.pan,
                        onValueChange = onPanChange,
                        size = 32.dp,
                        accentColor = channelColor
                    )
                    Text(
                        text = "L · C · R",
                        color = TextTertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 5. Botones M y S lado a lado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // MUTE
                val muteBg by animateColorAsState(if (channel.isMuted) StateMute else SurfaceCard)
                val muteText by animateColorAsState(if (channel.isMuted) Color.White else TextSecondary)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(if (isCompact) 24.dp else 28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(muteBg)
                        .border(1.dp, if (channel.isMuted) StateMute else BorderHairline, RoundedCornerShape(6.dp))
                        .clickable(onClick = onMuteToggle),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "M",
                        color = muteText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // SOLO
                val soloBg by animateColorAsState(if (channel.isSoloed) StateSolo else SurfaceCard)
                val soloText by animateColorAsState(if (channel.isSoloed) BgDark else TextSecondary)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(if (isCompact) 24.dp else 28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(soloBg)
                        .border(1.dp, if (channel.isSoloed) StateSolo else BorderHairline, RoundedCornerShape(6.dp))
                        .clickable(onClick = onSoloToggle),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "S",
                        color = soloText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 6. Fader Vertical + Escala dB + ProMeter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = if (isCompact) 1.dp else 4.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isCompact) {
                    FaderScale(
                        modifier = Modifier
                            .width(28.dp)
                            .fillMaxHeight()
                    )
                }

                ProFader(
                    volume = channel.volume,
                    onVolumeChange = onVolumeChange,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    capHeight = capH,
                    accentColor = AccentLavender,
                    faderTag = "channel_${channel.id}"
                )

                ProMeter(
                    levelProvider = levelProvider,
                    modifier = Modifier
                        .width(10.dp)
                        .fillMaxHeight(),
                    onClipTriggered = { isClipped = true }
                )
            }

            // 7. Lectura numérica en dB
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = FaderMath.formatDb(channel.volume),
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                if (isCompact && isClipped) {
                    Spacer(Modifier.width(3.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(StateClip)
                            .clickable { isClipped = false }
                    )
                }
            }

            if (!isCompact) {
                // 8. Pastilla CLIP Latch
                val clipBg by animateColorAsState(if (isClipped) StateClip else MeterSegmentOff)
                val clipText by animateColorAsState(if (isClipped) Color.White else TextTertiary)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(clipBg)
                        .clickable { isClipped = false },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "CLIP",
                        color = clipText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

/**
 * Strip Master ("VOL. GENERAL"):
 * Anclado a la derecha, cabecera violeta, switch de limitador, fader master y meter estéreo.
 */
@Composable
fun ProMasterStrip(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    levelProvider: () -> Float,
    isLimiterActive: Boolean,
    onLimiterToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onMidiMapClick: () -> Unit = {}
) {
    var isClipped by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(listOf(StripTop, StripBottom))
            )
            .border(1.2.dp, AccentPrimary.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(6.dp)
    ) {
        val isCompact = maxWidth < 100.dp || maxHeight < 320.dp
        val capH = if (isCompact) 24.dp else FaderMath.CAP_HEIGHT_DP

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (isCompact) 2.dp else 4.dp)
        ) {
            // Cabecera Violeta
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isCompact) 24.dp else 28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AccentPrimary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "MASTER",
                    color = Color.White,
                    fontSize = if (isCompact) 11.sp else 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            }

            // Switch Limiter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isCompact) 24.dp else 26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceCard)
                    .border(1.dp, BorderHairline, RoundedCornerShape(6.dp))
                    .clickable { onLimiterToggle(!isLimiterActive) }
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("LIMITER", color = if (isLimiterActive) StateSolo else TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isLimiterActive) StateSolo else TextTertiary)
                )
            }

            // Fader y Medidor Dual
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = if (isCompact) 1.dp else 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isCompact) {
                    FaderScale(
                        modifier = Modifier
                            .width(28.dp)
                            .fillMaxHeight()
                    )
                }

                ProFader(
                    volume = volume,
                    onVolumeChange = onVolumeChange,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    capHeight = capH,
                    accentColor = AccentPrimary,
                    faderTag = "master"
                )

                // Stereo Meter (Canal L y Canal R)
                Row(
                    modifier = Modifier.width(if (isCompact) 12.dp else 16.dp).fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ProMeter(
                        levelProvider = levelProvider,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClipTriggered = { isClipped = true }
                    )
                    ProMeter(
                        levelProvider = levelProvider,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }

            // Lectura numérica en dB
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = FaderMath.formatDb(volume),
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                if (isCompact && isClipped) {
                    Spacer(Modifier.width(3.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(StateClip)
                            .clickable { isClipped = false }
                    )
                }
            }

            if (!isCompact) {
                // Pastilla CLIP Latch
                val clipBg by animateColorAsState(if (isClipped) StateClip else MeterSegmentOff)
                val clipText by animateColorAsState(if (isClipped) Color.White else TextTertiary)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(clipBg)
                        .clickable { isClipped = false },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "CLIP",
                        color = clipText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                // Botón MIDI MAP
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(SurfaceCard)
                        .border(1.dp, BorderHairline, RoundedCornerShape(6.dp))
                        .clickable(onClick = onMidiMapClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "MIDI MAP",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Strip Metrónomo: Click, BPM, TAP y control de volumen con fader profesional.
 */
@Composable
fun ProMetronomeStrip(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    enabled: Boolean,
    onToggle: () -> Unit,
    bpm: Int,
    onTapTempo: () -> Unit,
    levelProvider: () -> Float,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(listOf(StripTop, StripBottom))
            )
            .border(1.dp, BorderHairline, RoundedCornerShape(14.dp))
            .padding(6.dp)
    ) {
        val isCompact = maxWidth < 96.dp || maxHeight < 320.dp
        val capH = if (isCompact) 24.dp else FaderMath.CAP_HEIGHT_DP

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (isCompact) 2.dp else 4.dp)
        ) {
            // Cabecera CLICK
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isCompact) 24.dp else 28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (enabled) StateOk else SurfaceCard)
                    .border(1.dp, if (enabled) StateOk else BorderHairline, RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "CLICK ${if (enabled) "ON" else "OFF"}",
                    color = if (enabled) BgDark else TextSecondary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // BPM & TAP
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isCompact) 24.dp else 26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceCard)
                    .border(1.dp, BorderHairline, RoundedCornerShape(6.dp))
                    .clickable(onClick = onTapTempo)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("$bpm", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("TAP", color = AccentPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            // Fader y Medidor
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = if (isCompact) 1.dp else 4.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isCompact) {
                    FaderScale(
                        modifier = Modifier
                            .width(26.dp)
                            .fillMaxHeight()
                    )
                }

                ProFader(
                    volume = volume,
                    onVolumeChange = onVolumeChange,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    capHeight = capH,
                    accentColor = StateOk,
                    faderTag = "metronome"
                )

                ProMeter(
                    levelProvider = levelProvider,
                    modifier = Modifier.width(10.dp).fillMaxHeight()
                )
            }

            // Lectura numérica en dB
            Text(
                text = FaderMath.formatDb(volume),
                color = TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Barra de scroll horizontal fina violeta de consola para el mezclador.
 */
@Composable
fun FineVioletScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    if (scrollState.maxValue > 0) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(SurfacePanel)
        ) {
            val totalW = maxWidth
            val thumbWidth = (totalW * 0.25f).coerceAtLeast(24.dp)
            val maxOffset = totalW - thumbWidth
            val progress = (scrollState.value.toFloat() / scrollState.maxValue.toFloat()).coerceIn(0f, 1f)
            val thumbOffset = maxOffset * progress

            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .width(thumbWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(AccentPrimary)
            )
        }
    }
}
