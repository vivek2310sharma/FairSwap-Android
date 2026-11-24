package com.fairswap.app.ui.intro

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class MotifPlacement(
    val xPx: Float,
    val yPx: Float,
    val sizePx: Float,
    val type: Int,
    val color: Color,
    val phaseOffset: Float,
)

object MotifLayoutEngine {

    /**
     * Generates collision-free motif placements scatter-arranged around screen edges,
     * protecting the central band (y = 35% to 65% height).
     */
    fun computePlacements(
        widthPx: Float,
        heightPx: Float,
        palette: IntroPalette,
        maxCount: Int = 16,
        random: Random = Random(42)
    ): List<MotifPlacement> {
        if (widthPx <= 0 || heightPx <= 0) return emptyList()

        val placements = mutableListOf<MotifPlacement>()
        val minX = 20f
        val maxX = widthPx - 20f
        val minY = 40f
        val maxY = heightPx - 40f

        val centerTop = heightPx * 0.35f
        val centerBottom = heightPx * 0.65f

        val colors = listOf(palette.art, palette.spark, palette.accent)

        var attempts = 0
        while (placements.size < maxCount && attempts < 200) {
            attempts++
            val sizePx = random.nextFloat() * 20f + 28f // 28 - 48 px size
            val x = random.nextFloat() * (maxX - minX) + minX
            val y = random.nextFloat() * (maxY - minY) + minY

            // Protect central band
            if (y + sizePx >= centerTop && y - sizePx <= centerBottom) {
                continue
            }

            // Check collision with existing motifs
            val collision = placements.any { existing ->
                val dx = existing.xPx - x
                val dy = existing.yPx - y
                val minDist = (existing.sizePx + sizePx) * 0.85f
                (dx * dx + dy * dy) < (minDist * minDist)
            }

            if (!collision) {
                val color = colors[random.nextInt(colors.size)]
                val type = random.nextInt(5)
                val phase = random.nextFloat() * (2f * PI.toFloat())
                placements.add(MotifPlacement(x, y, sizePx, type, color, phase))
            }
        }
        return placements
    }

    /**
     * Renders a motif shape given its type, center, size, and color.
     */
    fun drawMotif(
        drawScope: DrawScope,
        placement: MotifPlacement,
        offsetY: Float = 0f,
    ) {
        val cx = placement.xPx
        val cy = placement.yPx + offsetY
        val r = placement.sizePx * 0.5f
        val color = placement.color

        when (placement.type) {
            0 -> drawLotusPetal(drawScope, cx, cy, r, color)
            1 -> drawMandalaStar(drawScope, cx, cy, r, color)
            2 -> drawDiamondSpark(drawScope, cx, cy, r, color)
            3 -> drawPaisleyCurve(drawScope, cx, cy, r, color)
            else -> drawFleurSwirl(drawScope, cx, cy, r, color)
        }
    }

    private fun drawLotusPetal(scope: DrawScope, cx: Float, cy: Float, r: Float, color: Color) {
        val path = Path().apply {
            moveTo(cx, cy - r)
            cubicTo(cx + r, cy - r * 0.3f, cx + r * 0.8f, cy + r, cx, cy + r)
            cubicTo(cx - r * 0.8f, cy + r, cx - r, cy - r * 0.3f, cx, cy - r)
            close()
        }
        scope.drawPath(path, color = color, style = Stroke(width = 2f))
    }

    private fun drawMandalaStar(scope: DrawScope, cx: Float, cy: Float, r: Float, color: Color) {
        val points = 8
        val path = Path()
        for (i in 0 until points * 2) {
            val radius = if (i % 2 == 0) r else r * 0.4f
            val angle = i * PI / points
            val x = (cx + radius * cos(angle)).toFloat()
            val y = (cy + radius * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        scope.drawPath(path, color = color, style = Stroke(width = 1.8f))
    }

    private fun drawDiamondSpark(scope: DrawScope, cx: Float, cy: Float, r: Float, color: Color) {
        val path = Path().apply {
            moveTo(cx, cy - r)
            lineTo(cx + r * 0.6f, cy)
            lineTo(cx, cy + r)
            lineTo(cx - r * 0.6f, cy)
            close()
        }
        scope.drawPath(path, color = color, style = Stroke(width = 2f))
    }

    private fun drawPaisleyCurve(scope: DrawScope, cx: Float, cy: Float, r: Float, color: Color) {
        val path = Path().apply {
            moveTo(cx, cy - r)
            cubicTo(cx + r * 1.2f, cy - r * 0.5f, cx + r * 0.8f, cy + r * 0.8f, cx, cy + r * 0.5f)
            cubicTo(cx - r * 0.8f, cy + r * 0.2f, cx - r * 0.5f, cy - r * 0.8f, cx, cy - r)
            close()
        }
        scope.drawPath(path, color = color, style = Stroke(width = 1.8f))
    }

    private fun drawFleurSwirl(scope: DrawScope, cx: Float, cy: Float, r: Float, color: Color) {
        scope.drawCircle(center = Offset(cx, cy), radius = r * 0.6f, color = color, style = Stroke(width = 1.5f))
        scope.drawCircle(center = Offset(cx, cy), radius = r * 0.2f, color = color)
    }
}
