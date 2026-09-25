package app.mymusclemap.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
private val CaretWidth = 14.dp
private val CaretHeight = 8.dp
private val CalloutToHoleGap = 6.dp
private const val ScrimAlpha = 0.62f

@Composable
fun OnboardingWorkoutSpotlight(
    targetInRoot: Rect,
    onDismiss: () -> Unit,
    onTargetClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onDismiss)
    val density = LocalDensity.current
    val extraRadius = with(density) { SpotlightExtraRadius.toPx() }
    val edgePadding = with(density) { AppDimens.screenPadding.toPx() }
    val caretHeightPx = with(density) { CaretHeight.toPx() }
    val caretWidthPx = with(density) { CaretWidth.toPx() }
    val gapPx = with(density) { CalloutToHoleGap.toPx() }
    val cornerPx = with(density) { AppDimens.cornerSurface.toPx() }
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
    val holeRadius = if (hasTarget) localTarget.maxDimension / 2f + extraRadius else 0f
    val holeCenter = localTarget.center
    val calloutOffset = if (hasTarget && calloutSize.width > 0 && overlaySize.width > 0f) {
        val maxX = (overlaySize.width - edgePadding - calloutSize.width).coerceAtLeast(edgePadding)
        val x = (holeCenter.x - calloutSize.width / 2f).coerceIn(edgePadding, maxX)
        val holeTop = holeCenter.y - holeRadius
        val y = (holeTop - gapPx - calloutSize.height).coerceAtLeast(edgePadding)
        IntOffset(x.roundToInt(), y.roundToInt())
    } else {
        IntOffset.Zero
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(ONBOARDING_WORKOUT_SPOTLIGHT)
            .onGloballyPositioned { coordinates ->
                overlayOrigin = coordinates.positionInRoot()
                overlaySize = Size(
                    coordinates.size.width.toFloat(),
                    coordinates.size.height.toFloat()
                )
            }
            .pointerInput(localTarget, holeRadius, hasTarget) {
                detectTapGestures { tap ->
                    if (hasTarget && tap.inCircle(holeCenter, holeRadius)) {
                        currentTargetClick()
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val path = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                if (hasTarget) {
                    addOval(
                        Rect(
                            left = holeCenter.x - holeRadius,
                            top = holeCenter.y - holeRadius,
                            right = holeCenter.x + holeRadius,
                            bottom = holeCenter.y + holeRadius
                        )
                    )
                }
                if (hasTarget && calloutSize.width > 0) {
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
            if (hasTarget) {
                val glowRadius = holeRadius * 1.7f
                drawCircle(
                    brush = Brush.radialGradient(
                        0.00f to Color.Transparent,
                        0.58f to Color.Transparent,
                        0.72f to Color.White.copy(alpha = 0.42f),
                        1.00f to Color.Transparent,
                        center = holeCenter,
                        radius = glowRadius
                    ),
                    radius = glowRadius,
                    center = holeCenter
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .then(
                    if (hasTarget && calloutSize.width > 0) {
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
                modifier = Modifier.testTag(ONBOARDING_WORKOUT_COACH),
                shape = AppShapeTokens.surface,
                color = calloutFill,
                tonalElevation = 2.dp,
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(CalloutPadding)) {
                    Text(
                        text = stringResource(R.string.onboarding_start_workout_title),
                        style = AppTypeTokens.sectionTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(Modifier.height(AppDimens.headerStackGap))
                    Text(
                        text = stringResource(R.string.onboarding_start_workout_body),
                        style = AppTypeTokens.statSecondary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
