package com.martins.whisper.overlay

import kotlin.math.roundToInt

object OverlayGeometry {

    fun clamp(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        screenWidth: Int,
        screenHeight: Int,
        margin: Int,
    ): Pair<Int, Int> {
        val maxX = (screenWidth - width - margin).coerceAtLeast(margin)
        val maxY = (screenHeight - height - margin).coerceAtLeast(margin)
        return x.coerceIn(margin, maxX) to y.coerceIn(margin, maxY)
    }

    /**
     * Default spot on the right edge, vertically centred. The bottom edge is deliberately avoided:
     * on the real phone the bubble covered Gboard's Enter key and Messages' mic/send button.
     */
    fun defaultSide(
        width: Int,
        height: Int,
        screenWidth: Int,
        screenHeight: Int,
        margin: Int,
    ): Pair<Int, Int> {
        val x = (screenWidth - width - margin).coerceAtLeast(margin)
        val maxY = (screenHeight - height - margin).coerceAtLeast(margin)
        val y = ((screenHeight - height) / 2).coerceIn(margin, maxY)
        return x to y
    }

    /**
     * Window position for a drag: the window moves by the finger's travel since ACTION_DOWN while
     * the initial touch offset stays constant. Deliberately ignores view-local coordinates, which
     * change as the window moves and made the bubble drift under the finger.
     */
    fun draggedPosition(
        startX: Int,
        startY: Int,
        dxFromDown: Float,
        dyFromDown: Float,
        width: Int,
        height: Int,
        screenWidth: Int,
        screenHeight: Int,
        margin: Int,
    ): Pair<Int, Int> = clamp(
        startX + dxFromDown.roundToInt(),
        startY + dyFromDown.roundToInt(),
        width,
        height,
        screenWidth,
        screenHeight,
        margin,
    )

    /** On release the bubble parks against the nearest horizontal edge. */
    fun snapToEdge(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        screenWidth: Int,
        screenHeight: Int,
        margin: Int,
    ): Pair<Int, Int> {
        val left = margin
        val right = (screenWidth - width - margin).coerceAtLeast(left)
        val snappedX = if (x - left <= right - x) left else right
        val maxY = (screenHeight - height - margin).coerceAtLeast(margin)
        return snappedX to y.coerceIn(margin, maxY)
    }

    fun expandedOrCollapsedWidth(expanded: Boolean, bubble: Int, pill: Int): Int =
        if (expanded) pill else bubble

    fun expandedOrCollapsedHeight(expanded: Boolean, bubble: Int, pill: Int): Int =
        if (expanded) pill else bubble
}
