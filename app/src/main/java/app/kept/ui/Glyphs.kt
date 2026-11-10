package app.kept.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class Glyph { Plus, Check, Close, Back, Trash, Copy, Restore, Sliders, Calendar, ChevLeft, ChevRight, Search }

/** Hand-drawn 24-unit line icons: one stroke weight, square caps - matches the flat geometric language. */
@Composable
fun GlyphIcon(glyph: Glyph, tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val k = this.size.width / 24f
        val stroke = Stroke(width = 2f * k, cap = StrokeCap.Square, join = StrokeJoin.Miter)
        fun p(x: Float, y: Float) = Offset(x * k, y * k)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(tint, p(x1, y1), p(x2, y2), 2f * k, StrokeCap.Square)
        when (glyph) {
            Glyph.Plus -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
            Glyph.Check -> {
                val path = Path().apply { moveTo(5f * k, 12.5f * k); lineTo(10f * k, 17.5f * k); lineTo(19f * k, 7f * k) }
                drawPath(path, tint, style = stroke)
            }
            Glyph.Close -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
            Glyph.Back -> {
                line(19f, 12f, 5f, 12f)
                val path = Path().apply { moveTo(11f * k, 6f * k); lineTo(5f * k, 12f * k); lineTo(11f * k, 18f * k) }
                drawPath(path, tint, style = stroke)
            }
            Glyph.Trash -> {
                line(5f, 7f, 19f, 7f)
                drawRect(tint, p(7f, 7f), Size(10f * k, 13f * k), style = stroke)
                line(10f, 3.5f, 14f, 3.5f)
            }
            Glyph.Copy -> {
                drawRect(tint, p(8f, 8f), Size(11f * k, 11f * k), style = stroke)
                val path = Path().apply { moveTo(5f * k, 15f * k); lineTo(5f * k, 5f * k); lineTo(15f * k, 5f * k) }
                drawPath(path, tint, style = stroke)
            }
            Glyph.Restore -> {
                val path = Path().apply { moveTo(9f * k, 5f * k); lineTo(4f * k, 9.5f * k); lineTo(9f * k, 14f * k) }
                drawPath(path, tint, style = stroke)
                val arm = Path().apply { moveTo(4f * k, 9.5f * k); lineTo(14f * k, 9.5f * k); lineTo(19f * k, 13f * k); lineTo(19f * k, 19f * k); lineTo(10f * k, 19f * k) }
                drawPath(arm, tint, style = stroke)
            }
            Glyph.Calendar -> {
                drawRoundRect(tint, p(4f, 6f), Size(16f * k, 14f * k), androidx.compose.ui.geometry.CornerRadius(3f * k), style = stroke)
                line(4f, 11f, 20f, 11f); line(9f, 3.5f, 9f, 8f); line(15f, 3.5f, 15f, 8f)
                dot(tint, p(9f, 15f), 1.2f * k); dot(tint, p(15f, 15f), 1.2f * k)
            }
            Glyph.Search -> { drawCircle(tint, 6.5f * k, p(10.5f, 10.5f), style = stroke); line(15.5f, 15.5f, 20f, 20f) }
            Glyph.ChevLeft -> drawPath(Path().apply { moveTo(14f * k, 6f * k); lineTo(8f * k, 12f * k); lineTo(14f * k, 18f * k) }, tint, style = stroke)
            Glyph.ChevRight -> drawPath(Path().apply { moveTo(10f * k, 6f * k); lineTo(16f * k, 12f * k); lineTo(10f * k, 18f * k) }, tint, style = stroke)
            Glyph.Sliders -> {
                line(4f, 8f, 20f, 8f); line(4f, 16f, 20f, 16f)
                dot(tint, p(9f, 8f), 3f * k); dot(tint, p(15f, 16f), 3f * k)
            }
        }
    }
}

private fun DrawScope.dot(c: Color, center: Offset, r: Float) {
    drawRect(c, Offset(center.x - r, center.y - r), Size(2 * r, 2 * r))
}
