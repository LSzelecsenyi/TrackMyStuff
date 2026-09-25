package app.mymusclemap.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.ui.theme.AppTypeTokens
import kotlin.math.roundToInt

private val CalloutMaxWidth = 280.dp
private val CalloutPadding = 16.dp
private val SpotlightExtraRadius = 10.dp
private val SpotlightRectPadding = 10.dp
private val CaretWidth = 14.dp
private val CaretHeight = 8.dp
private val CalloutToHoleGap = 6.dp
private const val ScrimAlpha = 0.62f

sealed interface OnboardingSpotlightShape {
    data class Circle(val extraRadius: Dp = SpotlightExtraRadius) : OnboardingSpotlightShape
    data class RoundedRect(
        val extraPadding: Dp = SpotlightRectPadding,
        val cornerRadius: Dp = AppDimens.cornerSurface
    ) : OnboardingSpotlightShape
}

private sealed interface SpotlightHole {
    val top: Float
    val centerX: Float
    fun contains(point: Offset): Boolean

    data class Circle(val center: Offset, val radius: Float) : SpotlightHole {
        override val top: Float get() = center.y - radius
        override val centerX: Float get() = center.x
        override fun contains(point: Offset): Boolean = point.inCircle(center, radius)
    }

    data class RoundedRect(val rect: Rect, val cornerRadius: Float) : SpotlightHole {
        override val top: Float get() = rect.top
        override val centerX: Float get() = rect.center.x
        override fun contains(point: Offset): Boolean = rect.contains(point)
    }
}

@Composable
fun OnboardingWorkoutSpotlight(
    targetInRoot: Rect,
    onDismiss: () -> Unit,
    onTargetClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OnboardingSpotlight(
        targetInRoot = targetInRoot,
        shape = OnboardingSpotlightShape.Circle(),
        title = stringResource(R.string.onboarding_start_workout_title),
        body = stringResource(R.string.onboarding_start_workout_body),
        onDismiss = onDismiss,
        onTargetClick = onTargetClick,
        overlayTestTag = ONBOARDING_WORKOUT_SPOTLIGHT,
        calloutTestTag = ONBOARDING_WORKOUT_COACH,
        modifier = modifier
    )
}

@Composable
fun OnboardingHeatmapSpotlight(
    targetInRoot: Rect,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    OnboardingSpotlight(
        targetInRoot = targetInRoot,
        shape = OnboardingSpotlightShape.RoundedRect(),
        title = stringResource(R.string.onboarding_heatmap_title),
        body = stringResource(R.string.onboarding_heatmap_body),
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        overlayTestTag = ONBOARDING_HEATMAP_SPOTLIGHT,
        calloutTestTag = ONBOARDING_HEATMAP_COACH,
        modifier = modifier
    )
}

@Composable
fun OnboardingCalendarWeightSpotlight(
    dayInRoot: Rect,
    calendarInRoot: Rect,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val useDay = dayInRoot.width > 1f && dayInRoot.height > 1f
    OnboardingSpotlight(
        targetInRoot = if (useDay) dayInRoot else calendarInRoot,
        shape = if (useDay) {
            OnboardingSpotlightShape.Circle(extraRadius = 8.dp)
        } else {
            OnboardingSpotlightShape.RoundedRect()
        },
        title = stringResource(R.string.onboarding_weight_calendar_title),
        body = stringResource(R.string.onboarding_weight_calendar_body),
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        overlayTestTag = ONBOARDING_CALENDAR_WEIGHT_SPOTLIGHT,
        calloutTestTag = ONBOARDING_CALENDAR_WEIGHT_COACH,
        modifier = modifier
    )
}

@Composable
fun OnboardingChartSpotlight(
    targetInRoot: Rect,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    OnboardingSpotlight(
        targetInRoot = targetInRoot,
        shape = OnboardingSpotlightShape.RoundedRect(),
        title = stringResource(R.string.onboarding_chart_title),
        body = stringResource(R.string.onboarding_chart_body),
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        overlayTestTag = ONBOARDING_CHART_SPOTLIGHT,
        calloutTestTag = ONBOARDING_CHART_COACH,
        modifier = modifier
    )
}

@Composable
fun OnboardingCalendarHistorySpotlight(
    targetInRoot: Rect,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    OnboardingSpotlight(
        targetInRoot = targetInRoot,
        shape = OnboardingSpotlightShape.RoundedRect(),
        title = stringResource(R.string.onboarding_calendar_title),
        body = stringResource(R.string.onboarding_calendar_body),
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        overlayTestTag = ONBOARDING_CALENDAR_HISTORY_SPOTLIGHT,
        calloutTestTag = ONBOARDING_CALENDAR_COACH,
        modifier = modifier
    )
}

@Composable
fun OnboardingSpotlight(
    targetInRoot: Rect,
    shape: OnboardingSpotlightShape,
    title: String,
    body: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onTargetClick: (() -> Unit)? = null,
    onConfirm: (() -> Unit)? = null,
    overlayTestTag: String = ONBOARDING_WORKOUT_SPOTLIGHT,
    calloutTestTag: String = ONBOARDING_WORKOUT_COACH
) {
    BackHandler(onBack = onDismiss)
    val density = LocalDensity.current
    val extraPx = with(density) {
        when (shape) {
            is OnboardingSpotlightShape.Circle -> shape.extraRadius.toPx()
            is OnboardingSpotlightShape.RoundedRect -> shape.extraPadding.toPx()
        }
    }
    val cornerPx = with(density) {
        when (shape) {
            is OnboardingSpotlightShape.Circle -> AppDimens.cornerSurface.toPx()
            is OnboardingSpotlightShape.RoundedRect -> shape.cornerRadius.toPx()
        }
    }
    val edgePadding = with(density) { AppDimens.screenPadding.toPx() }
    val caretHeightPx = with(density) { CaretHeight.toPx() }
    val caretWidthPx = with(density) { CaretWidth.toPx() }
    val gapPx = with(density) { CalloutToHoleGap.toPx() }
    val calloutFill = MaterialTheme.colorScheme.surface
    val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = ScrimAlpha)
    val currentTargetClick by rememberUpdatedState(onTargetClick)
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    var overlaySize by remember { mutableStateOf(Size.Zero) }
    var calloutSize by remember { mutableStateOf(IntSize.Zero) }
    val hasTarget = targetInRoot.width > 1f && targetInRoot.height > 1f
    val localTarget = if (hasTarget) {
        Rect(
            left = targetInRoot.left - overlayOrigin.x,
            top = targetInRoot.top - overlayOrigin.y,
            right = targetInRoot.right - overlayOrigin.x,
            bottom = targetInRoot.bottom - overlayOrigin.y
        )
    } else {
        Rect.Zero
    }
    val hole = if (hasTarget) {
        when (shape) {
            is OnboardingSpotlightShape.Circle -> {
                val radius = localTarget.maxDimension / 2f + extraPx
                SpotlightHole.Circle(localTarget.center, radius)
            }
            is OnboardingSpotlightShape.RoundedRect -> {
                SpotlightHole.RoundedRect(localTarget.inflate(extraPx), cornerPx)
            }
        }
    } else {
        null
    }
    val calloutOffset = if (hole != null && calloutSize.width > 0 && overlaySize.width > 0f) {
        val maxX = (overlaySize.width - edgePadding - calloutSize.width).coerceAtLeast(edgePadding)
        val x = (hole.centerX - calloutSize.width / 2f).coerceIn(edgePadding, maxX)
        val y = (hole.top - gapPx - calloutSize.height).coerceAtLeast(edgePadding)
        IntOffset(x.roundToInt(), y.roundToInt())
    } else {
        IntOffset.Zero
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(overlayTestTag)
            .onGloballyPositioned { coordinates ->
                overlayOrigin = coordinates.positionInRoot()
                overlaySize = Size(
                    coordinates.size.width.toFloat(),
                    coordinates.size.height.toFloat()
                )
            }
            .pointerInput(hole, hasTarget, currentTargetClick) {
                detectTapGestures { tap ->
                    val clickTarget = currentTargetClick
                    if (clickTarget != null && hole != null && hole.contains(tap)) {
                        clickTarget()
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val path = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                when (val spotlight = hole) {
                    is SpotlightHole.Circle -> addOval(
                        Rect(
                            left = spotlight.center.x - spotlight.radius,
                            top = spotlight.center.y - spotlight.radius,
                            right = spotlight.center.x + spotlight.radius,
                            bottom = spotlight.center.y + spotlight.radius
                        )
                    )
                    is SpotlightHole.RoundedRect -> addRoundRect(
                        RoundRect(
                            rect = spotlight.rect,
                            cornerRadius = CornerRadius(spotlight.cornerRadius, spotlight.cornerRadius)
                        )
                    )
                    null -> Unit
                }
                if (hole != null && calloutSize.width > 0) {
                    val cardHeight = calloutSize.height - caretHeightPx
                    addRoundRect(
                        RoundRect(
                            left = calloutOffset.x.toFloat(),
                            top = calloutOffset.y.toFloat(),
                            right = calloutOffset.x + calloutSize.width.toFloat(),
                            bottom = calloutOffset.y + cardHeight,
                            cornerRadius = CornerRadius(cornerPx, cornerPx)
                        )
                    )
                    val caretLeft = calloutOffset.x + (calloutSize.width - caretWidthPx) / 2f
                    val caretTop = calloutOffset.y + cardHeight
                    moveTo(caretLeft, caretTop)
                    lineTo(caretLeft + caretWidthPx, caretTop)
                    lineTo(caretLeft + caretWidthPx / 2f, caretTop + caretHeightPx)
                    close()
                }
            }
            drawPath(path, scrim)
            when (val spotlight = hole) {
                is SpotlightHole.Circle -> {
                    val glowRadius = spotlight.radius * 1.7f
                    drawCircle(
                        brush = Brush.radialGradient(
                            0.00f to Color.Transparent,
                            0.58f to Color.Transparent,
                            0.72f to Color.White.copy(alpha = 0.42f),
                            1.00f to Color.Transparent,
                            center = spotlight.center,
                            radius = glowRadius
                        ),
                        radius = glowRadius,
                        center = spotlight.center
                    )
                }
                is SpotlightHole.RoundedRect -> {
                    val glow = spotlight.rect.inflate(6f)
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.28f),
                        topLeft = Offset(glow.left, glow.top),
                        size = Size(glow.width, glow.height),
                        cornerRadius = CornerRadius(
                            spotlight.cornerRadius + 4f,
                            spotlight.cornerRadius + 4f
                        ),
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
                null -> Unit
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .then(
                    if (hole != null && calloutSize.width > 0) {
                        Modifier.offset { calloutOffset }
                    } else {
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = AppDimens.navBarHeight + AppDimens.itemGap)
                            .padding(horizontal = AppDimens.screenPadding)
                    }
                )
                .widthIn(max = CalloutMaxWidth)
                .onSizeChanged { calloutSize = it }
        ) {
            Surface(
                modifier = Modifier.testTag(calloutTestTag),
                shape = AppShapeTokens.surface,
                color = calloutFill,
                tonalElevation = 2.dp,
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(CalloutPadding)) {
                    Text(
                        text = title,
                        style = AppTypeTokens.sectionTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(Modifier.height(AppDimens.headerStackGap))
                    Text(
                        text = body,
                        style = AppTypeTokens.statSecondary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (onConfirm != null) {
                        Spacer(Modifier.height(AppDimens.itemGap))
                        Button(
                            onClick = onConfirm,
                            shape = AppShapeTokens.button,
                            modifier = Modifier.defaultMinSize(minHeight = AppDimens.minTouch)
                        ) {
                            Text(stringResource(R.string.onboarding_got_it))
                        }
                    }
                }
            }
            Canvas(modifier = Modifier.size(width = CaretWidth, height = CaretHeight)) {
                val caret = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                }
                drawPath(caret, calloutFill)
            }
        }
    }
}

private fun Offset.inCircle(center: Offset, radius: Float): Boolean {
    val dx = x - center.x
    val dy = y - center.y
    return dx * dx + dy * dy <= radius * radius
}
