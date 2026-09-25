package app.mymusclemap.ui.components.musclemap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import androidx.compose.ui.unit.isSpecified
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.ui.components.musclemap.artwork.BodyMusclesArtwork
import app.mymusclemap.ui.components.musclemap.artwork.BodyMusclesCatalog
import app.mymusclemap.ui.components.musclemap.artwork.MuscleArtworkRegion
import app.mymusclemap.ui.components.musclemap.artwork.MuscleMapView
import kotlin.math.min

data class ParsedMuscleRegion(
    val region: MuscleArtworkRegion,
    val path: Path,
    val bounds: Rect
)

@Composable
fun rememberParsedMuscleRegions(): List<ParsedMuscleRegion> {
    return remember { parseMuscleRegions() }
}

fun parseMuscleRegions(): List<ParsedMuscleRegion> {
    return BodyMusclesCatalog.regions.map { region ->
        val parser = PathParser()
        parser.parsePathString(region.pathData)
        val raw = parser.toPath()
        raw.fillType = PathFillType.NonZero
        val path = if (region.view == MuscleMapView.BACK) {
            Path().apply {
                fillType = PathFillType.NonZero
                addPath(raw, Offset(-BodyMusclesArtwork.BACK_OFFSET_X, 0f))
            }
        } else {
            raw
        }
        val androidPath = path.asAndroidPath()
        val rect = android.graphics.RectF()
        androidPath.computeBounds(rect, true)
        ParsedMuscleRegion(
            region = region,
            path = path,
            bounds = Rect(rect.left, rect.top, rect.right, rect.bottom)
        )
    }
}

@Composable
fun MuscleMap(
    fills: Map<MuscleGroup, Color>,
    unmappedFill: Color,
    outline: Color,
    mappedOutline: Color,
    selectedOutline: Color,
    selected: MuscleGroup?,
    onSelect: (MuscleGroup) -> Unit,
    contentDescription: String,
    accessibilityActions: List<Pair<String, MuscleGroup>> = emptyList(),
    modifier: Modifier = Modifier,
    interactive: Boolean = true
) {
    val parsed = rememberParsedMuscleRegions()
    val front = remember(parsed) {
        paintOrderedRegions(parsed.filter { it.region.view == MuscleMapView.FRONT })
    }
    val back = remember(parsed) {
        paintOrderedRegions(parsed.filter { it.region.view == MuscleMapView.BACK })
    }
    val semanticsModifier = Modifier.semantics {
        this.contentDescription = contentDescription
        if (accessibilityActions.isNotEmpty()) {
            customActions = accessibilityActions.map { (label, group) ->
                CustomAccessibilityAction(label) {
                    onSelect(group)
                    true
                }
            }
        }
    }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val availableWidth = if (constraints.hasBoundedWidth) maxWidth else 0.dp
        val pair = muscleMapPairLayout(
            availableWidth = availableWidth,
            gap = MUSCLE_MAP_FIGURE_GAP
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(pair.figureHeight)
                .then(semanticsModifier),
            horizontalArrangement = Arrangement.spacedBy(pair.gap, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Top
        ) {
            val figureModifier = Modifier
                .width(pair.figureWidth)
                .height(pair.figureHeight)
            MuscleFigure(
                regions = front,
                fills = fills,
                unmappedFill = unmappedFill,
                outline = outline,
                mappedOutline = mappedOutline,
                selectedOutline = selectedOutline,
                selected = selected,
                onSelect = onSelect,
                interactive = interactive,
                testTag = "muscle_map_front",
                modifier = figureModifier
            )
            MuscleFigure(
                regions = back,
                fills = fills,
                unmappedFill = unmappedFill,
                outline = outline,
                mappedOutline = mappedOutline,
                selectedOutline = selectedOutline,
                selected = selected,
                onSelect = onSelect,
                interactive = interactive,
                testTag = "muscle_map_back",
                modifier = figureModifier
            )
        }
    }
}

@Composable
private fun MuscleFigure(
    regions: List<ParsedMuscleRegion>,
    fills: Map<MuscleGroup, Color>,
    unmappedFill: Color,
    outline: Color,
    mappedOutline: Color,
    selectedOutline: Color,
    selected: MuscleGroup?,
    onSelect: (MuscleGroup) -> Unit,
    interactive: Boolean,
    testTag: String,
    modifier: Modifier
) {
    Canvas(
        modifier = modifier
            .testTag(testTag)
            .pointerInput(regions, interactive) {
                if (!interactive) return@pointerInput
                detectTapGestures { offset ->
                    val layout = figureLayout(size.width.toFloat(), size.height.toFloat())
                    val svg = Offset(
                        (offset.x - layout.offsetX) / layout.scale,
                        (offset.y - layout.offsetY) / layout.scale
                    )
                    hitTest(regions, svg)?.let(onSelect)
                }
            }
    ) {
        val layout = figureLayout(size.width, size.height)
        withTransform({
            translate(layout.offsetX, layout.offsetY)
            scale(scaleX = layout.scale, scaleY = layout.scale, pivot = Offset.Zero)
        }) {
            regions.forEach { parsed ->
                val group = parsed.region.muscleGroup
                val fill = group?.let { fills[it] } ?: unmappedFill
                drawPath(parsed.path, color = fill)
                val isSelected = group != null && group == selected
                val strokeColor = when {
                    isSelected -> selectedOutline
                    group != null -> mappedOutline
                    else -> outline
                }
                drawPath(
                    path = parsed.path,
                    color = strokeColor,
                    style = Stroke(width = if (isSelected) 0.28f else 0.12f)
                )
            }
        }
    }
}

internal val MUSCLE_MAP_FIGURE_GAP = 12.dp
internal val MUSCLE_MAP_ASPECT = BodyMusclesArtwork.VIEW_HEIGHT / BodyMusclesArtwork.VIEW_WIDTH

internal data class MuscleMapPairLayout(
    val figureWidth: Dp,
    val figureHeight: Dp,
    val gap: Dp
) {
    val placesSideBySide: Boolean = true
    val figureCount: Int = 2
}

internal fun muscleMapPairLayout(
    availableWidth: Dp,
    gap: Dp = MUSCLE_MAP_FIGURE_GAP
): MuscleMapPairLayout {
    val boundedWidth = if (!availableWidth.isSpecified || !availableWidth.isFinite || availableWidth < 0.dp) {
        0.dp
    } else {
        availableWidth
    }
    val figureWidth = ((boundedWidth - gap) / 2f).coerceAtLeast(0.dp)
    val figureHeight = figureWidth * MUSCLE_MAP_ASPECT
    return MuscleMapPairLayout(
        figureWidth = figureWidth,
        figureHeight = figureHeight,
        gap = gap
    )
}

internal data class FigureLayout(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float
)

internal fun figureLayout(width: Float, height: Float): FigureLayout {
    val scale = min(
        width / BodyMusclesArtwork.VIEW_WIDTH,
        height / BodyMusclesArtwork.VIEW_HEIGHT
    )
    val drawnWidth = BodyMusclesArtwork.VIEW_WIDTH * scale
    val drawnHeight = BodyMusclesArtwork.VIEW_HEIGHT * scale
    return FigureLayout(
        scale = scale,
        offsetX = (width - drawnWidth) / 2f,
        offsetY = (height - drawnHeight) / 2f
    )
}

internal fun regionArea(parsed: ParsedMuscleRegion): Float {
    return parsed.bounds.width.coerceAtLeast(0f) * parsed.bounds.height.coerceAtLeast(0f)
}

internal fun paintOrderedRegions(regions: List<ParsedMuscleRegion>): List<ParsedMuscleRegion> {
    val unmapped = regions.filter { it.region.muscleGroup == null }
    val mapped = regions.filter { it.region.muscleGroup != null }
    val underlay = mapped.filter { it.region.muscleGroup !in DELTOID_OVERLAY_GROUPS }
        .sortedByDescending { regionArea(it) }
    val deltoids = mapped.filter { it.region.muscleGroup in DELTOID_OVERLAY_GROUPS }
        .sortedWith(
            compareBy<ParsedMuscleRegion> { deltoidPaintRank(it.region.muscleGroup) }
                .thenByDescending { regionArea(it) }
        )
    return unmapped + underlay + deltoids
}

private fun deltoidPaintRank(group: MuscleGroup?): Int {
    return when (group) {
        MuscleGroup.SIDE_DELTOID, MuscleGroup.REAR_DELTOID -> 0
        MuscleGroup.FRONT_DELTOID -> 1
        else -> 0
    }
}

private val DELTOID_OVERLAY_GROUPS = setOf(
    MuscleGroup.FRONT_DELTOID,
    MuscleGroup.SIDE_DELTOID,
    MuscleGroup.REAR_DELTOID
)

internal fun overlayRegionIds(
    regions: List<ParsedMuscleRegion>,
    overlayGroups: Set<MuscleGroup>
): Set<String> {
    return regions
        .filter { parsed -> parsed.region.muscleGroup in overlayGroups }
        .map { it.region.id }
        .toSet()
}

internal fun hitTest(
    regions: List<ParsedMuscleRegion>,
    point: Offset,
    inflate: Float = 1.8f
): MuscleGroup? {
    data class Candidate(
        val group: MuscleGroup,
        val contains: Boolean,
        val distance: Float,
        val area: Float
    )
    val candidates = ArrayList<Candidate>()
    regions.forEach { parsed ->
        val group = parsed.region.muscleGroup ?: return@forEach
        val inflated = parsed.bounds.inflate(inflate)
        if (!inflated.contains(point)) {
            return@forEach
        }
        val contains = pathContains(parsed.path, point)
        val center = parsed.bounds.center
        val dx = point.x - center.x
        val dy = point.y - center.y
        candidates += Candidate(group, contains, dx * dx + dy * dy, regionArea(parsed))
    }
    if (candidates.isEmpty()) {
        return null
    }
    val exact = candidates.filter { it.contains }
    if (exact.isNotEmpty()) {
        return exact.minBy { it.area }.group
    }
    return candidates.minBy { it.distance }.group
}

internal fun pathContains(path: Path, point: Offset): Boolean {
    val source = android.graphics.Path(path.asAndroidPath())
    val bounds = android.graphics.RectF()
    source.computeBounds(bounds, true)
    val half = 0.02f
    if (
        point.x < bounds.left - half ||
        point.x > bounds.right + half ||
        point.y < bounds.top - half ||
        point.y > bounds.bottom + half
    ) {
        return false
    }
    val probe = android.graphics.Path()
    probe.addRect(
        point.x - half,
        point.y - half,
        point.x + half,
        point.y + half,
        android.graphics.Path.Direction.CW
    )
    val result = android.graphics.Path()
    return result.op(source, probe, android.graphics.Path.Op.INTERSECT) && !result.isEmpty
}
