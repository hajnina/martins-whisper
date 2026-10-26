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

    fun defaultBottomEnd(
        width: Int,
        height: Int,
        screenWidth: Int,
        screenHeight: Int,
        margin: Int,
        bottomInset: Int,
    ): Pair<Int, Int> {
        val x = (screenWidth - width - margin).coerceAtLeast(margin)
        val y = (screenHeight - height - margin - bottomInset).coerceAtLeast(margin)
        return x to y
    }

    fun expandedOrCollapsedWidth(expanded: Boolean, bubble: Int, pill: Int): Int =
        if (expanded) pill else bubble

    fun expandedOrCollapsedHeight(expanded: Boolean, bubble: Int, pill: Int): Int =
        if (expanded) pill else bubble
}
