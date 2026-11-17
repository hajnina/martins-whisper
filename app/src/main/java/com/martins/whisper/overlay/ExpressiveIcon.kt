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
        "M16.2 2.6C14.2 6.6 6.3 9.5 4.7 14.1 3.2 18.4 6.4 21.6 10.8 21.6 15.4 21.6 19.2 18.5 " +
            "19.5 14 19.8 9.5 17.7 5.6 16.2 2.6ZM10.4 12.2A1.6 1.6 0 0 1 12 10.6 1.6 1.6 0 0 1 " +
            "13.6 12.2V15.2A1.6 1.6 0 0 1 12 16.8 1.6 1.6 0 0 1 10.4 15.2Z"

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
