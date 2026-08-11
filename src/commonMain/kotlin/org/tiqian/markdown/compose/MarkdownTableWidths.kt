package org.tiqian.markdown.compose


internal data class MarkdownTableWidthResolution(
    val columnWidths: List<Float>,
    val tableWidth: Float,
)

/**
 * Resolves table columns between their measured minimum and preferred widths.
 * Spare width raises the narrowest natural columns toward the widest one, minimizing
 * column-width variation without growing any column past the widest natural column.
 * Horizontal overflow is introduced only when the sum of the real minimums cannot fit.
 */
internal fun resolveMarkdownTableWidths(
    preferredWidths: List<Float>,
    minimumWidths: List<Float>,
    availableWidth: Float,
): MarkdownTableWidthResolution {
    require(preferredWidths.isNotEmpty())
    require(minimumWidths.size == preferredWidths.size)
    require(availableWidth.isFinite() && availableWidth >= 0f)

    val minimum = minimumWidths.map { width ->
        require(width.isFinite() && width >= 0f)
        width
    }
    val preferred = preferredWidths.mapIndexed { index, width ->
        require(width.isFinite() && width >= 0f)
        width.coerceAtLeast(minimum[index])
    }
    val minimumTotal = minimum.sum()
    val preferredTotal = preferred.sum()
    val equalColumnTotal = preferred.max() * preferred.size
    val tableWidth = availableWidth
        .coerceAtMost(equalColumnTotal)
        .coerceAtLeast(minimumTotal)

    val widths = when {
        tableWidth <= minimumTotal -> minimum
        tableWidth < preferredTotal -> {
            val shrinkableTotal = preferredTotal - minimumTotal
            val retainedFraction = (tableWidth - minimumTotal) / shrinkableTotal
            preferred.mapIndexed { index, width ->
                minimum[index] + (width - minimum[index]) * retainedFraction
            }
        }
        tableWidth > preferredTotal -> equalizeMarkdownTableColumns(preferred, tableWidth)
        else -> preferred
    }.toMutableList()

    // Absorb floating-point residue in the final column so borders end at the table edge.
    widths[widths.lastIndex] += tableWidth - widths.sum()
    return MarkdownTableWidthResolution(columnWidths = widths, tableWidth = tableWidth)
}

private fun equalizeMarkdownTableColumns(
    preferredWidths: List<Float>,
    targetTotal: Float,
): List<Float> {
    val widths = preferredWidths.toMutableList()
    val ascending = preferredWidths.indices.sortedBy(preferredWidths::get)
    var remaining = targetTotal - preferredWidths.sum()
    for (raisedCount in 1 until ascending.size) {
        val currentLevel = widths[ascending[raisedCount - 1]]
        val nextLevel = widths[ascending[raisedCount]]
        val cost = (nextLevel - currentLevel) * raisedCount
        if (remaining >= cost) {
            for (index in 0 until raisedCount) widths[ascending[index]] = nextLevel
            remaining -= cost
        } else {
            val increment = remaining / raisedCount
            for (index in 0 until raisedCount) widths[ascending[index]] += increment
            remaining = 0f
            break
        }
    }
    if (remaining > 0f) {
        val increment = remaining / widths.size
        widths.indices.forEach { index -> widths[index] += increment }
    }
    return widths
}
