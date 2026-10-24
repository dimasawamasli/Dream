package com.dream.boost

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

private const val TAU = 6.2831855f
private const val FALLING_STARS = 150
private const val FAR_STARS = 110

// Angka acak deterministik 0..1 (tanpa state, jadi aman dipanggil tiap frame)
private fun rnd(a: Float, b: Float): Float {
    val s = sin(a * 12.9898f + b * 78.233f) * 43758.547f
    return s - floor(s)
}

/**
 * Latar ruang angkasa hitam-ungu dengan blackhole bergerak.
 * [holeY] = posisi vertikal blackhole (0..1 dari tinggi layar), [holeScale] = ukuran.
 */
@Composable
fun SpaceBackground(holeY: Float, holeScale: Float, modifier: Modifier = Modifier) {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { t = (it - start) / 1_000_000_000f }
        }
    }
    val hy by animateFloatAsState(holeY, tween(900), label = "holeY")
    val hs by animateFloatAsState(holeScale, tween(900), label = "holeScale")

    Canvas(modifier.fillMaxSize()) {
        drawSpace(t, hy, hs)
    }
}

private fun DrawScope.drawSpace(t: Float, holeY: Float, holeScale: Float) {
    val w = size.width
    val h = size.height
    val c = Offset(w / 2f, h * holeY)
    val r = size.minDimension * 0.10f * holeScale

    // 1) dasar: hitam -> ungu -> hitam
    drawRect(
        brush = Brush.verticalGradient(
            listOf(
                Color(0xFF000000),
                Color(0xFF12002A),
                Color(0xFF2B0B52),
                Color(0xFF0B0016),
                Color(0xFF000000)
            )
        )
    )

    // 2) kabut ungu yang pelan bergeser
    fun blob(cx: Float, cy: Float, rad: Float, col: Color) {
        drawCircle(
            brush = Brush.radialGradient(listOf(col, Color.Transparent), center = Offset(cx, cy), radius = rad),
            radius = rad,
            center = Offset(cx, cy)
        )
    }
    blob(w * 0.12f + sin(t * 0.20f) * w * 0.04f, h * 0.76f, w * 0.95f, Color(0x668B2BE2))
    blob(w * 0.92f, h * 0.52f + sin(t * 0.17f) * h * 0.02f, w * 0.75f, Color(0x44C026D3))
    blob(w * 0.80f + sin(t * 0.13f) * w * 0.03f, h * 0.06f, w * 0.70f, Color(0x444F2BD6))

    // 3) bintang jauh (diam, berkedip)
    for (i in 0 until FAR_STARS) {
        val fi = i.toFloat()
        val x = rnd(fi, 1.7f) * w
        val y = rnd(fi, 9.1f) * h
        val tw = (0.5f + 0.5f * sin(t * (0.8f + rnd(fi, 3.3f) * 1.8f) + fi)).coerceIn(0f, 1f)
        val rad = (0.5f + rnd(fi, 5.5f) * 1.1f) * density
        drawCircle(Color.White.copy(alpha = 0.18f + 0.6f * tw), rad, Offset(x, y))
    }

    // 4) bintang yang tersedot dari pinggir layar
    val rMax = hypot(max(c.x, w - c.x), max(c.y, h - c.y)) * 1.1f
    val r0 = r * 0.9f
    for (i in 0 until FALLING_STARS) {
        val fi = i.toFloat()
        val speed = 0.040f + 0.055f * rnd(fi, 2.2f)
        val ph = t * speed + rnd(fi, 4.4f)
        val cyc = floor(ph)
        val p = ph - cyc
        val a0 = rnd(fi, cyc + 6.1f) * TAU

        fun at(q: Float): Offset {
            val rad = r0 + (rMax - r0) * (1f - q).pow(0.55f) // makin dekat makin cepat
            val ang = a0 + 1.1f * q.pow(2.2f)                // makin dekat makin berputar
            return Offset(c.x + cos(ang) * rad, c.y + sin(ang) * rad)
        }

        val head = at(p)
        val tail = at((p - 0.045f).coerceAtLeast(0f))
        val fadeIn = (p * 14f).coerceIn(0f, 1f)
        val fadeOut = (1f - (p - 0.88f) / 0.12f).coerceIn(0f, 1f)
        val a = fadeIn * fadeOut
        val near = p.pow(2.5f)
        val col = lerp(Color(0xFFFFFFFF), Color(0xFFB45CFF), near)
        val sw = (1.1f + 1.6f * rnd(fi, 7.7f)) * density * (1f - 0.45f * near)
        drawLine(col.copy(alpha = a * 0.5f), tail, head, sw, StrokeCap.Round)
        drawCircle(col.copy(alpha = a), sw * 0.75f, head)
    }

    // 5) blackhole
    val pulse = 1f + 0.05f * sin(t * 1.7f)
    val glowR = r * 6f * pulse
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xAAB026FF), Color(0x552A0A5A), Color.Transparent),
            center = c,
            radius = glowR
        ),
        radius = glowR,
        center = c
    )

    val diskR = r * 2.35f
    val diskW = r * 0.95f
    val sweep = Brush.sweepGradient(
        0.00f to Color(0x00B24BFF),
        0.16f to Color(0xFFB24BFF),
        0.30f to Color(0xFFFFFFFF),
        0.46f to Color(0xCCC04BFF),
        0.70f to Color(0x554B1A8F),
        0.92f to Color(0x00B24BFF),
        1.00f to Color(0x00B24BFF),
        center = c
    )

    fun disk(alpha: Float, widthMul: Float) {
        withTransform({
            rotate(-14f, c)
            scale(1f, 0.27f, c)
            rotate(t * 38f, c)
        }) {
            drawCircle(
                brush = sweep,
                radius = diskR,
                center = c,
                alpha = alpha,
                style = Stroke(width = diskW * widthMul)
            )
        }
    }

    disk(0.35f, 1.7f) // pendar
    disk(1f, 1f)      // piringan (bagian belakang)

    // cahaya piringan yang melengkung di atas lubang
    drawArc(
        brush = Brush.verticalGradient(
            listOf(Color(0xFFFFE8FF), Color(0xFFB24BFF), Color(0x00B24BFF)),
            startY = c.y - r * 1.5f,
            endY = c.y + r * 0.2f
        ),
        startAngle = 188f,
        sweepAngle = 164f,
        useCenter = false,
        topLeft = Offset(c.x - r * 1.14f, c.y - r * 1.14f),
        size = Size(r * 2.28f, r * 2.28f),
        style = Stroke(width = r * 0.14f, cap = StrokeCap.Round)
    )

    // event horizon
    drawCircle(Color.Black, r, c)
    drawCircle(Color(0xFFD8B4FE), r, c, style = Stroke(width = r * 0.045f))

    // bagian depan piringan melintas di depan lubang
    clipRect(top = c.y) { disk(1f, 1f) }
}
