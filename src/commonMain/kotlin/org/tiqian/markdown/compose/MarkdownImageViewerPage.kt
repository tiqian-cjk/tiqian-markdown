package org.tiqian.markdown.compose

import org.tiqian.markdown.MarkdownImageBlock

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow

private const val DESKTOP_WHEEL_ZOOM_BASE = 1.12f
private const val DESKTOP_KEYBOARD_ZOOM_FACTOR = 1.25f
private val DESKTOP_KEYBOARD_PAN_DISTANCE = 48.dp

@Composable
internal fun MarkdownImageViewerPage(
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
