package org.tiqian.markdown.compose

import kotlin.math.ceil
import kotlin.math.floor

internal data class MarkdownTableWidthResolution(
    val columnWidths: List<Float>,
    val tableWidth: Float,
)

/**
 * Resolves table columns between their measured minimum and preferred widths.
 *
 * `TableColumnEmQuantization`: when [emPx] is positive, every column's CONTENT width (the width
 * minus [horizontalPaddingPx]) lands on an integer multiple of the em grid, so cell prose keeps
 * 行长为字号整数倍 without per-line slack; the fixed cell padding absorbs nothing — remainders are
 * redistributed in whole-em steps by largest remainder.
 *
 * `TableFluidFill`: when the natural table is narrower than [fillTargetWidth] (the prose fluid
 * tier, not the raw available width), all columns stretch PROPORTIONALLY to fill it — the
 * content's wide/narrow signal is preserved instead of pumping narrow columns toward the widest
 * (the old equalising behaviour, judged unnatural). A table already wider than the target but
 * fitting the available width keeps its natural width.
 *
 * `TableShrinkWaterline`: when preferred widths exceed the available width, only columns above a
 * common waterline are compressed, each stopping at its own minimum; narrow columns keep their
 * natural width. Horizontal overflow is introduced only when the sum of minimums cannot fit.
 */
internal fun resolveMarkdownTableWidths(
    preferredWidths: List<Float>,
    minimumWidths: List<Float>,
    availableWidth: Float,
    emPx: Float = 0f,
    fillTargetWidth: Float = availableWidth,
    horizontalPaddingPx: Float = 0f,
): MarkdownTableWidthResolution {
    require(preferredWidths.isNotEmpty())
    require(minimumWidths.size == preferredWidths.size)
    require(availableWidth.isFinite() && availableWidth >= 0f)

    val pad = horizontalPaddingPx.coerceAtLeast(0f)
    fun quantizeUpContent(width: Float): Float {
        val content = (width - pad).coerceAtLeast(0f)
        if (emPx <= 0f) return content
        return ceil(content / emPx - CONTENT_QUANTIZATION_EPSILON) * emPx
    }

    val minimumContent = minimumWidths.map { width ->
        require(width.isFinite() && width >= 0f)
        quantizeUpContent(width)
    }
    val preferredContent = preferredWidths.mapIndexed { index, width ->
        require(width.isFinite() && width >= 0f)
        quantizeUpContent(width).coerceAtLeast(minimumContent[index])
    }
    val paddingTotal = pad * preferredWidths.size
    val minimumTotal = minimumContent.sum() + paddingTotal
    val preferredTotal = preferredContent.sum() + paddingTotal

    val contentWidths = when {
        // Sum of minimums cannot fit: keep minimums and let the table scroll horizontally.
        minimumTotal > availableWidth -> minimumContent
        // Too wide: compress only the columns above the waterline, floor at each minimum.
        preferredTotal > availableWidth -> shrinkToWaterline(
            preferredContent = preferredContent,
            minimumContent = minimumContent,
            targetContentTotal = availableWidth - paddingTotal,
            emPx = emPx,
        )
        // Narrower than the fluid tier: stretch proportionally to fill it.
        preferredTotal < minOf(fillTargetWidth, availableWidth) -> stretchProportionally(
            preferredContent = preferredContent,
            targetContentTotal = minOf(fillTargetWidth, availableWidth) - paddingTotal,
            emPx = emPx,
        )
        // Between the fill target and the available width: natural width.
        else -> preferredContent
    }
    val widths = contentWidths.map { it + pad }
    return MarkdownTableWidthResolution(columnWidths = widths, tableWidth = widths.sum())
}

/** Lower the widest columns to a shared waterline until the target total fits. */
private fun shrinkToWaterline(
    preferredContent: List<Float>,
    minimumContent: List<Float>,
    targetContentTotal: Float,
    emPx: Float,
): List<Float> {
    // Continuous waterline by descending sweep over distinct preferred levels.
    // The waterline may sit below any single column's minimum — per-column clamping already
    // enforces each floor — so the search's lower bound is the smallest floor, not the largest.
    var level = preferredContent.max()
    var lower = minimumContent.min().coerceAtMost(level)
    repeat(WATERLINE_BISECTION_STEPS) {
        val candidate = (level + lower) / 2f
        val total = preferredContent.indices.sumOf { index ->
            preferredContent[index]
                .coerceAtMost(candidate)
                .coerceAtLeast(minimumContent[index])
                .toDouble()
        }
        if (total > targetContentTotal) level = candidate else lower = candidate
    }
    val continuous = preferredContent.indices.map { index ->
        preferredContent[index].coerceAtMost(lower).coerceAtLeast(minimumContent[index])
    }
    if (emPx <= 0f) return continuous
    return distributeOnGrid(
        continuous = continuous,
        floors = minimumContent,
        ceilings = preferredContent,
        targetTotal = targetContentTotal,
        emPx = emPx,
    )
}

/** Scale all columns by the same factor so their total reaches the target. */
private fun stretchProportionally(
    preferredContent: List<Float>,
    targetContentTotal: Float,
    emPx: Float,
): List<Float> {
    val naturalTotal = preferredContent.sum()
    if (naturalTotal <= 0f) return preferredContent
    val scale = (targetContentTotal / naturalTotal).coerceAtLeast(1f)
    val continuous = preferredContent.map { it * scale }
    if (emPx <= 0f) return continuous
    return distributeOnGrid(
        continuous = continuous,
        floors = preferredContent,
        ceilings = List(preferredContent.size) { Float.MAX_VALUE },
        targetTotal = targetContentTotal,
        emPx = emPx,
    )
}

/**
 * Floor each continuous width to the em grid, then hand out whole-em increments by largest
 * remainder while the total stays within the target and each column within its ceiling.
 */
private fun distributeOnGrid(
    continuous: List<Float>,
    floors: List<Float>,
    ceilings: List<Float>,
    targetTotal: Float,
    emPx: Float,
): List<Float> {
    val widths = continuous.mapIndexed { index, width ->
        (floor(width / emPx + CONTENT_QUANTIZATION_EPSILON) * emPx).coerceAtLeast(floors[index])
    }.toMutableList()
    val byRemainder = continuous.indices.sortedByDescending { continuous[it] - widths[it] }
    var total = widths.sum()
    for (index in byRemainder) {
        if (total + emPx > targetTotal + CONTENT_QUANTIZATION_EPSILON) break
        if (widths[index] + emPx > ceilings[index]) continue
        widths[index] += emPx
        total += emPx
    }
    return widths
}

private const val WATERLINE_BISECTION_STEPS = 24
private const val CONTENT_QUANTIZATION_EPSILON = 1e-3f
