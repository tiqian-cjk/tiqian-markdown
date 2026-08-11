package org.tiqian.markdown.compose

import org.tiqian.markdown.MarkdownImageBlock
import org.tiqian.markdown.MarkdownNodeKey

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.DragScope
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
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
import kotlin.math.abs
import kotlin.math.pow

private const val DESKTOP_WHEEL_ZOOM_BASE = 1.12f
private const val DESKTOP_KEYBOARD_ZOOM_FACTOR = 1.25f
private const val MARKDOWN_IMAGE_VIEWER_FADE_DURATION_MILLIS = 180
private val DESKTOP_KEYBOARD_PAN_DISTANCE = 48.dp

private class MarkdownImagePagerGesture(initialPage: Int) {
    var anchorPage: Int = initialPage
    var dragDistancePx: Float = 0f
}

private class MarkdownImagePagerDragState(
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

private suspend fun PagerState.settleToPageWithoutOvershoot(
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

/** Loading information supplied by the host's image library. */
enum class MarkdownImageLoadState {
    Loading,
    Success,
    Error,
}

/**
 * A decoded image presentation. The host keeps ownership of fetching, authentication, caches and
 * animated/vector formats; the renderer owns figure and viewer geometry.
 */
@Immutable
class MarkdownImageContent(
    val intrinsicSize: IntSize?,
    val loadState: MarkdownImageLoadState,
    val progress: Float? = null,
    val content: @Composable (Modifier) -> Unit,
)

typealias MarkdownImageProvider = @Composable (MarkdownImageBlock) -> MarkdownImageContent

/** Pager persists item keys through Android Bundle, so expose the structural key as a String. */
internal fun MarkdownNodeKey.imageViewerSaveableKey(): String = buildString {
    append(parserStableKey)
    append(':')
    path.joinTo(this, separator = ".")
}

/** Where the viewer overlay is laid out relative to its host. */
enum class MarkdownImageViewerPresentation {
    /** The host itself already occupies the complete viewport. */
    HostBounds,

    /** Use a platform popup when Markdown is nested inside a scrolling article body. */
    PlatformWindow,
}

@Stable
class MarkdownImageViewerState internal constructor() {
    internal var activeSession: MarkdownImageViewerSession? by mutableStateOf(null)
        private set

    val activeImage: MarkdownImageBlock?
        get() = activeSession?.activeImage

    val isVisible: Boolean get() = activeSession != null

    fun show(image: MarkdownImageBlock, gallery: List<MarkdownImageBlock> = listOf(image)) {
        val normalizedGallery = gallery
            .distinctBy { it.metadata.key }
            .let { images -> if (images.any { it.metadata.key == image.metadata.key }) images else images + image }
        activeSession = MarkdownImageViewerSession(
            images = normalizedGallery,
            initialIndex = normalizedGallery.indexOfFirst { it.metadata.key == image.metadata.key },
        )
    }

    fun dismiss() {
        activeSession = null
    }
}

@Stable
internal class MarkdownImageViewerSession(
    val images: List<MarkdownImageBlock>,
    initialIndex: Int,
) {
    init {
        require(images.isNotEmpty()) { "An image viewer session needs at least one image" }
    }

    var activeIndex by mutableIntStateOf(initialIndex.coerceIn(images.indices))
        private set

    val activeImage: MarkdownImageBlock get() = images[activeIndex]

    fun select(index: Int) {
        if (index in images.indices) activeIndex = index
    }
}

@Composable
fun rememberMarkdownImageViewerState(): MarkdownImageViewerState =
    remember { MarkdownImageViewerState() }

private val LocalMarkdownImageProvider = staticCompositionLocalOf<MarkdownImageProvider?> { null }
private val LocalMarkdownImageViewerState = staticCompositionLocalOf<MarkdownImageViewerState?> { null }
internal val LocalMarkdownImageGallery = staticCompositionLocalOf<List<MarkdownImageBlock>> { emptyList() }

/**
 * Installs image loading and the optional full-window viewer around a Markdown surface.
 *
 * Put this at the screen boundary, rather than around each paragraph, so the overlay receives the
 * whole available viewport. The host can observe [state] to connect its platform back gesture.
 */
@Composable
@OptIn(ExperimentalComposeUiApi::class)
@Suppress("DEPRECATION")
fun MarkdownImageViewerHost(
    imageProvider: MarkdownImageProvider,
    modifier: Modifier = Modifier,
    state: MarkdownImageViewerState = rememberMarkdownImageViewerState(),
    presentation: MarkdownImageViewerPresentation = MarkdownImageViewerPresentation.HostBounds,
    viewerActions: @Composable RowScope.(MarkdownImageBlock) -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier) {
        CompositionLocalProvider(
            LocalMarkdownImageProvider provides imageProvider,
            LocalMarkdownImageViewerState provides state,
        ) {
            content()
        }
        if (state.isVisible) {
            BackHandler(onBack = state::dismiss)
        }
        when (presentation) {
            MarkdownImageViewerPresentation.HostBounds -> MarkdownImageViewerHostBoundsOverlay(
                state = state,
                imageProvider = imageProvider,
                viewerActions = viewerActions,
            )

            MarkdownImageViewerPresentation.PlatformWindow -> MarkdownImageViewerPlatformWindowOverlay(
                state = state,
                imageProvider = imageProvider,
                viewerActions = viewerActions,
            )
        }
    }
}

@Composable
private fun MarkdownImageViewerHostBoundsOverlay(
    state: MarkdownImageViewerState,
    imageProvider: MarkdownImageProvider,
    viewerActions: @Composable RowScope.(MarkdownImageBlock) -> Unit,
) {
    AnimatedContent(
        targetState = state.activeSession,
        modifier = Modifier.fillMaxSize().zIndex(1f),
        transitionSpec = {
            (fadeIn(
                animationSpec = tween(
                    durationMillis = MARKDOWN_IMAGE_VIEWER_FADE_DURATION_MILLIS,
                    easing = FastOutSlowInEasing,
                ),
            ) togetherWith fadeOut(
                animationSpec = tween(
                    durationMillis = MARKDOWN_IMAGE_VIEWER_FADE_DURATION_MILLIS,
                    easing = FastOutSlowInEasing,
                ),
            )) using null
        },
        contentKey = { it != null },
    ) { session ->
        if (session != null) {
            MarkdownImageViewerSessionContent(session, imageProvider, state::dismiss, viewerActions)
        }
    }
}

@Composable
private fun MarkdownImageViewerPlatformWindowOverlay(
    state: MarkdownImageViewerState,
    imageProvider: MarkdownImageProvider,
    viewerActions: @Composable RowScope.(MarkdownImageBlock) -> Unit,
) {
    state.activeSession?.let { session ->
        MarkdownImageViewerPlatformWindow(onDismissRequest = state::dismiss) {
            MarkdownImageViewerSessionContent(session, imageProvider, state::dismiss, viewerActions)
        }
    }
}

@Composable
private fun MarkdownImageViewerSessionContent(
    session: MarkdownImageViewerSession,
    imageProvider: MarkdownImageProvider,
    onDismiss: () -> Unit,
    viewerActions: @Composable RowScope.(MarkdownImageBlock) -> Unit,
) {
    MarkdownImageViewer(
        image = session.activeImage,
        images = session.images,
        imageProvider = imageProvider,
        onDismiss = onDismiss,
        onImageChange = { image ->
            session.select(session.images.indexOfFirst { it.metadata.key == image.metadata.key })
        },
        actions = viewerActions,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
internal fun markdownImageContent(block: MarkdownImageBlock): MarkdownImageContent? =
    LocalMarkdownImageProvider.current?.invoke(block)

internal fun Modifier.openMarkdownImageOnClick(
    state: MarkdownImageViewerState?,
    block: MarkdownImageBlock,
    gallery: List<MarkdownImageBlock>,
): Modifier = if (state == null) {
    this
} else {
    markdownClickablePointer().clickable(role = Role.Image) { state.show(block, gallery) }
}

@Composable
internal fun currentMarkdownImageViewerState(): MarkdownImageViewerState? =
    LocalMarkdownImageViewerState.current

@Composable
internal fun currentMarkdownImageGallery(): List<MarkdownImageBlock> =
    LocalMarkdownImageGallery.current

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

@Composable
private fun MarkdownImageViewerPage(
    image: MarkdownImageBlock,
    imageProvider: MarkdownImageProvider,
    onDismiss: () -> Unit,
    active: Boolean,
    allowHorizontalPageSwipe: Boolean,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleControls: () -> Unit,
    onDefaultWidthChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val keyboardPanDistancePx = with(density) { DESKTOP_KEYBOARD_PAN_DISTANCE.toPx() }
    val imageContent = imageProvider(image)
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val decay = rememberSplineBasedDecay<Float>()
    var viewportSize by remember(image) { mutableStateOf(IntSize.Zero) }
    var scale by remember(image) { mutableFloatStateOf(1f) }
    var translation by remember(image) { mutableStateOf(Offset.Zero) }
    var hasUserInteracted by remember(image) { mutableStateOf(false) }
    var lastGestureAnchor by remember(image) { mutableStateOf<Offset?>(null) }
    var animationJob by remember(image) { mutableStateOf<Job?>(null) }
    val maxLongImageWidthPx = with(density) { 600.dp.toPx() }
    val layout = remember(viewportSize, imageContent.intrinsicSize, maxLongImageWidthPx) {
        calculateMarkdownImageViewerLayout(
            viewportSize = viewportSize,
            imageSize = imageContent.intrinsicSize,
            maxLongImageWidthPx = maxLongImageWidthPx,
        )
    }
    val minimumScale = remember(layout) {
        layout?.let(::calculateMarkdownImageViewerMinimumScale) ?: 1f
    }

    fun displayedScale(currentLayout: MarkdownImageViewerLayout): Float =
        if (!hasUserInteracted && animationJob == null) currentLayout.resetScale else scale

    fun displayedTranslation(currentLayout: MarkdownImageViewerLayout): Offset =
        if (!hasUserInteracted && animationJob == null) currentLayout.baseOrigin else translation

    val isAtDefaultWidth = layout?.let { currentLayout ->
        abs(displayedScale(currentLayout) - currentLayout.resetScale) < 0.01f
    } ?: true

    fun animateTransform(
        targetScale: Float,
        targetTranslation: Offset,
        useTween: Boolean,
        initialScale: Float = scale,
        initialTranslation: Offset = translation,
    ) {
        animationJob?.cancel()
        animationJob = scope.launch {
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = if (useTween) {
                    tween(durationMillis = 220, easing = FastOutSlowInEasing)
                } else {
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    )
                },
            ) { value, _ ->
                scale = initialScale + (targetScale - initialScale) * value
                translation = Offset(
                    x = initialTranslation.x + (targetTranslation.x - initialTranslation.x) * value,
                    y = initialTranslation.y + (targetTranslation.y - initialTranslation.y) * value,
                )
            }
            scale = targetScale
            translation = targetTranslation
            animationJob = null
        }
    }

    fun settleTransform(currentLayout: MarkdownImageViewerLayout) {
        val currentScale = displayedScale(currentLayout)
        val currentTranslation = displayedTranslation(currentLayout)
        val settledScale = coerceMarkdownImageViewerSettledScale(currentScale, minimumScale)
        val anchor = lastGestureAnchor ?: Offset(
            currentLayout.viewportSize.width / 2f,
            currentLayout.viewportSize.height / 2f,
        )
        val rebased = if (abs(settledScale - currentScale) > 0.001f) {
            calculateMarkdownImageViewerScaledTranslation(
                currentTranslation,
                currentScale,
                settledScale,
                anchor,
                anchor,
            )
        } else {
            currentTranslation
        }
        animateTransform(
            targetScale = settledScale,
            targetTranslation = clampMarkdownImageViewerTranslation(rebased, currentLayout, settledScale),
            useTween = false,
            initialScale = currentScale,
            initialTranslation = currentTranslation,
        )
    }

    fun zoomAround(anchor: Offset, factor: Float, animated: Boolean) {
        val currentLayout = layout ?: return
        val oldScale = displayedScale(currentLayout)
        val oldTranslation = displayedTranslation(currentLayout)
        val targetScale = coerceMarkdownImageViewerSettledScale(
            scale = oldScale * factor,
            minimumScale = minimumScale,
        )
        if (abs(targetScale - oldScale) < 0.0001f) return
        val targetTranslation = clampMarkdownImageViewerTranslation(
            translation = calculateMarkdownImageViewerScaledTranslation(
                currentTranslation = oldTranslation,
                currentScale = oldScale,
                targetScale = targetScale,
                anchor = anchor,
                destinationAnchor = anchor,
            ),
            layout = currentLayout,
            scale = targetScale,
        )
        animationJob?.cancel()
        animationJob = null
        hasUserInteracted = true
        if (animated) {
            animateTransform(
                targetScale = targetScale,
                targetTranslation = targetTranslation,
                useTween = true,
                initialScale = oldScale,
                initialTranslation = oldTranslation,
            )
        } else {
            scale = targetScale
            translation = targetTranslation
        }
    }

    fun resetTransform(animated: Boolean) {
        val currentLayout = layout ?: return
        val oldScale = displayedScale(currentLayout)
        val oldTranslation = displayedTranslation(currentLayout)
        hasUserInteracted = true
        animateTransform(
            targetScale = currentLayout.resetScale,
            targetTranslation = currentLayout.baseOrigin,
            useTween = animated,
            initialScale = oldScale,
            initialTranslation = oldTranslation,
        )
    }

    fun panBy(delta: Offset) {
        val currentLayout = layout ?: return
        val currentScale = displayedScale(currentLayout)
        val currentTranslation = displayedTranslation(currentLayout)
        animationJob?.cancel()
        animationJob = null
        hasUserInteracted = true
        scale = currentScale
        translation = clampMarkdownImageViewerTranslation(
            translation = currentTranslation + delta,
            layout = currentLayout,
            scale = currentScale,
        )
    }

    fun startFling(
        currentLayout: MarkdownImageViewerLayout,
        velocityTracker: VelocityTracker,
    ): Boolean {
        val currentScale = displayedScale(currentLayout)
        val settledScale = coerceMarkdownImageViewerSettledScale(currentScale, minimumScale)
        if (abs(currentScale - settledScale) > 0.001f) return false
        val bounds = calculateMarkdownImageViewerTranslationBounds(currentLayout, currentScale)
        val velocity = velocityTracker.calculateVelocity()
        val minimumVelocity = with(density) { 50.dp.toPx() }
        val maximumVelocity = with(density) { 8_000.dp.toPx() }
        val velocityX = velocity.x
            .takeIf { abs(it) >= minimumVelocity && bounds.minX < bounds.maxX }
            ?.coerceIn(-maximumVelocity, maximumVelocity) ?: 0f
        val velocityY = velocity.y
            .takeIf { abs(it) >= minimumVelocity && bounds.minY < bounds.maxY }
            ?.coerceIn(-maximumVelocity, maximumVelocity) ?: 0f
        if (velocityX == 0f && velocityY == 0f) return false

        animationJob?.cancel()
        var currentX = translation.x
        var currentY = translation.y
        animationJob = scope.launch {
            coroutineScope {
                if (velocityX != 0f) launch {
                    AnimationState(currentX, velocityX).animateDecay(decay) {
                        currentX = value.coerceIn(bounds.minX, bounds.maxX)
                        translation = Offset(currentX, currentY)
                        if (value != currentX) cancelAnimation()
                    }
                }
                if (velocityY != 0f) launch {
                    AnimationState(currentY, velocityY).animateDecay(decay) {
                        currentY = value.coerceIn(bounds.minY, bounds.maxY)
                        translation = Offset(currentX, currentY)
                        if (value != currentY) cancelAnimation()
                    }
                }
            }
            translation = clampMarkdownImageViewerTranslation(translation, currentLayout, currentScale)
            animationJob = null
        }
        return true
    }

    LaunchedEffect(layout, minimumScale) {
        val currentLayout = layout ?: return@LaunchedEffect
        animationJob?.cancel()
        animationJob = null
        if (!hasUserInteracted) {
            scale = currentLayout.resetScale
            translation = currentLayout.baseOrigin
        } else {
            scale = coerceMarkdownImageViewerSettledScale(scale, minimumScale)
            translation = clampMarkdownImageViewerTranslation(translation, currentLayout, scale)
        }
    }

    LaunchedEffect(isAtDefaultWidth) {
        onDefaultWidthChanged(isAtDefaultWidth)
    }

    LaunchedEffect(active, image) {
        if (active) focusRequester.requestFocus()
    }

    BoxWithConstraints(
        modifier
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (!active || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val viewportCenter = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
                when {
                    event.key == Key.Escape -> {
                        onDismiss()
                        true
                    }
                    event.utf16CodePoint == '+'.code || event.utf16CodePoint == '='.code -> {
                        zoomAround(viewportCenter, DESKTOP_KEYBOARD_ZOOM_FACTOR, animated = true)
                        true
                    }
                    event.utf16CodePoint == '-'.code || event.utf16CodePoint == '_'.code -> {
                        zoomAround(viewportCenter, 1f / DESKTOP_KEYBOARD_ZOOM_FACTOR, animated = true)
                        true
                    }
                    event.utf16CodePoint == '0'.code -> {
                        resetTransform(animated = true)
                        true
                    }
                    event.key == Key.DirectionLeft -> {
                        if (isAtDefaultWidth && canGoPrevious) {
                            onPrevious()
                        } else {
                            panBy(Offset(keyboardPanDistancePx, 0f))
                        }
                        true
                    }
                    event.key == Key.DirectionRight -> {
                        if (isAtDefaultWidth && canGoNext) {
                            onNext()
                        } else {
                            panBy(Offset(-keyboardPanDistancePx, 0f))
                        }
                        true
                    }
                    event.key == Key.DirectionUp -> {
                        panBy(Offset(0f, keyboardPanDistancePx))
                        true
                    }
                    event.key == Key.DirectionDown -> {
                        panBy(Offset(0f, -keyboardPanDistancePx))
                        true
                    }
                    else -> false
                }
            },
    ) {
        viewportSize = IntSize(constraints.maxWidth, constraints.maxHeight)
        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(active, layout, minimumScale) {
                    if (!active) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Scroll) continue
                            val change = event.changes.firstOrNull() ?: continue
                            val verticalScroll = change.scrollDelta.y
                            if (verticalScroll == 0f) continue
                            val exponent = (-verticalScroll).coerceIn(-4f, 4f)
                            zoomAround(
                                anchor = change.position,
                                factor = DESKTOP_WHEEL_ZOOM_BASE.pow(exponent),
                                animated = false,
                            )
                            change.consume()
                        }
                    }
                }
                .pointerInput(active, layout, minimumScale, allowHorizontalPageSwipe) {
                    if (!active) return@pointerInput
                    val currentLayout = layout ?: return@pointerInput
                    awaitEachGesture {
                        val velocityTracker = VelocityTracker()
                        var didTransform = false
                        var hadMultiplePointers = false
                        var endGesture = false
                        var lockedToPager = false
                        var lockedToImage = false
                        var accumulatedDelta = Offset.Zero
                        do {
                            val event = awaitPointerEvent()
                            val pressedCount = event.changes.count { it.pressed }
                            if (pressedCount >= 2) {
                                hadMultiplePointers = true
                            } else if (hadMultiplePointers) {
                                endGesture = true
                                break
                            } else {
                                event.changes.firstOrNull { it.pressed }?.let { change ->
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                }
                            }
                            val previousCentroid = event.calculateCentroid(useCurrent = false)
                            val currentCentroid = event.calculateCentroid(useCurrent = true)
                            val zoom = event.calculateZoom()
                            val changed = previousCentroid != Offset.Unspecified &&
                                currentCentroid != Offset.Unspecified &&
                                (zoom != 1f || currentCentroid != previousCentroid)
                            if (changed) {
                                val oldScale = if (didTransform) scale else displayedScale(currentLayout)
                                val gestureDelta = currentCentroid - previousCentroid
                                if (!lockedToPager && !lockedToImage) {
                                    val canChoosePager = allowHorizontalPageSwipe &&
                                        !hadMultiplePointers &&
                                        zoom == 1f &&
                                        abs(oldScale - currentLayout.resetScale) < 0.01f
                                    if (canChoosePager) {
                                        accumulatedDelta += gestureDelta
                                        if (accumulatedDelta.getDistance() < viewConfiguration.touchSlop) continue
                                        lockedToPager = abs(accumulatedDelta.x) > abs(accumulatedDelta.y)
                                        lockedToImage = !lockedToPager
                                    } else {
                                        lockedToImage = true
                                    }
                                }
                                if (lockedToPager) continue
                                val oldTranslation = if (didTransform) {
                                    translation
                                } else {
                                    displayedTranslation(currentLayout)
                                }
                                if (!didTransform) {
                                    animationJob?.cancel()
                                    animationJob = null
                                    scale = oldScale
                                    translation = oldTranslation
                                    hasUserInteracted = true
                                }
                                didTransform = true
                                lastGestureAnchor = currentCentroid
                                val targetScale = coerceMarkdownImageViewerGestureScale(
                                    oldScale * zoom,
                                    minimumScale,
                                )
                                val targetTranslation = calculateMarkdownImageViewerScaledTranslation(
                                    oldTranslation,
                                    oldScale,
                                    targetScale,
                                    previousCentroid,
                                    currentCentroid,
                                )
                                translation = if (hadMultiplePointers) {
                                    targetTranslation
                                } else {
                                    clampMarkdownImageViewerTranslation(
                                        targetTranslation,
                                        currentLayout,
                                        targetScale,
                                    )
                                }
                                scale = targetScale
                                event.changes.filter { it.positionChanged() }.forEach { it.consume() }
                            }
                        } while (!endGesture && event.changes.any { it.pressed })

                        if (didTransform && !(!hadMultiplePointers && startFling(currentLayout, velocityTracker))) {
                            settleTransform(currentLayout)
                        }
                    }
                }
                .pointerInput(active, layout, minimumScale) {
                    if (!active) return@pointerInput
                    val currentLayout = layout ?: return@pointerInput
                    detectTapGestures(
                        onTap = { tap ->
                            val currentScale = displayedScale(currentLayout)
                            val currentTranslation = displayedTranslation(currentLayout)
                            val imageBounds = Rect(
                                left = currentTranslation.x,
                                top = currentTranslation.y,
                                right = currentTranslation.x + currentLayout.baseSize.width * currentScale,
                                bottom = currentTranslation.y + currentLayout.baseSize.height * currentScale,
                            )
                            if (markdownImageViewerHasDesktopNavigation && tap !in imageBounds) {
                                onDismiss()
                            } else {
                                onToggleControls()
                            }
                        },
                        onDoubleTap = { tap ->
                            val oldScale = displayedScale(currentLayout)
                            val oldTranslation = displayedTranslation(currentLayout)
                            animationJob?.cancel()
                            animationJob = null
                            scale = oldScale
                            translation = oldTranslation
                            hasUserInteracted = true
                            if (abs(oldScale - currentLayout.resetScale) > 0.05f) {
                                val resetTranslation = if (currentLayout.isLongImage) {
                                    clampMarkdownImageViewerTranslation(
                                        calculateMarkdownImageViewerScaledTranslation(
                                            oldTranslation,
                                            oldScale,
                                            currentLayout.resetScale,
                                            tap,
                                            Offset(tap.x, currentLayout.viewportSize.height / 2f),
                                        ),
                                        currentLayout,
                                        currentLayout.resetScale,
                                    )
                                } else {
                                    currentLayout.baseOrigin
                                }
                                animateTransform(
                                    currentLayout.resetScale,
                                    resetTranslation,
                                    true,
                                    oldScale,
                                    oldTranslation,
                                )
                            } else {
                                val targetScale = (currentLayout.resetScale *
                                    MARKDOWN_IMAGE_VIEWER_DOUBLE_TAP_SCALE_MULTIPLIER)
                                    .coerceAtMost(
                                        maxOf(
                                            MARKDOWN_IMAGE_VIEWER_MAX_SCALE,
                                            currentLayout.resetScale,
                                        ),
                                    )
                                val targetTranslation = clampMarkdownImageViewerTranslation(
                                    calculateMarkdownImageViewerScaledTranslation(
                                        oldTranslation,
                                        oldScale,
                                        targetScale,
                                        tap,
                                        tap,
                                    ),
                                    currentLayout,
                                    targetScale,
                                )
                                animateTransform(
                                    targetScale,
                                    targetTranslation,
                                    true,
                                    oldScale,
                                    oldTranslation,
                                )
                            }
                        },
                    )
                },
        ) {
            if (layout != null) {
                Layout(
                    content = {
                        imageContent.content(
                            Modifier.graphicsLayer {
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                                translationX = displayedTranslation(layout).x
                                translationY = displayedTranslation(layout).y
                                scaleX = displayedScale(layout)
                                scaleY = displayedScale(layout)
                            },
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                ) { measurables, constraints ->
                    val imageWidth = layout.baseSize.width.toInt().coerceAtLeast(1)
                    val imageHeight = layout.baseSize.height.toInt().coerceAtLeast(1)
                    val placeable = measurables.single().measure(
                        androidx.compose.ui.unit.Constraints.fixed(imageWidth, imageHeight),
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(0, 0)
                    }
                }
            } else {
                imageContent.content(Modifier.fillMaxSize().alpha(0f))
            }

            when (imageContent.loadState) {
                MarkdownImageLoadState.Loading -> CircularProgressIndicator(
                    progress = { imageContent.progress ?: 0f },
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.24f),
                )
                MarkdownImageLoadState.Error -> Text(
                    text = image.description.ifBlank { "Image failed to load" },
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                MarkdownImageLoadState.Success -> Unit
            }
        }
    }
}
