package com.ai.assistance.operit.ui.common.markdown

import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import android.view.ViewConfiguration
import android.widget.Toast
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.editorCellWidth
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.editorNextSymbolOffset
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

private data class CodeBlockMetrics(
    val charWidth: Float,
    val lineHeight: Float,
    val baseline: Float,
)

private data class CodeBlockColorRange(
    val start: Int,
    val end: Int,
    val colorArgb: Int,
)

private data class CodeBlockWrappedRow(
    val start: Int,
    val end: Int,
    val cells: Int,
)

private data class CodeBlockLineRenderData(
    val text: String,
    val colorRanges: List<CodeBlockColorRange>,
    val rows: List<CodeBlockWrappedRow>,
    val maxCells: Int,
)

private val CodeDefaultTextColor = Color(0xFFD4D4D4)
private val CodeLineNumberColor = Color(0xFF6A737D)

@Composable
internal fun CanvasMonospaceCodeBlockBody(
    code: String,
    codeLines: List<String>,
    highlightedLines: List<AnnotatedString>?,
    autoWrapEnabled: Boolean,
    maxScrollableHeight: Dp,
    modifier: Modifier = Modifier,
    textSelectionRequest: MarkdownTextSelectionRequest? = null,
    selectionState: MarkdownCanvasTextSelectionState? = null,
    nodeIndex: Int = -1,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val hostView = LocalView.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val touchSlop =
        remember(hostView) {
            ViewConfiguration.get(hostView.context).scaledTouchSlop.toFloat()
        }
    val verticalFlingAnim = remember { Animatable(0f) }
    val horizontalFlingAnim = remember { Animatable(0f) }
    val flingDecay = rememberSplineBasedDecay<Float>()
    val codeTypeface = remember(context) { getMarkdownCodeTypeface(context) }
    val textSizePx = with(density) { 12.sp.toPx() }
    val textPaint =
        remember(codeTypeface, textSizePx) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = codeTypeface
                textSize = textSizePx
                isSubpixelText = true
            }
        }
    val systemGlyphPaint =
        remember(textSizePx) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.DEFAULT
                textSize = textSizePx
                isSubpixelText = true
            }
        }
    val lineNumberPaint =
        remember(codeTypeface, textSizePx) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = codeTypeface
                textSize = textSizePx
                color = CodeLineNumberColor.toArgb()
                isSubpixelText = true
            }
        }
    val selectionTextPaint =
        remember(codeTypeface, textSizePx) {
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = codeTypeface
                textSize = textSizePx
                color = CodeDefaultTextColor.toArgb()
                isSubpixelText = true
            }
        }
    val metrics =
        remember(codeTypeface, textSizePx) {
            textPaint.typeface = codeTypeface
            textPaint.textSize = textSizePx
            val fontMetrics = textPaint.fontMetrics
            val charWidth = ceil(textPaint.measureText("M").toDouble()).toFloat().coerceAtLeast(1f)
            val charHeight = fontMetrics.descent - fontMetrics.ascent
            val lineHeight = (charHeight * 1.25f).coerceAtLeast(1f)
            val baseline = -fontMetrics.ascent + (lineHeight - charHeight) / 2f
            CodeBlockMetrics(
                charWidth = charWidth,
                lineHeight = lineHeight,
                baseline = baseline,
            )
        }

    var autoScrollEnabled by remember { mutableStateOf(true) }
    var isProgrammaticScroll by remember { mutableStateOf(false) }
    // Keep scroll state stable across streaming code updates; the surrounding node key
    // already scopes this state to the current markdown/code-block instance.
    var verticalOffsetPx by remember(autoWrapEnabled) { mutableStateOf(0f) }
    var horizontalOffsetPx by remember(autoWrapEnabled) { mutableStateOf(0f) }
    var isHandlingTouch by remember { mutableStateOf(false) }

    val displayLines =
        remember(codeLines, highlightedLines) {
            if (highlightedLines != null) {
                highlightedLines
            } else {
                codeLines.map { AnnotatedString(it) }
            }
        }
    val defaultTextColorArgb =
        remember(highlightedLines) {
            if (highlightedLines != null) {
                CodeDefaultTextColor.toArgb()
            } else {
                Color.White.toArgb()
            }
        }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .heightIn(max = maxScrollableHeight),
    ) {
        val viewportWidthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val maxScrollableHeightPx =
            with(density) { maxScrollableHeight.toPx() }.coerceAtLeast(metrics.lineHeight)
        val outerTopPaddingPx = with(density) { 8.dp.toPx() }
        val outerBottomPaddingPx = with(density) { 8.dp.toPx() }
        val contentEndPaddingPx = with(density) { 8.dp.toPx() }
        val gutterHorizontalPaddingPx = with(density) { 8.dp.toPx() }
        val lineNumberEndPaddingPx = with(density) { 4.dp.toPx() }
        val gutterToCodeGapPx = with(density) { 4.dp.toPx() }
        val lineSpacingPx = with(density) { 4.dp.toPx() }
        val digits = codeLines.size.toString().length.coerceAtLeast(2)
        val gutterWidthPx =
            gutterHorizontalPaddingPx +
                metrics.charWidth * digits +
                lineNumberEndPaddingPx +
                gutterHorizontalPaddingPx
        val codeMeasureWidthPx =
            (viewportWidthPx - gutterWidthPx - gutterToCodeGapPx - contentEndPaddingPx)
                .coerceAtLeast(metrics.charWidth)
        val maxColumns =
            if (autoWrapEnabled) {
                (codeMeasureWidthPx / metrics.charWidth).toInt().coerceAtLeast(1)
            } else {
                Int.MAX_VALUE
            }

        val lineRenderData =
            remember(displayLines, autoWrapEnabled, maxColumns, defaultTextColorArgb) {
                displayLines.map { line ->
                    buildCodeBlockLineRenderData(
                        line = line,
                        autoWrapEnabled = autoWrapEnabled,
                        maxColumns = maxColumns,
                        defaultTextColorArgb = defaultTextColorArgb,
                    )
                }
            }
        val lineBlockHeightsPx =
            remember(lineRenderData, metrics.lineHeight) {
                FloatArray(lineRenderData.size) { index ->
                    lineRenderData[index].rows.size.coerceAtLeast(1) * metrics.lineHeight
                }
            }
        val lineTopsPx =
            remember(lineBlockHeightsPx, outerTopPaddingPx, lineSpacingPx) {
                FloatArray(lineBlockHeightsPx.size).also { tops ->
                    var currentTop = outerTopPaddingPx
                    lineBlockHeightsPx.indices.forEach { index ->
                        tops[index] = currentTop
                        currentTop += lineBlockHeightsPx[index]
                        if (index < lineBlockHeightsPx.lastIndex) {
                            currentTop += lineSpacingPx
                        }
                    }
                }
            }
        val totalContentHeightPx =
            remember(lineBlockHeightsPx, outerTopPaddingPx, outerBottomPaddingPx, lineSpacingPx) {
                if (lineBlockHeightsPx.isEmpty()) {
                    outerTopPaddingPx + outerBottomPaddingPx + metrics.lineHeight
                } else {
                    outerTopPaddingPx +
                        outerBottomPaddingPx +
                        lineBlockHeightsPx.sum() +
                        lineSpacingPx * (lineBlockHeightsPx.size - 1)
                }
            }
        val actualViewportHeightPx =
            totalContentHeightPx.coerceAtMost(maxScrollableHeightPx).coerceAtLeast(metrics.lineHeight)
        val maxLineCells =
            remember(lineRenderData) {
                lineRenderData.maxOfOrNull { it.maxCells }?.coerceAtLeast(1) ?: 1
            }
        val codeColumnWidthPx =
            if (autoWrapEnabled) {
                codeMeasureWidthPx
            } else {
                (maxLineCells * metrics.charWidth).coerceAtLeast(codeMeasureWidthPx)
            }
        val canvasWidthPx =
            gutterWidthPx + gutterToCodeGapPx + codeColumnWidthPx + contentEndPaddingPx
        val maxHorizontalOffsetPx = (canvasWidthPx - viewportWidthPx).coerceAtLeast(0f)
        val maxVerticalOffsetPx = (totalContentHeightPx - actualViewportHeightPx).coerceAtLeast(0f)
        val verticalOnlyScroll = autoWrapEnabled
        val overscanPx = metrics.lineHeight * 6f
        val minVisibleTopPx = (verticalOffsetPx - overscanPx).coerceAtLeast(0f)
        val maxVisibleBottomPx = verticalOffsetPx + actualViewportHeightPx + overscanPx
        val visibleRange =
            remember(lineTopsPx, lineBlockHeightsPx, minVisibleTopPx, maxVisibleBottomPx) {
                if (lineTopsPx.isEmpty()) {
                    null
                } else {
                    val startIndex =
                        findFirstVisibleCodeLine(
                            lineTopsPx = lineTopsPx,
                            lineHeightsPx = lineBlockHeightsPx,
                            targetTopPx = minVisibleTopPx,
                        )
                    val endIndex =
                        findLastVisibleCodeLine(
                            lineTopsPx = lineTopsPx,
                            targetBottomPx = maxVisibleBottomPx,
                        )
                    if (startIndex > endIndex || startIndex >= lineTopsPx.size || endIndex < 0) {
                        null
                    } else {
                        startIndex to endIndex
                    }
                }
            }
        val visibleLines =
            remember(visibleRange, lineRenderData) {
                val range = visibleRange ?: return@remember emptyList()
                lineRenderData.subList(range.first, range.second + 1)
            }
        val visibleStartIndex = visibleRange?.first ?: 0
        val currentVerticalOffsetPx by rememberUpdatedState(verticalOffsetPx)
        val currentHorizontalOffsetPx by rememberUpdatedState(horizontalOffsetPx)
        val currentMaxVerticalOffsetPx by rememberUpdatedState(maxVerticalOffsetPx)
        val currentMaxHorizontalOffsetPx by rememberUpdatedState(maxHorizontalOffsetPx)
        val activeSelectionState = selectionState
        val selectionEnabled = activeSelectionState != null && nodeIndex >= 0
        val codeSelectionLayouts =
            remember(lineRenderData, codeColumnWidthPx, selectionTextPaint) {
                val layoutWidth = codeColumnWidthPx.roundToInt().coerceAtLeast(1)
                lineRenderData.map { line ->
                    createCodeBlockSelectionLayout(line.text, selectionTextPaint, layoutWidth)
                }
            }
        val codeSelectionInstructions =
            remember(
                lineRenderData,
                codeSelectionLayouts,
                lineTopsPx,
                lineBlockHeightsPx,
                verticalOffsetPx,
                horizontalOffsetPx,
                gutterWidthPx,
                gutterToCodeGapPx,
                viewportWidthPx,
            ) {
                buildCodeBlockSelectionInstructions(
                    lineRenderData = lineRenderData,
                    lineLayouts = codeSelectionLayouts,
                    lineTopsPx = lineTopsPx,
                    lineBlockHeightsPx = lineBlockHeightsPx,
                    verticalOffsetPx = verticalOffsetPx,
                    horizontalOffsetPx = horizontalOffsetPx,
                    gutterWidthPx = gutterWidthPx,
                    gutterToCodeGapPx = gutterToCodeGapPx,
                    viewportWidthPx = viewportWidthPx,
                )
            }
        var canvasBoundsInWindow by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
        var toolbarSize by remember(code) { mutableStateOf(IntSize.Zero) }
        val autoScrollController = LocalMarkdownTextSelectionAutoScrollController.current
        val selectionColor = MaterialTheme.colorScheme.primary
        val selectionPaint = remember(selectionColor) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = selectionColor.copy(alpha = 0.24f).toArgb()
                style = Paint.Style.FILL
            }
        }
        val cursorPaint = remember(selectionColor, density) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = selectionColor.toArgb()
                style = Paint.Style.STROKE
                strokeWidth = with(density) { 2.dp.toPx() }
                strokeCap = Paint.Cap.ROUND
            }
        }
        val handlePaint = remember(selectionColor) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = selectionColor.toArgb()
                style = Paint.Style.FILL
            }
        }
        val handleRadiusPx = with(density) { 7.dp.toPx() }
        val handleTouchRadiusPx = with(density) { 38.dp.toPx() }
        val magnifierSurfaceColor = MaterialTheme.colorScheme.surface
        val magnifierTextColor = MaterialTheme.colorScheme.onSurface
        val magnifierBubblePaint = remember(magnifierSurfaceColor) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = magnifierSurfaceColor.copy(alpha = 0.96f).toArgb()
                style = Paint.Style.FILL
            }
        }
        val magnifierBorderPaint = remember(selectionColor, density) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = selectionColor.copy(alpha = 0.45f).toArgb()
                style = Paint.Style.STROKE
                strokeWidth = with(density) { 1.dp.toPx() }
            }
        }
        val magnifierTextPaint = remember(magnifierTextColor, density) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = magnifierTextColor.toArgb()
                textSize = with(density) { 17.sp.toPx() }
                typeface = codeTypeface
                isSubpixelText = true
            }
        }
        val magnifierWidthDp = 164.dp
        val magnifierHeightDp = 48.dp
        val magnifierMarginDp = 8.dp
        val magnifierWidthPx = with(density) { magnifierWidthDp.toPx() }
        val magnifierHeightPx = with(density) { magnifierHeightDp.toPx() }
        val magnifierMarginPx = with(density) { magnifierMarginDp.toPx() }
        val toolbarGapPx = with(density) { 6.dp.toPx() }
        val toolbarEdgePaddingPx = with(density) { 6.dp.toPx() }
        val toolbarEstimatedWidthPx = with(density) { 96.dp.toPx() }
        val toolbarEstimatedHeightPx = with(density) { 30.dp.toPx() }
        val nodeSelectionState =
            remember(activeSelectionState, nodeIndex, codeSelectionInstructions) {
                derivedStateOf {
                    val state = activeSelectionState ?: return@derivedStateOf null
                    state.selection?.let { selection ->
                        nodeSelectionSnapshot(
                            selection = selection,
                            nodeIndex = nodeIndex,
                            instructions = codeSelectionInstructions,
                        )
                    }
                }
            }
        val toolbarSelectionState =
            remember(activeSelectionState, nodeIndex, codeSelectionInstructions) {
                derivedStateOf {
                    val state = activeSelectionState ?: return@derivedStateOf null
                    val selection = state.selection
                    if (selection == null || compareSelectionPoints(selection.start, selection.end) == 0) {
                        null
                    } else {
                        val orderedStart = orderedSelectionPoints(selection).first
                        if (orderedStart.nodeIndex == nodeIndex) {
                            nodeSelectionSnapshot(
                                selection = selection,
                                nodeIndex = nodeIndex,
                                instructions = codeSelectionInstructions,
                            )
                        } else {
                            null
                        }
                    }
                }
            }
        val nodeMagnifierState =
            remember(activeSelectionState, nodeIndex) {
                derivedStateOf {
                    activeSelectionState?.dragMagnifier?.takeIf { it.hostNodeIndex == nodeIndex }
                }
            }
        val nodeSelection = nodeSelectionState.value
        val toolbarNodeSelection = toolbarSelectionState.value
        val nodeMagnifier = nodeMagnifierState.value

        LaunchedEffect(canvasBoundsInWindow, codeSelectionInstructions, selectionEnabled) {
            val state = activeSelectionState ?: return@LaunchedEffect
            if (!selectionEnabled) return@LaunchedEffect
            val bounds = canvasBoundsInWindow ?: return@LaunchedEffect
            state.updateNodeLayout(
                nodeIndex = nodeIndex,
                boundsInWindow = bounds,
                instructions = codeSelectionInstructions,
            )
        }

        LaunchedEffect(textSelectionRequest?.id, canvasBoundsInWindow, codeSelectionInstructions, selectionEnabled) {
            val state = activeSelectionState ?: return@LaunchedEffect
            if (!selectionEnabled) return@LaunchedEffect
            val request = textSelectionRequest
            if (request == null) {
                state.clear()
                return@LaunchedEffect
            }
            if (state.handledRequestId == request.id) {
                return@LaunchedEffect
            }
            val bounds = canvasBoundsInWindow ?: return@LaunchedEffect
            if (!bounds.containsOffset(request.positionInWindow)) {
                state.dragMagnifier = null
                return@LaunchedEffect
            }

            val localPosition = request.positionInWindow - Offset(bounds.left, bounds.top)
            val hit = findTextSelectionHit(codeSelectionInstructions, localPosition, nodeIndex)
            val instruction = hit?.let { codeSelectionInstructions.getOrNull(it.instructionIndex) }
            state.handledRequestId = request.id
            if (hit != null && instruction is DrawInstruction.TextLayout) {
                state.selection =
                    createInitialSelection(
                        nodeIndex = nodeIndex,
                        instructionIndex = hit.instructionIndex,
                        instruction = instruction,
                        offset = hit.offset,
                    )
            } else {
                state.selection = null
                state.dragMagnifier = null
            }
        }

        LaunchedEffect(code, autoWrapEnabled, maxVerticalOffsetPx) {
            if (autoScrollEnabled) {
                isProgrammaticScroll = true
                verticalFlingAnim.stop()
                try {
                    verticalOffsetPx = maxVerticalOffsetPx
                } finally {
                    isProgrammaticScroll = false
                }
            } else {
                verticalOffsetPx = verticalOffsetPx.coerceIn(0f, maxVerticalOffsetPx)
            }
            horizontalOffsetPx =
                if (verticalOnlyScroll) {
                    0f
                } else {
                    horizontalOffsetPx.coerceIn(0f, maxHorizontalOffsetPx)
                }
        }

        LaunchedEffect(maxVerticalOffsetPx, maxHorizontalOffsetPx) {
            verticalOffsetPx = verticalOffsetPx.coerceIn(0f, maxVerticalOffsetPx)
            horizontalOffsetPx =
                if (verticalOnlyScroll) {
                    0f
                } else {
                    horizontalOffsetPx.coerceIn(0f, maxHorizontalOffsetPx)
                }
        }

        DisposableEffect(Unit) {
            onDispose {
                isHandlingTouch = false
            }
        }

        suspend fun flingOffset(
            animatable: Animatable<Float, AnimationVector1D>,
            startValue: Float,
            initialVelocity: Float,
            maxOffset: Float,
            onUpdate: (Float) -> Unit,
        ) {
            if (maxOffset <= 0f || abs(initialVelocity) < 20f) {
                return
            }
            animatable.stop()
            animatable.updateBounds(0f, maxOffset)
            animatable.snapTo(startValue)
            try {
                animatable.animateDecay(initialVelocity, flingDecay) {
                    onUpdate(value.coerceIn(0f, maxOffset))
                }
            } catch (_: Throwable) {
            }
        }

        fun canConsumeVerticalDrag(deltaY: Float): Boolean {
            return when {
                deltaY < 0f -> verticalOffsetPx < maxVerticalOffsetPx
                deltaY > 0f -> verticalOffsetPx > 0f
                else -> false
            }
        }

        fun canConsumeHorizontalDrag(deltaX: Float): Boolean {
            return when {
                deltaX < 0f -> horizontalOffsetPx < maxHorizontalOffsetPx
                deltaX > 0f -> horizontalOffsetPx > 0f
                else -> false
            }
        }

        fun isVerticalDominantGesture(delta: Offset): Boolean {
            return verticalOnlyScroll || abs(delta.y) >= abs(delta.x)
        }

        fun shouldClaimDrag(totalDelta: Offset): Boolean {
            val canScrollVertically = canConsumeVerticalDrag(totalDelta.y)
            if (verticalOnlyScroll) {
                return canScrollVertically
            }
            val canScrollHorizontally = canConsumeHorizontalDrag(totalDelta.x)
            return if (isVerticalDominantGesture(totalDelta)) {
                canScrollVertically
            } else {
                canScrollHorizontally || canScrollVertically
            }
        }

        fun shouldYieldDragToParent(delta: Offset): Boolean {
            if (verticalOnlyScroll) {
                return !canConsumeVerticalDrag(delta.y)
            }
            return if (isVerticalDominantGesture(delta)) {
                !canConsumeVerticalDrag(delta.y)
            } else {
                !canConsumeHorizontalDrag(delta.x) && !canConsumeVerticalDrag(delta.y)
            }
        }

        fun updateOffsets(deltaX: Float, deltaY: Float): Boolean {
            val previousVerticalOffset = verticalOffsetPx
            val previousHorizontalOffset = horizontalOffsetPx
            verticalOffsetPx = (verticalOffsetPx - deltaY).coerceIn(0f, maxVerticalOffsetPx)
            if (!verticalOnlyScroll) {
                horizontalOffsetPx = (horizontalOffsetPx - deltaX).coerceIn(0f, maxHorizontalOffsetPx)
            }
            if (!isProgrammaticScroll) {
                autoScrollEnabled = verticalOffsetPx >= (maxVerticalOffsetPx - 80f)
            }
            return previousVerticalOffset != verticalOffsetPx || previousHorizontalOffset != horizontalOffsetPx
        }

        fun requestPointerFocus() {
            hostView.requestFocus()
            focusRequester.requestFocus()
            scope.launch {
                verticalFlingAnim.stop()
                horizontalFlingAnim.stop()
            }
        }

        fun startGestureCapture() {
            isHandlingTouch = true
            hostView.parent?.requestDisallowInterceptTouchEvent(true)
            scope.launch {
                verticalFlingAnim.stop()
                horizontalFlingAnim.stop()
            }
        }

        fun stopGestureCapture() {
            isHandlingTouch = false
            hostView.parent?.requestDisallowInterceptTouchEvent(false)
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(with(density) { actualViewportHeightPx.toDp() })
                    .onGloballyPositioned { coordinates ->
                        canvasBoundsInWindow = coordinates.boundsInWindow()
                    }
                    .focusRequester(focusRequester)
                    .focusable()
                    .pointerInput(selectionEnabled, codeSelectionInstructions, activeSelectionState, nodeIndex) {
                        if (!selectionEnabled) return@pointerInput
                        val state = activeSelectionState ?: return@pointerInput
                        awaitEachGesture {
                            val down = awaitPointerEvent(PointerEventPass.Main).changes.first()
                            val downPosition = down.position
                            val selection = state.selection
                            val activeHandle =
                                if (selection != null) {
                                    selectionHandleAt(
                                        selection = selection,
                                        nodeIndex = nodeIndex,
                                        instructions = codeSelectionInstructions,
                                        position = downPosition,
                                        radiusPx = handleTouchRadiusPx,
                                        handleRadiusPx = handleRadiusPx,
                                    )
                                } else {
                                    null
                                }

                            if (activeHandle == null || selection == null) {
                                return@awaitEachGesture
                            }

                            down.consume()
                            val downBounds = canvasBoundsInWindow ?: return@awaitEachGesture
                            val downPositionInWindow =
                                Offset(
                                    x = downBounds.left + downPosition.x,
                                    y = downBounds.top + downPosition.y,
                                )
                            val initialPoint =
                                when (activeHandle) {
                                    TextSelectionHandle.START -> selection.start
                                    TextSelectionHandle.END -> selection.end
                                }
                            state.dragMagnifier =
                                TextSelectionMagnifier(
                                    hostNodeIndex = nodeIndex,
                                    handle = activeHandle,
                                    position = downPosition,
                                    positionInWindow = downPositionInWindow,
                                    point = initialPoint,
                                )
                            autoScrollController?.reset()
                            try {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes.firstOrNull() ?: break
                                    if (!change.pressed) break
                                    val changeBounds = canvasBoundsInWindow ?: break
                                    val changePositionInWindow =
                                        Offset(
                                            x = changeBounds.left + change.position.x,
                                            y = changeBounds.top + change.position.y,
                                        )
                                    val currentMagnifier = state.dragMagnifier
                                    if (currentMagnifier?.hostNodeIndex == nodeIndex) {
                                        state.dragMagnifier =
                                            currentMagnifier.copy(
                                                position = change.position,
                                                positionInWindow = changePositionInWindow,
                                            )
                                    }
                                    val point = state.findPointInWindow(changePositionInWindow)
                                    if (point != null) {
                                        val currentSelection = state.selection ?: selection
                                        val updatedSelection =
                                            when (activeHandle) {
                                                TextSelectionHandle.START -> currentSelection.copy(start = point)
                                                TextSelectionHandle.END -> currentSelection.copy(end = point)
                                            }
                                        if (updatedSelection != currentSelection) {
                                            state.selection = updatedSelection
                                        }
                                        state.dragMagnifier =
                                            TextSelectionMagnifier(
                                                hostNodeIndex = nodeIndex,
                                                handle = activeHandle,
                                                position = change.position,
                                                positionInWindow = changePositionInWindow,
                                                point = point,
                                            )
                                    }
                                    change.consume()

                                    val magnifier = state.dragMagnifier
                                    if (magnifier?.hostNodeIndex != nodeIndex) break
                                    val controller = autoScrollController
                                    if (controller != null && controller.scrollByEdge(magnifier.positionInWindow)) {
                                        val point = state.findPointInWindow(magnifier.positionInWindow)
                                        val currentSelection = state.selection ?: selection
                                        if (point != null) {
                                            val updatedSelection =
                                                when (magnifier.handle) {
                                                    TextSelectionHandle.START -> currentSelection.copy(start = point)
                                                    TextSelectionHandle.END -> currentSelection.copy(end = point)
                                                }
                                            if (updatedSelection != currentSelection) {
                                                state.selection = updatedSelection
                                            }
                                            val bounds = canvasBoundsInWindow
                                            if (bounds != null) {
                                                state.dragMagnifier =
                                                    magnifier.copy(
                                                        position = magnifier.positionInWindow - Offset(bounds.left, bounds.top),
                                                        point = point,
                                                    )
                                            }
                                        }
                                    }
                                }
                            } finally {
                                autoScrollController?.reset()
                                state.dragMagnifier = null
                            }
                        }
                    }
                    .pointerInput(autoWrapEnabled, maxHorizontalOffsetPx, maxVerticalOffsetPx) {
                        if (maxHorizontalOffsetPx <= 0f && maxVerticalOffsetPx <= 0f) {
                            return@pointerInput
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val activeSelection = activeSelectionState?.selection
                            val downHitsSelectionHandle =
                                if (selectionEnabled && activeSelection != null) {
                                    selectionHandleAt(
                                        selection = activeSelection,
                                        nodeIndex = nodeIndex,
                                        instructions = codeSelectionInstructions,
                                        position = down.position,
                                        radiusPx = handleTouchRadiusPx,
                                        handleRadiusPx = handleRadiusPx,
                                    ) != null
                                } else {
                                    false
                                }
                            if (downHitsSelectionHandle) {
                                return@awaitEachGesture
                            }
                            var activePointerId: PointerId = down.id
                            var gestureStartPosition = down.position
                            var lastPointerPosition = down.position
                            var hasDragged = false
                            var hasCapturedGesture = false
                            var yieldedGestureToParent = false
                            val velocityTracker =
                                VelocityTracker().apply {
                                    addPosition(down.uptimeMillis, down.position)
                                }

                            requestPointerFocus()

                            try {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val activeChange =
                                        event.changes.firstOrNull { it.id == activePointerId }
                                            ?: event.changes.firstOrNull { it.pressed }
                                            ?: event.changes.firstOrNull()
                                            ?: break

                                    if (activeChange.id != activePointerId) {
                                        activePointerId = activeChange.id
                                        gestureStartPosition = activeChange.position
                                        lastPointerPosition = activeChange.position
                                    }

                                    velocityTracker.addPosition(
                                        activeChange.uptimeMillis,
                                        activeChange.position,
                                    )

                                    if (!activeChange.pressed) {
                                        if (hasCapturedGesture) {
                                            activeChange.consume()
                                        }
                                        break
                                    }

                                    val delta = activeChange.position - lastPointerPosition
                                    if (!hasDragged) {
                                        val totalDelta = activeChange.position - gestureStartPosition
                                        val dragExceededTouchSlop =
                                            if (verticalOnlyScroll) {
                                                abs(totalDelta.y) > touchSlop
                                            } else {
                                                abs(totalDelta.x) > touchSlop || abs(totalDelta.y) > touchSlop
                                            }
                                        if (dragExceededTouchSlop) {
                                            if (shouldClaimDrag(totalDelta)) {
                                                hasDragged = true
                                                hasCapturedGesture = true
                                                startGestureCapture()
                                            } else {
                                                yieldedGestureToParent = true
                                                break
                                            }
                                        }
                                    }

                                    if (hasDragged && delta != Offset.Zero) {
                                        if (shouldYieldDragToParent(delta)) {
                                            yieldedGestureToParent = true
                                            break
                                        }
                                        val didScroll =
                                            updateOffsets(
                                                deltaX = if (verticalOnlyScroll) 0f else delta.x,
                                                deltaY = delta.y,
                                            )
                                        if (!didScroll) {
                                            yieldedGestureToParent = true
                                            break
                                        }
                                    }

                                    lastPointerPosition = activeChange.position
                                    if (hasCapturedGesture) {
                                        activeChange.consume()
                                    }
                                }
                            } finally {
                                stopGestureCapture()
                            }

                            if (hasDragged && hasCapturedGesture && !yieldedGestureToParent) {
                                val velocity = velocityTracker.calculateVelocity()
                                scope.launch {
                                    flingOffset(
                                        animatable = verticalFlingAnim,
                                        startValue = currentVerticalOffsetPx,
                                        initialVelocity = -velocity.y,
                                        maxOffset = currentMaxVerticalOffsetPx,
                                    ) { value ->
                                        verticalOffsetPx = value
                                        if (!isProgrammaticScroll) {
                                            autoScrollEnabled =
                                                value >= (currentMaxVerticalOffsetPx - 80f)
                                        }
                                    }
                                }
                                if (!verticalOnlyScroll) {
                                    scope.launch {
                                        flingOffset(
                                            animatable = horizontalFlingAnim,
                                            startValue = currentHorizontalOffsetPx,
                                            initialVelocity = -velocity.x,
                                            maxOffset = currentMaxHorizontalOffsetPx,
                                        ) { value ->
                                            horizontalOffsetPx = value
                                        }
                                    }
                                }
                            }
                        }
                    },
        ) {
            Canvas(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { clip = true },
            ) {
                drawIntoCanvas { canvas ->
                    val nativeCanvas = canvas.nativeCanvas
                    visibleLines.forEachIndexed lineLoop@ { relativeIndex, line ->
                        val lineIndex = visibleStartIndex + relativeIndex
                        val lineTopPx = lineTopsPx[lineIndex] - verticalOffsetPx
                        val lineBlockHeightPx = lineBlockHeightsPx[lineIndex]
                        if (lineTopPx + lineBlockHeightPx < 0f || lineTopPx > size.height) {
                            return@lineLoop
                        }

                        line.rows.forEachIndexed rowLoop@ { rowIndex, row ->
                            val rowTopPx = lineTopPx + rowIndex * metrics.lineHeight
                            val rowBottomPx = rowTopPx + metrics.lineHeight
                            if (rowBottomPx < 0f || rowTopPx > size.height) {
                                return@rowLoop
                            }
                            drawCodeBlockRow(
                                canvas = nativeCanvas,
                                text = line.text,
                                row = row,
                                colorRanges = line.colorRanges,
                                defaultColorArgb = defaultTextColorArgb,
                                textPaint = textPaint,
                                systemGlyphPaint = systemGlyphPaint,
                                charWidthPx = metrics.charWidth,
                                startX = gutterWidthPx + gutterToCodeGapPx - horizontalOffsetPx,
                                baseline = rowTopPx + metrics.baseline,
                            )
                        }
                    }
                }

                drawRect(
                    color = Color(0xFF252526),
                    size =
                        androidx.compose.ui.geometry.Size(
                            width = gutterWidthPx,
                            height = size.height.coerceAtLeast(totalContentHeightPx),
                        ),
                )

                drawIntoCanvas { canvas ->
                    val nativeCanvas = canvas.nativeCanvas
                    visibleLines.forEachIndexed lineNumberLoop@ { relativeIndex, _ ->
                        val lineIndex = visibleStartIndex + relativeIndex
                        val lineTopPx = lineTopsPx[lineIndex] - verticalOffsetPx
                        val lineBlockHeightPx = lineBlockHeightsPx[lineIndex]
                        if (lineTopPx + lineBlockHeightPx < 0f || lineTopPx > size.height) {
                            return@lineNumberLoop
                        }

                        val label = (lineIndex + 1).toString().padStart(digits)
                        val lineNumberX =
                            gutterWidthPx -
                                gutterHorizontalPaddingPx -
                                lineNumberEndPaddingPx -
                                label.length * metrics.charWidth
                        val lineNumberBaseline =
                            lineTopPx +
                                (lineBlockHeightPx - metrics.lineHeight) / 2f +
                                metrics.baseline
                        nativeCanvas.drawText(label, lineNumberX, lineNumberBaseline, lineNumberPaint)
                    }
                }

                val selectionForInstruction = nodeSelection
                if (selectionForInstruction != null) {
                    drawIntoCanvas { canvas ->
                        val nativeCanvas = canvas.nativeCanvas
                        val selectionPath = android.graphics.Path()
                        codeSelectionInstructions.forEachIndexed { instructionIndex, instruction ->
                            val textLayout = instruction as? DrawInstruction.TextLayout ?: return@forEachIndexed
                            val selectionRange =
                                selectedRangeForInstruction(
                                    selection = selectionForInstruction,
                                    instructionIndex = instructionIndex,
                                    textLength = textLayout.layout.text.length,
                                )
                            val hasSelectionHandle =
                                selectionForInstruction.startHandle?.instructionIndex == instructionIndex ||
                                    selectionForInstruction.endHandle?.instructionIndex == instructionIndex
                            if (selectionRange == null && !hasSelectionHandle) {
                                return@forEachIndexed
                            }

                            nativeCanvas.save()
                            nativeCanvas.clipRect(
                                gutterWidthPx + gutterToCodeGapPx,
                                0f,
                                size.width,
                                size.height,
                            )
                            nativeCanvas.translate(textLayout.x, textLayout.y)
                            if (selectionRange != null) {
                                selectionPath.reset()
                                textLayout.layout.getSelectionPath(
                                    selectionRange.start,
                                    selectionRange.end,
                                    selectionPath,
                                )
                                nativeCanvas.drawPath(selectionPath, selectionPaint)
                            }
                            val selectionStartPoint = selectionForInstruction.startHandle
                            if (
                                selectionStartPoint != null &&
                                    selectionStartPoint.instructionIndex == instructionIndex
                            ) {
                                drawCanvasTextSelectionHandle(
                                    canvas = nativeCanvas,
                                    instruction =
                                        textLayout.copy(
                                            x = 0f,
                                            y = 0f,
                                        ),
                                    offset = selectionStartPoint.offset,
                                    cursorPaint = cursorPaint,
                                    handlePaint = handlePaint,
                                    handleRadiusPx = handleRadiusPx,
                                )
                            }
                            val selectionEndPoint = selectionForInstruction.endHandle
                            if (
                                selectionEndPoint != null &&
                                    selectionEndPoint.instructionIndex == instructionIndex
                            ) {
                                drawCanvasTextSelectionHandle(
                                    canvas = nativeCanvas,
                                    instruction =
                                        textLayout.copy(
                                            x = 0f,
                                            y = 0f,
                                        ),
                                    offset = selectionEndPoint.offset,
                                    cursorPaint = cursorPaint,
                                    handlePaint = handlePaint,
                                    handleRadiusPx = handleRadiusPx,
                                )
                            }
                            nativeCanvas.restore()
                        }
                    }
                }
            }
            val magnifier = nodeMagnifier
            val magnifierInstruction =
                magnifier?.let {
                    activeSelectionState?.textLayoutForPoint(it.point)
                }
            if (magnifier != null && magnifierInstruction != null) {
                Popup(
                    alignment = Alignment.TopStart,
                    offset =
                        IntOffset(
                            x = (magnifier.position.x - magnifierWidthPx / 2f).roundToInt(),
                            y = (magnifier.position.y - magnifierHeightPx - magnifierMarginPx * 3f).roundToInt(),
                        ),
                    properties =
                        PopupProperties(
                            focusable = false,
                            clippingEnabled = false,
                        ),
                ) {
                    Canvas(
                        modifier = Modifier.size(magnifierWidthDp, magnifierHeightDp)
                    ) {
                        drawIntoCanvas { canvas ->
                            drawCanvasTextSelectionMagnifier(
                                canvas = canvas.nativeCanvas,
                                layout = magnifierInstruction.layout,
                                magnifier = magnifier,
                                bubblePaint = magnifierBubblePaint,
                                borderPaint = magnifierBorderPaint,
                                textPaint = magnifierTextPaint,
                                cursorPaint = cursorPaint,
                                bubbleWidthPx = size.width,
                                bubbleHeightPx = size.height,
                                marginPx = magnifierMarginPx,
                            )
                        }
                    }
                }
            }
            if (toolbarNodeSelection != null) {
                val selectionBounds =
                    selectionVisualBounds(
                        instructions = codeSelectionInstructions,
                        selection = toolbarNodeSelection,
                        handleRadiusPx = handleRadiusPx,
                    )
                Surface(
                    modifier =
                        Modifier
                            .offset {
                                val toolbarWidthPx =
                                    if (toolbarSize.width > 0) toolbarSize.width.toFloat() else toolbarEstimatedWidthPx
                                val toolbarHeightPx =
                                    if (toolbarSize.height > 0) toolbarSize.height.toFloat() else toolbarEstimatedHeightPx
                                placementForToolbar(
                                    selectionBounds = selectionBounds,
                                    toolbarWidthPx = toolbarWidthPx,
                                    toolbarHeightPx = toolbarHeightPx,
                                    canvasWidthPx = viewportWidthPx.toFloat(),
                                    canvasHeightPx = actualViewportHeightPx,
                                    gapPx = toolbarGapPx,
                                    edgePaddingPx = toolbarEdgePaddingPx,
                                )
                            }
                            .onGloballyPositioned { coordinates ->
                                toolbarSize = coordinates.size
                            },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                    tonalElevation = 3.dp,
                    shadowElevation = 4.dp,
                ) {
                    Row(
                        modifier =
                            Modifier
                                .height(30.dp)
                                .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .height(24.dp)
                                    .clickable {
                                        val state = activeSelectionState
                                        if (state != null) {
                                            clipboardManager.setText(AnnotatedString(state.selectedText()))
                                            state.dismissSelection()
                                        }
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.message_copied_to_clipboard),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                    .padding(horizontal = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = context.getString(R.string.copy),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                            )
                        }
                        Box(
                            modifier =
                                Modifier
                                    .height(18.dp)
                                    .width(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                        )
                        Box(
                            modifier =
                                Modifier
                                    .height(24.dp)
                                    .clickable {
                                        activeSelectionState?.dismissSelection()
                                    }
                                    .padding(horizontal = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = context.getString(R.string.done),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun createCodeBlockSelectionLayout(
    text: String,
    paint: TextPaint,
    width: Int,
): StaticLayout {
    @Suppress("DEPRECATION")
    return StaticLayout(
        text,
        paint,
        width.coerceAtLeast(1),
        android.text.Layout.Alignment.ALIGN_NORMAL,
        1f,
        0f,
        false,
    )
}

private fun buildCodeBlockSelectionInstructions(
    lineRenderData: List<CodeBlockLineRenderData>,
    lineLayouts: List<StaticLayout>,
    lineTopsPx: FloatArray,
    lineBlockHeightsPx: FloatArray,
    verticalOffsetPx: Float,
    horizontalOffsetPx: Float,
    gutterWidthPx: Float,
    gutterToCodeGapPx: Float,
    viewportWidthPx: Int,
): List<DrawInstruction> {
    if (lineRenderData.isEmpty()) return emptyList()

    val codeStartX = gutterWidthPx + gutterToCodeGapPx - horizontalOffsetPx
    val hitLeft = gutterWidthPx + gutterToCodeGapPx
    val hitRight = viewportWidthPx.toFloat().coerceAtLeast(hitLeft)

    return lineRenderData.mapIndexed { index, line ->
        val lineTop = lineTopsPx[index] - verticalOffsetPx
        val lineHeight = lineBlockHeightsPx[index].coerceAtLeast(1f)
        DrawInstruction.TextLayout(
            layout = lineLayouts[index],
            x = codeStartX,
            y = lineTop,
            text = line.text,
            copySeparatorBefore = if (index == 0) TextCopySeparator.NONE else TextCopySeparator.NEWLINE,
            selectionHitBounds =
                RectF(
                    hitLeft,
                    lineTop,
                    hitRight,
                    lineTop + lineHeight,
                ),
        )
    }
}

private fun buildCodeBlockLineRenderData(
    line: AnnotatedString,
    autoWrapEnabled: Boolean,
    maxColumns: Int,
    defaultTextColorArgb: Int,
): CodeBlockLineRenderData {
    val text = line.text
    val rows = wrapCodeBlockRows(text = text, autoWrapEnabled = autoWrapEnabled, maxColumns = maxColumns)
    return CodeBlockLineRenderData(
        text = text,
        colorRanges = buildCodeBlockColorRanges(line, defaultTextColorArgb),
        rows = rows,
        maxCells = rows.maxOfOrNull { it.cells }?.coerceAtLeast(1) ?: 1,
    )
}

private fun buildCodeBlockColorRanges(
    line: AnnotatedString,
    defaultTextColorArgb: Int,
): List<CodeBlockColorRange> {
    if (line.spanStyles.isEmpty()) {
        return listOf(
            CodeBlockColorRange(
                start = 0,
                end = line.text.length,
                colorArgb = defaultTextColorArgb,
            ),
        )
    }

    val ranges =
        line.spanStyles
            .mapNotNull { range ->
                val color = range.item.color
                if (color == Color.Unspecified || range.start >= range.end) {
                    null
                } else {
                    CodeBlockColorRange(
                        start = range.start,
                        end = range.end,
                        colorArgb = color.toArgb(),
                    )
                }
            }
            .sortedBy { it.start }

    return if (ranges.isEmpty()) {
        listOf(
            CodeBlockColorRange(
                start = 0,
                end = line.text.length,
                colorArgb = defaultTextColorArgb,
            ),
        )
    } else {
        ranges
    }
}

private fun wrapCodeBlockRows(
    text: String,
    autoWrapEnabled: Boolean,
    maxColumns: Int,
): List<CodeBlockWrappedRow> {
    if (!autoWrapEnabled) {
        return listOf(
            CodeBlockWrappedRow(
                start = 0,
                end = text.length,
                cells = measureCodeBlockCells(text),
            ),
        )
    }
    if (text.isEmpty()) {
        return listOf(CodeBlockWrappedRow(start = 0, end = 0, cells = 1))
    }

    val rows = ArrayList<CodeBlockWrappedRow>()
    val safeMaxColumns = maxColumns.coerceAtLeast(1)
    var rowStart = 0
    var rowCells = 0
    var offset = 0

    while (offset < text.length) {
        val nextOffset = editorNextSymbolOffset(text, offset, text.length)
        val cellWidth = editorCellWidth(text, offset, text.length).coerceAtLeast(1)
        if (rowCells > 0 && rowCells + cellWidth > safeMaxColumns) {
            rows += CodeBlockWrappedRow(start = rowStart, end = offset, cells = rowCells.coerceAtLeast(1))
            rowStart = offset
            rowCells = 0
        }
        rowCells += cellWidth
        offset = nextOffset
    }

    rows += CodeBlockWrappedRow(start = rowStart, end = text.length, cells = rowCells.coerceAtLeast(1))
    return rows
}

private fun measureCodeBlockCells(text: String): Int {
    if (text.isEmpty()) {
        return 1
    }

    var cells = 0
    var offset = 0
    while (offset < text.length) {
        cells += editorCellWidth(text, offset, text.length).coerceAtLeast(1)
        offset = editorNextSymbolOffset(text, offset, text.length)
    }
    return cells.coerceAtLeast(1)
}

private fun findFirstVisibleCodeLine(
    lineTopsPx: FloatArray,
    lineHeightsPx: FloatArray,
    targetTopPx: Float,
): Int {
    var low = 0
    var high = lineTopsPx.lastIndex
    var result = lineTopsPx.size
    while (low <= high) {
        val mid = (low + high) ushr 1
        val bottom = lineTopsPx[mid] + lineHeightsPx[mid]
        if (bottom >= targetTopPx) {
            result = mid
            high = mid - 1
        } else {
            low = mid + 1
        }
    }
    return result
}

private fun findLastVisibleCodeLine(
    lineTopsPx: FloatArray,
    targetBottomPx: Float,
): Int {
    var low = 0
    var high = lineTopsPx.lastIndex
    var result = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (lineTopsPx[mid] <= targetBottomPx) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return result
}

private fun drawCodeBlockRow(
    canvas: AndroidCanvas,
    text: String,
    row: CodeBlockWrappedRow,
    colorRanges: List<CodeBlockColorRange>,
    defaultColorArgb: Int,
    textPaint: Paint,
    systemGlyphPaint: Paint,
    charWidthPx: Float,
    startX: Float,
    baseline: Float,
) {
    var x = startX
    var offset = row.start
    var runStart = -1
    var runEnd = -1
    var runX = 0f
    var runColor = defaultColorArgb
    var rangeIndex = 0

    while (rangeIndex < colorRanges.size && colorRanges[rangeIndex].end <= row.start) {
        rangeIndex++
    }

    fun colorForOffset(targetOffset: Int): Int {
        while (rangeIndex < colorRanges.size && colorRanges[rangeIndex].end <= targetOffset) {
            rangeIndex++
        }
        val range = colorRanges.getOrNull(rangeIndex)
        return if (range != null && targetOffset >= range.start && targetOffset < range.end) {
            range.colorArgb
        } else {
            defaultColorArgb
        }
    }

    fun flushRun() {
        if (runStart < 0 || runEnd <= runStart) {
            return
        }
        textPaint.color = runColor
        canvas.drawText(text, runStart, runEnd, runX, baseline, textPaint)
        runStart = -1
        runEnd = -1
    }

    while (offset < row.end) {
        val nextOffset = editorNextSymbolOffset(text, offset, row.end)
        val cellWidth = editorCellWidth(text, offset, row.end).coerceAtLeast(1)
        val advance = charWidthPx * cellWidth

        if (text[offset] != '\t') {
            val usesSystemGlyphPaint =
                if (nextOffset == offset + 1) {
                    shouldRenderWithSystemGlyphs(text[offset].code)
                } else {
                    shouldRenderWithSystemGlyphs(text, offset, nextOffset)
                }
            val color = colorForOffset(offset)
            val canBatch =
                !usesSystemGlyphPaint &&
                    cellWidth == 1 &&
                    nextOffset == offset + 1

            if (canBatch) {
                if (runStart >= 0 && runColor == color && runEnd == offset) {
                    runEnd = nextOffset
                } else {
                    flushRun()
                    runStart = offset
                    runEnd = nextOffset
                    runX = x
                    runColor = color
                }
            } else {
                flushRun()
                val paint = if (usesSystemGlyphPaint) systemGlyphPaint else textPaint
                paint.color = color
                canvas.drawText(text, offset, nextOffset, x, baseline, paint)
            }
        } else {
            flushRun()
        }

        x += advance
        offset = nextOffset
    }

    flushRun()
}

private fun shouldRenderWithSystemGlyphs(codePoint: Int): Boolean {
    return codePoint in 0x1F000..0x1FAFF ||
        codePoint in 0x2600..0x27BF ||
        codePoint in 0x1F3FB..0x1F3FF ||
        codePoint in 0x1F1E6..0x1F1FF ||
        codePoint in 0xFE00..0xFE0F ||
        codePoint == 0x200D ||
        codePoint == 0x20E3
}

private fun shouldRenderWithSystemGlyphs(
    text: CharSequence,
    start: Int,
    end: Int,
): Boolean {
    var index = start.coerceAtLeast(0)
    val safeEnd = end.coerceAtMost(text.length)
    while (index < safeEnd) {
        val codePoint = Character.codePointAt(text, index)
        if (shouldRenderWithSystemGlyphs(codePoint)) {
            return true
        }
        index += Character.charCount(codePoint)
    }
    return false
}
