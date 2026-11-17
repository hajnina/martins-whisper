package com.martins.whisper.overlay

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.PathParser

/**
 * The bubble glyph. Swapping the icon = editing [BUBBLE_GLYPH] only (24x24 viewBox, SVG path data,
 * evenOdd so the capsule is a hole); everything else scales and centres it.
 */
object ExpressiveIcon {

    const val BUBBLE_GLYPH =
        "M12 2a5 5 0 0 1 5 5v10.4a1.2 1.2 0 0 1-2.4 0V10a.7.7 0 0 0-1.4 0v10.8a1.2 1.2 0 0 1-2.4 0V10a.7.7 0 0 0-1.4 0v7.4a1.2 1.2 0 0 1-2.4 0V7a5 5 0 0 1 5-5Z " +
            "M10.35 4.8h3.3a.75.75 0 0 1 0 1.5h-3.3a.75.75 0 0 1 0-1.5Z"

    /** Glyph tile as a fraction of the bubble's edge. */
    const val TILE_FRACTION = 0.55f

    private val source: Path by lazy {
        PathParser.createPathFromPathData(BUBBLE_GLYPH).apply { fillType = Path.FillType.EVEN_ODD }
    }

    /** Glyph scaled to [tileFraction] of [size] and centred on ([centerX], [centerY]). */
    fun glyph(centerX: Float, centerY: Float, size: Float, tileFraction: Float = TILE_FRACTION): Path {
        val path = Path(source)
        val bounds = RectF()
        path.computeBounds(bounds, true)
        val span = maxOf(bounds.width(), bounds.height())
        if (span <= 0f || size <= 0f) return path
        val scale = size * tileFraction / span
        val matrix = Matrix()
        matrix.setScale(scale, scale)
        matrix.postTranslate(centerX - bounds.centerX() * scale, centerY - bounds.centerY() * scale)
        path.transform(matrix)
        return path
    }
}
