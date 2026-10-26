package com.martins.whisper.core.text

/**
 * Minimal extended-grapheme-cluster boundary detection for insertion. It never splits a UTF-16
 * surrogate pair, a combining-mark sequence, a variation selector, an emoji modifier/ZWJ sequence
 * or a regional-indicator flag. It is intentionally local (O(cluster length)) so huge fields stay
 * cheap to edit.
 */
object Graphemes {

    private const val ZWJ = 0x200D
    private const val ZWNJ = 0x200C

    fun isBoundary(text: String, index: Int): Boolean {
        if (index <= 0 || index >= text.length) return true
        if (text[index - 1].isHighSurrogate() && text[index].isLowSurrogate()) return false
        if (isExtend(text.codePointAt(index))) return false
        val before = text.codePointBefore(index)
        if (before == ZWJ || before == ZWNJ) return false
        if (isRegionalIndicator(before) && isRegionalIndicator(text.codePointAt(index))) {
            var count = 0
            var cursor = index
            while (cursor >= 2 && isRegionalIndicator(text.codePointBefore(cursor))) {
                count++
                cursor -= 2
            }
            return count % 2 == 0
        }
        return true
    }

    /** First boundary at or before [index]. */
    fun clusterStart(text: String, index: Int): Int {
        var cursor = index.coerceIn(0, text.length)
        while (cursor > 0 && !isBoundary(text, cursor)) cursor--
        return cursor
    }

    /** First boundary at or after [index]. */
    fun clusterEnd(text: String, index: Int): Int {
        var cursor = index.coerceIn(0, text.length)
        while (cursor < text.length && !isBoundary(text, cursor)) cursor++
        return cursor
    }

    private fun isRegionalIndicator(codePoint: Int): Boolean =
        codePoint in 0x1F1E6..0x1F1FF

    private fun isExtend(codePoint: Int): Boolean {
        if (codePoint == ZWJ || codePoint == ZWNJ) return true
        if (codePoint in 0xFE00..0xFE0F || codePoint in 0xE0100..0xE01EF) return true
        if (codePoint in 0x1F3FB..0x1F3FF) return true
        if (codePoint in 0xE0020..0xE007F) return true
        return when (Character.getType(codePoint)) {
            Character.NON_SPACING_MARK.toInt(),
            Character.ENCLOSING_MARK.toInt(),
            Character.COMBINING_SPACING_MARK.toInt(),
            -> true
            else -> false
        }
    }
}
