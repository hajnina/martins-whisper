package com.martins.whisper.overlay

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

    fun expandedOrCollapsedWidth(expanded: Boolean, bubble: Int, pill: Int): Int =
        if (expanded) pill else bubble

    fun expandedOrCollapsedHeight(expanded: Boolean, bubble: Int, pill: Int): Int =
        if (expanded) pill else bubble
}
