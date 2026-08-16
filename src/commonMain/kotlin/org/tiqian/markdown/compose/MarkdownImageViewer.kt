package org.tiqian.markdown.compose

import org.tiqian.markdown.MarkdownImageBlock
import org.tiqian.markdown.MarkdownNodeKey

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.tiqian.markdown.compose.generated.resources.Res
import org.tiqian.markdown.compose.generated.resources.close_image_viewer
import org.tiqian.markdown.compose.generated.resources.ic_arrow_back_24dp
import org.tiqian.markdown.compose.generated.resources.ic_chevron_left_24dp
import org.tiqian.markdown.compose.generated.resources.ic_chevron_right_24dp
import org.tiqian.markdown.compose.generated.resources.next_image
import org.tiqian.markdown.compose.generated.resources.previous_image

/** The same 16-step smoothstep scrim used by Fog Island's image viewer. */
private fun smoothMarkdownImageViewerScrimBrush(
    color: Color,
    flatFraction: Float = 0f,
): Brush {
    require(flatFraction in 0f..1f) { "flatFraction must be in [0, 1]" }
    val steps = 16
    val maxAlpha = color.alpha
    val colorStops = buildList {
        if (flatFraction > 0f) add(0f to color)
        for (index in 0..steps) {
            val progress = index.toFloat() / steps
            val position = flatFraction + progress * (1f - flatFraction)
            val alpha = maxAlpha *
                (1f - progress) *
                (1f - progress) *
                (1f + 2f * progress)
            add(position to color.copy(alpha = alpha))
        }
    }.toTypedArray()
    return Brush.verticalGradient(colorStops = colorStops)
}

internal fun markdownImageViewerScrimFlatFraction(
    scrimHeightPx: Float,
    systemBarHeightPx: Float,
    topBarHeightPx: Float,
): Float {
    if (scrimHeightPx <= 0f) return 0.1f
    val clampedSystemBarHeight = systemBarHeightPx.coerceIn(0f, scrimHeightPx)
    val clampedTopBarHeight = topBarHeightPx.coerceIn(
        minimumValue = 0f,
        maximumValue = scrimHeightPx - clampedSystemBarHeight,
    )
    return ((clampedSystemBarHeight + clampedTopBarHeight * 0.1f) / scrimHeightPx)
        .coerceIn(0f, 1f)
}

/**
 * Displays an image gallery starting at [image].
 *
 * Once composed, page movement is owned by the pager and reported through [onImageChange]. Treating
 * those reports as commands to resynchronize the pager introduces a feedback race during consecutive
 * swipes, so changing [image] does not move an already-open viewer.
 */
@Composable
fun MarkdownImageViewer(
    image: MarkdownImageBlock,
    imageProvider: MarkdownImageProvider,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.(MarkdownImageBlock) -> Unit = {},
    images: List<MarkdownImageBlock> = listOf(image),
    onImageChange: (MarkdownImageBlock) -> Unit = {},
) {
    val gallery = remember(images, image) {
        images
            .distinctBy { it.metadata.key }
            .let { items -> if (items.any { it.metadata.key == image.metadata.key }) items else items + image }
    }
    val initialPage = gallery.indexOfFirst { it.metadata.key == image.metadata.key }
        .coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage) { gallery.size }
    val minimumFlingVelocity = LocalViewConfiguration.current.minimumFlingVelocity
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val pageAtDefaultWidth = remember(gallery) { mutableStateMapOf<MarkdownNodeKey, Boolean>() }
    var intendedPage by remember(gallery) { mutableIntStateOf(initialPage) }
    val pagerGesture = remember(gallery) { MarkdownImagePagerGesture(initialPage) }
    val pagerDragState = remember(pagerState, pagerGesture) {
        MarkdownImagePagerDragState(pagerState) { delta -> pagerGesture.dragDistancePx += delta }
    }
    var controlsVisible by remember { mutableStateOf(true) }
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    val presentedPage = intendedPage.coerceIn(gallery.indices)
    val currentImage = gallery[presentedPage]
    val canGoPrevious = presentedPage > 0
    val canGoNext = presentedPage < gallery.lastIndex

    MarkdownImageViewerSystemBarsEffect(visible = controlsVisible)

    fun settleToPage(page: Int, pointerVelocity: Float = 0f) {
        if (page !in gallery.indices) return
        intendedPage = page
        onImageChange(gallery[page])
        scope.launch {
            pagerState.settleToPageWithoutOvershoot(
                page = page,
                initialScrollVelocity = -pointerVelocity,
            )
        }
    }

    Box(modifier.background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .draggable(
                    state = pagerDragState,
                    orientation = Orientation.Horizontal,
                    enabled = gallery.size > 1 &&
                        pageAtDefaultWidth[gallery[presentedPage].metadata.key] != false,
                    startDragImmediately = pagerState.isScrollInProgress,
                    onDragStarted = {
                        pagerGesture.anchorPage = intendedPage
                        pagerGesture.dragDistancePx = 0f
                    },
                    onDragStopped = { velocity ->
                        val target = resolveMarkdownImagePagerTarget(
                            anchorPage = pagerGesture.anchorPage,
                            dragDistancePx = pagerGesture.dragDistancePx,
                            pointerVelocityPxPerSecond = velocity,
                            minimumFlingVelocityPxPerSecond = minimumFlingVelocity,
                            pageSizePx = pagerState.layoutInfo.pageSize,
                            pageCount = gallery.size,
                        )
                        settleToPage(target, velocity)
                    },
                ),
            userScrollEnabled = false,
            key = { page -> gallery[page].metadata.key.imageViewerSaveableKey() },
            overscrollEffect = null,
        ) { page ->
            val pageImage = gallery[page]
            MarkdownImageViewerPage(
                image = pageImage,
                imageProvider = imageProvider,
                onDismiss = onDismiss,
                active = page == presentedPage,
                allowHorizontalPageSwipe = gallery.size > 1,
                canGoPrevious = page > 0,
                canGoNext = page < gallery.lastIndex,
                onPrevious = { settleToPage(page - 1) },
                onNext = { settleToPage(page + 1) },
                onToggleControls = { controlsVisible = !controlsVisible },
                onDefaultWidthChanged = { atDefaultWidth ->
                    pageAtDefaultWidth[pageImage.metadata.key] = atDefaultWidth
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (markdownImageViewerHasDesktopNavigation && gallery.size > 1) {
            AnimatedVisibility(
                visible = controlsVisible,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp),
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(180)),
            ) {
                MarkdownImageNavigationButton(
                    enabled = canGoPrevious,
                    onClick = { settleToPage(presentedPage - 1) },
                    icon = Res.drawable.ic_chevron_left_24dp,
                    contentDescription = stringResource(Res.string.previous_image),
                )
            }
            AnimatedVisibility(
                visible = controlsVisible,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(180)),
            ) {
                MarkdownImageNavigationButton(
                    enabled = canGoNext,
                    onClick = { settleToPage(presentedPage + 1) },
                    icon = Res.drawable.ic_chevron_right_24dp,
                    contentDescription = stringResource(Res.string.next_image),
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(tween(240, easing = FastOutSlowInEasing)) +
                slideInVertically(tween(240, easing = FastOutSlowInEasing)) { -it / 2 },
            exit = fadeOut(tween(240, easing = FastOutSlowInEasing)) +
                slideOutVertically(tween(240, easing = FastOutSlowInEasing)) { -it / 2 },
        ) {
            val measuredTopBarHeight = with(density) { topBarHeightPx.toDp() }
            val statusBarHeightPx = WindowInsets.statusBars.getTop(density).toFloat()
            val statusBarHeight = with(density) { statusBarHeightPx.toDp() }
            val scrimHeight = statusBarHeight + measuredTopBarHeight + 32.dp
            val scrimHeightPx = with(density) { scrimHeight.toPx() }
            val flatFraction = markdownImageViewerScrimFlatFraction(
                scrimHeightPx = scrimHeightPx,
                systemBarHeightPx = statusBarHeightPx,
                topBarHeightPx = topBarHeightPx.toFloat(),
            )
            val topBarScrim = remember(flatFraction) {
                smoothMarkdownImageViewerScrimBrush(
                    color = Color.Black.copy(alpha = 0.6f),
                    flatFraction = flatFraction,
                )
            }
            Box(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(scrimHeight)
                        .background(topBarScrim),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding(),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .onSizeChanged { topBarHeightPx = it.height },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(color = Color.Black.copy(alpha = 0.24f), shape = CircleShape) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.markdownClickablePointer(),
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_arrow_back_24dp),
                                    contentDescription = stringResource(Res.string.close_image_viewer),
                                    tint = Color.White,
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AnimatedContent(
                                targetState = presentedPage + 1,
                                contentAlignment = Alignment.CenterStart,
                                transitionSpec = {
                                    (fadeIn(tween(120)) togetherWith fadeOut(tween(120))) using
                                        SizeTransform(clip = false) { _, _ ->
                                            tween(
                                                durationMillis = 120,
                                                easing = FastOutSlowInEasing,
                                            )
                                        }
                                },
                                label = "Markdown image index",
                            ) { index ->
                                Text(
                                    text = index.toString(),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                )
                            }
                            Text(
                                text = " / ${gallery.size}",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                            )
                        }
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                        actions(currentImage)
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownImageNavigationButton(
    enabled: Boolean,
    onClick: () -> Unit,
    icon: org.jetbrains.compose.resources.DrawableResource,
    contentDescription: String,
) {
    Surface(
        color = Color.Black.copy(alpha = if (enabled) 0.32f else 0.16f),
        shape = CircleShape,
        modifier = Modifier
            .size(48.dp)
            .alpha(if (enabled) 1f else 0.54f),
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxSize()
                .markdownClickablePointer(enabled),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = contentDescription,
                modifier = Modifier.size(24.dp),
                tint = Color.White,
            )
        }
    }
}
