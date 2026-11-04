package app.kept.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Flat "Truchet" background: each cell holds two quarter-circle arcs whose orientation is chosen
 * by a fixed hash. The arcs join into soft, flowing curves while staying purely geometric.
 * Drawn at very low alpha so it reads as texture, never as content.
 */
fun Modifier.arcPattern(color: Color = C.Accent.copy(alpha = 0.06f)): Modifier = drawWithCache {
    val cell = 72.dp.toPx()
    val stroke = Stroke(width = 1.5.dp.toPx())
    onDrawBehind {
        val cols = (size.width / cell).toInt() + 1
        val rows = (size.height / cell).toInt() + 1
        for (r in 0 until rows) for (c in 0 until cols) {
            var h = c * 374761393 + r * 668265263
            h = (h xor (h ushr 13)) * 1274126177
            val flip = (h ushr 16) and 1 == 0
            val x = c * cell
            val y = r * cell
            val d = Size(cell, cell)
            // arcs centred on opposite corners of the cell
            if (flip) {
                drawArc(color, 0f, 90f, false, Offset(x - cell / 2, y - cell / 2), d, style = stroke)
                drawArc(color, 180f, 90f, false, Offset(x + cell / 2, y + cell / 2), d, style = stroke)
            } else {
                drawArc(color, 90f, 90f, false, Offset(x + cell / 2, y - cell / 2), d, style = stroke)
                drawArc(color, 270f, 90f, false, Offset(x - cell / 2, y + cell / 2), d, style = stroke)
            }
        }
    }
}
