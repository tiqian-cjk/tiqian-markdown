package org.tiqian.markdown.compose

import org.tiqian.markdown.MarkdownNodeKey

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.DragScope
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.pager.PagerState
import kotlin.math.abs

internal class MarkdownImagePagerGesture(initialPage: Int) {
    var anchorPage: Int = initialPage
    var dragDistancePx: Float = 0f
}

internal class MarkdownImagePagerDragState(
    private val state: PagerState,
    private val onDragDelta: (Float) -> Unit,
) : DraggableState {
    override suspend fun drag(
        dragPriority: MutatePriority,
        block: suspend DragScope.() -> Unit,
    ) {
        state.scroll(dragPriority) {
            val scrollScope = this
            block(object : DragScope {
                override fun dragBy(pixels: Float) {
                    onDragDelta(pixels)
                    scrollScope.scrollBy(-pixels)
                }
            })
        }
    }

    override fun dispatchRawDelta(delta: Float) {
        onDragDelta(delta)
        state.dispatchRawDelta(-delta)
    }
}

internal fun resolveMarkdownImagePagerTarget(
    anchorPage: Int,
    dragDistancePx: Float,
    pointerVelocityPxPerSecond: Float,
    minimumFlingVelocityPxPerSecond: Float,
    pageSizePx: Int,
    pageCount: Int,
): Int {
    if (pageCount <= 0) return 0
    val direction = when {
        abs(pointerVelocityPxPerSecond) >= minimumFlingVelocityPxPerSecond ->
            if (pointerVelocityPxPerSecond < 0f) 1 else -1
        pageSizePx > 0 && abs(dragDistancePx) >= pageSizePx * 0.5f ->
            if (dragDistancePx < 0f) 1 else -1
        else -> 0
    }
    return (anchorPage + direction).coerceIn(0, pageCount - 1)
}

private fun PagerState.distanceToStart(page: Int): Float {
    val layout = layoutInfo
    layout.visiblePagesInfo.firstOrNull { it.index == page }?.let { return it.offset.toFloat() }
    val pageStep = layout.pageSize + layout.pageSpacing
    val currentOffset = layout.visiblePagesInfo
        .firstOrNull { it.index == currentPage }
        ?.offset
        ?: 0
    return currentOffset + (page - currentPage) * pageStep.toFloat()
}

internal suspend fun PagerState.settleToPageWithoutOvershoot(
    page: Int,
    initialScrollVelocity: Float,
) {
    scroll(MutatePriority.Default) {
        val distance = distanceToStart(page)
        var consumedDistance = 0f
        AnimationState(initialValue = 0f, initialVelocity = initialScrollVelocity).animateTo(
            targetValue = distance,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = 1f,
            ),
        ) {
            val boundedValue = value.coerceToTarget(distance)
            val delta = boundedValue - consumedDistance
            val consumed = scrollBy(delta)
            consumedDistance += consumed
            if (boundedValue != value || abs(delta - consumed) > 0.5f) {
                cancelAnimation()
            }
        }
        val remainder = distanceToStart(page)
        if (abs(remainder) > 0.5f) scrollBy(remainder)
    }
}

private fun Float.coerceToTarget(target: Float): Float = when {
    target > 0f -> coerceAtMost(target)
    target < 0f -> coerceAtLeast(target)
    else -> 0f
}

/** Pager persists item keys through Android Bundle, so expose the structural key as a String. */
internal fun MarkdownNodeKey.imageViewerSaveableKey(): String = buildString {
    append(parserStableKey)
    append(':')
    path.joinTo(this, separator = ".")
}
