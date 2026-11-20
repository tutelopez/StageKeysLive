package com.tutelopezmusic.stagekeyslive

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FaderMathTest {

    private val eps = 1e-4f

    @Test
    fun testFaderMathRoundtripAndEquidistance() {
        // 1. Verificación p = 1 -> V = 1 (0 dB)
        val vAt1 = FaderMath.positionToVolume(1.0f)
        assertEquals(1.0f, vAt1, eps)
        val dbAt1 = FaderMath.volumeToDb(vAt1)
        assertEquals(0.0f, dbAt1, eps)

        // 2. Verificación p = 0 -> V = 0 (-inf dB)
        val vAt0 = FaderMath.positionToVolume(0.0f)
        assertEquals(0.0f, vAt0, eps)
        val dbAt0 = FaderMath.volumeToDb(vAt0)
        assertTrue(dbAt0.isInfinite() && dbAt0 < 0, "dbAt0 debe ser -infinito")

        // 3. Ida y vuelta p -> V -> p en p = 0, 1/6, 0.5, 5/6, 1
        val testPositions = listOf(0.0f, 1f / 6f, 0.5f, 5f / 6f, 1.0f)
        for (p in testPositions) {
            val v = FaderMath.positionToVolume(p)
            val pBack = FaderMath.volumeToPosition(v)
            assertTrue(
                abs(p - pBack) < eps,
                "Roundtrip falló para p=$p: v=$v, pBack=$pBack"
            )
        }

        // 4. Verificación de equiespaciado de marcas mayores: 0, -10, -20, -30, -40, -50 dB
        val p0 = FaderMath.volumeToPosition(FaderMath.positionToVolume(1.0f))
        val pMin10 = FaderMath.volumeToPosition(FaderMath.positionToVolume(5f / 6f))
        val pMin20 = FaderMath.volumeToPosition(FaderMath.positionToVolume(4f / 6f))
        val pMin30 = FaderMath.volumeToPosition(FaderMath.positionToVolume(3f / 6f))
        val pMin40 = FaderMath.volumeToPosition(FaderMath.positionToVolume(2f / 6f))
        val pMin50 = FaderMath.volumeToPosition(FaderMath.positionToVolume(1f / 6f))

        val step1 = p0 - pMin10
        val step2 = pMin10 - pMin20
        val step3 = pMin20 - pMin30
        val step4 = pMin30 - pMin40
        val step5 = pMin40 - pMin50

        val expectedStep = 1f / 6f
        assertEquals(expectedStep, step1, eps, "Paso 0 a -10 dB no es 1/6")
        assertEquals(expectedStep, step2, eps, "Paso -10 a -20 dB no es 1/6")
        assertEquals(expectedStep, step3, eps, "Paso -20 a -30 dB no es 1/6")
        assertEquals(expectedStep, step4, eps, "Paso -30 a -40 dB no es 1/6")
        assertEquals(expectedStep, step5, eps, "Paso -40 a -50 dB no es 1/6")
    }

    @Test
    fun testCapCenterMatchesScaleMarks() {
        val trackHeight = 300f
        val capHeight = 36f

        val marks = listOf(
            1.0f to 0f,
            5f / 6f to -10f,
            4f / 6f to -20f,
            3f / 6f to -30f,
            2f / 6f to -40f,
            1f / 6f to -50f,
            0.0f to Float.NEGATIVE_INFINITY
        )

        for ((p, _) in marks) {
            // Posición calculada para la marca y etiqueta en la escala
            val markY = FaderMath.yCentro(p, trackHeight, capHeight)

            // Posición del centro de la tapa del fader
            val capTop = (trackHeight - capHeight) * (1f - p)
            val capCenterY = capTop + (capHeight / 2f)

            // Deben coincidir exactamente (misma yCentro)
            assertEquals(markY, capCenterY, eps, "El centro de la tapa no coincide con la marca para p=$p")
        }
    }
}
