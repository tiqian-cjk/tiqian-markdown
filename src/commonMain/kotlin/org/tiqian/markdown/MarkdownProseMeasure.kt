package org.tiqian.markdown

import kotlin.math.floor
import kotlin.math.log2

/**
 * Resolves the prose measure in CJK cells. From 32ic onward, each doubling of
 * available width admits another 8ic, until the explicit 48ic hard limit.
 */
internal fun resolveMarkdownProseMeasureCells(
    availableCells: Float,
    policy: MarkdownProseMeasure,
): Float {
    require(availableCells.isFinite() && availableCells >= 0f)
    if (availableCells < 1f) return availableCells

    val target = if (availableCells <= policy.fluidStart.count) {
        availableCells
    } else {
        (
            policy.fluidStart.count +
                policy.growthPerDoubling.count * log2(availableCells / policy.fluidStart.count)
            ).coerceAtMost(policy.maximum.count)
    }
    return floor(target).coerceAtLeast(1f)
}
