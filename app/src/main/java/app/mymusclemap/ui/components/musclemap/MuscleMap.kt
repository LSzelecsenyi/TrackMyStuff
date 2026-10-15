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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import app.mymusclemap.ui.components.musclemap.artwork.MuscleArtworkRegion
import app.mymusclemap.ui.components.musclemap.artwork.MuscleMapArtworkSelector
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
    val artwork = MuscleMapArtworkSelector.current()
    return artwork.regions.map { region ->
        val parser = PathParser()
        parser.parsePathString(region.pathData)
        val raw = parser.toPath()
        raw.fillType = PathFillType.NonZero
        val path = if (region.view == MuscleMapView.BACK) {
            Path().apply {
                fillType = PathFillType.NonZero
                addPath(raw, Offset(-artwork.splitX, 0f))
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
    interactive: Boolean = true,
    maxFigureHeight: Dp = Dp.Unspecified
) {
    val context = LocalContext.current
    val anatomy = remember { MuscleMapAnatomy.load(context) }
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
        // Full-aspect figures are too tall to leave room for the week calendar on narrow phones.
        val heightCap = if (maxFigureHeight.isSpecified && availableWidth <= 336.dp) {
            maxFigureHeight
        } else {
            Dp.Unspecified
        }
        val pair = muscleMapPairLayout(
            availableWidth = availableWidth,
            gap = MUSCLE_MAP_FIGURE_GAP,
            maxFigureHeight = heightCap
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
                anatomy = anatomy.front,
                lighting = anatomy.frontLighting,
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
                anatomy = anatomy.back,
                lighting = anatomy.backLighting,
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
    anatomy: android.graphics.Bitmap,
    lighting: android.graphics.Bitmap,
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
    val artwork = MuscleMapArtworkSelector.current()
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
        drawIntoCanvas { composeCanvas ->
            val native = composeCanvas.nativeCanvas
            native.save()
            native.translate(layout.offsetX, layout.offsetY)
            native.scale(layout.scale, layout.scale)
            MuscleMapFigurePainter.draw(
                canvas = native,
                anatomy = anatomy,
                lighting = lighting,
                regions = regions,
                fills = fills,
                unmappedFill = unmappedFill,
                outline = outline,
                mappedOutline = mappedOutline,
                selectedOutline = selectedOutline,
                selected = selected,
                viewWidth = artwork.viewWidth,
                viewHeight = artwork.viewHeight
            )
            native.restore()
        }
    }
}

internal val MUSCLE_MAP_FIGURE_GAP = 12.dp

/** Stroke widths keep the weight they had on the previous 35-unit artwork. */
private const val LEGACY_VIEW_WIDTH = 35f
internal const val MUSCLE_MAP_STROKE_WIDTH = 0.12f * 526f / LEGACY_VIEW_WIDTH
internal const val MUSCLE_MAP_SELECTED_STROKE_WIDTH = 0.28f * 526f / LEGACY_VIEW_WIDTH
internal val MUSCLE_MAP_ASPECT: Float
    get() {
        val artwork = MuscleMapArtworkSelector.current()
        return artwork.viewHeight / artwork.viewWidth
    }

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
    gap: Dp = MUSCLE_MAP_FIGURE_GAP,
    maxFigureHeight: Dp = Dp.Unspecified
): MuscleMapPairLayout {
    val boundedWidth = if (!availableWidth.isSpecified || !availableWidth.isFinite || availableWidth < 0.dp) {
        0.dp
    } else {
        availableWidth
    }
    val naturalWidth = ((boundedWidth - gap) / 2f).coerceAtLeast(0.dp)
    val naturalHeight = naturalWidth * MUSCLE_MAP_ASPECT
    val figureHeight = if (maxFigureHeight.isSpecified && naturalHeight > maxFigureHeight) {
        maxFigureHeight
    } else {
        naturalHeight
    }
    val figureWidth = if (figureHeight == naturalHeight) {
        naturalWidth
    } else {
        (figureHeight / MUSCLE_MAP_ASPECT).coerceAtLeast(0.dp)
    }
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
    val artwork = MuscleMapArtworkSelector.current()
    val scale = min(
        width / artwork.viewWidth,
        height / artwork.viewHeight
    )
    val drawnWidth = artwork.viewWidth * scale
    val drawnHeight = artwork.viewHeight * scale
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
    inflate: Float = 1.8f * 526f / 35f
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
    val half = 0.02f * 526f / 35f
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
