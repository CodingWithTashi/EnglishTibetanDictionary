package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.ui.theme.DictShapes
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Toast padding (16 start, 8 end), the action's side padding (10) and the message's vertical padding. */
private val ToastStart = 16.dp
private val ToastEnd = 8.dp
private val ActionSide = 10.dp
private val MessageVertical = 12.dp

/** Widest toast on large screens. */
private val ToastMaxWidth = 640.dp

private const val FLING_DP_PER_S = 600
private const val DOWN_DISMISS_DP = 32
private const val SWIPE_OUT_MS = 150
private const val SWIPE_DISMISS_FRACTION = 0.25f
private const val FLING_PROJECTION = 0.1f
private const val MIN_ALPHA_DROP = 0.8f

/** Snackbar visuals that keep the full [text] (spans included) next to its plain [message]. */
private class DictToastVisuals(
    val text: CharSequence,
    override val actionLabel: String?,
    override val duration: SnackbarDuration,
) : SnackbarVisuals {
    override val message: String = text.toString()
    override val withDismissAction: Boolean = false
}

/**
 * The toast: inverseSurface, radius 12, at least 48 high; 13 inverseOnSurface message whose Tibetan runs draw in
 * Jomolhari 16 at line height 1.0; an inverseAccent 600 action. Tap the card, or flick it sideways or down, to
 * dismiss it. No shadow (tonal surfaces only).
 */
@Composable
internal fun DictToast(data: SnackbarData, modifier: Modifier = Modifier) {
    val colors = DictTheme.colors
    val type = DictTheme.type
    val shape = DictShapes.small
    val dismissLabel = stringResource(R.string.close)
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    // Keyed on [data] so a dismissed toast's offset never leaks into the next one.
    val x = remember(data) { Animatable(0f) }
    val y = remember(data) { Animatable(0f) }
    var width by remember(data) { mutableIntStateOf(1) }
    val flingVelocity = with(density) { FLING_DP_PER_S.dp.toPx() }
    val downThreshold = with(density) { DOWN_DISMISS_DP.dp.toPx() }
    val text = (data.visuals as? DictToastVisuals)?.text ?: data.visuals.message
    Row(
        modifier
            .fillMaxWidth()
            .widthIn(max = ToastMaxWidth)
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .offset { IntOffset(x.value.roundToInt(), y.value.roundToInt()) }
            .graphicsLayer {
                val gone = max(abs(x.value) / width, y.value / (downThreshold * 4)).coerceIn(0f, 1f)
                alpha = 1f - gone * MIN_ALPHA_DROP
            }
            .pointerInput(data) {
                val velocity = VelocityTracker()
                // The card follows the finger, so change.position barely moves; track the summed drag instead.
                var travelled = Offset.Zero
                detectDragGestures(
                    onDragStart = { travelled = Offset.Zero },
                    onDrag = { change, drag ->
                        change.consume()
                        travelled += drag
                        velocity.addPosition(change.uptimeMillis, travelled)
                        scope.launch {
                            x.snapTo(x.value + drag.x)
                            y.snapTo((y.value + drag.y).coerceAtLeast(0f))
                        }
                    },
                    onDragEnd = {
                        val v = velocity.calculateVelocity()
                        velocity.resetTracking()
                        val goX = abs(x.value) > width * SWIPE_DISMISS_FRACTION || abs(v.x) > flingVelocity
                        val goDown = y.value > downThreshold || v.y > flingVelocity
                        scope.launch {
                            when {
                                goX -> {
                                    val right = x.value + v.x * FLING_PROJECTION >= 0f
                                    x.animateTo(if (right) width.toFloat() else -width.toFloat(), tween(SWIPE_OUT_MS))
                                }
                                goDown -> y.animateTo(y.value + downThreshold * 4, tween(SWIPE_OUT_MS))
                                else -> {
                                    launch { x.animateTo(0f) }
                                    y.animateTo(0f)
                                    return@launch
                                }
                            }
                            data.dismiss()
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            launch { x.animateTo(0f) }
                            y.animateTo(0f)
                        }
                    },
                )
            }
            .clip(shape)
            .background(colors.inverseSurface)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = dismissLabel) {
                data.dismiss()
            }
            .heightIn(min = DictTheme.dimens.toastHeight)
            .padding(start = ToastStart, end = ToastEnd)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                customActions = listOf(CustomAccessibilityAction(dismissLabel) { data.dismiss(); true })
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DictTheme.dimens.chipGap),
    ) {
        MixedText(
            text,
            type.secondary,
            type.tibetanToast,
            Modifier
                .weight(1f)
                .padding(vertical = MessageVertical),
            color = colors.inverseOnSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        val action = data.visuals.actionLabel
        if (action != null) {
            Box(
                Modifier
                    .defaultMinSize(minWidth = DictTheme.dimens.minTouch, minHeight = DictTheme.dimens.minTouch)
                    .clickable(role = Role.Button) { data.performAction() }
                    .padding(horizontal = ActionSide),
                contentAlignment = Alignment.Center,
            ) {
                MixedText(action, type.labelSemibold, type.tibetanToast, color = colors.inverseAccent)
            }
        }
    }
}

/**
 * Tracks the top edge (root coordinates) of bottom-pinned UI such as the nav bar, so the toast host can sit just
 * above the highest one instead of covering it.
 */
@Stable
class ToastAnchors {
    private val tops = mutableStateMapOf<Any, Float>()

    /** Top edge of the highest registered anchor in px, or null when none is on screen. */
    val top: Float? get() = tops.values.minOrNull()

    internal fun update(key: Any, top: Float) {
        if (tops[key] != top) tops[key] = top
    }

    internal fun remove(key: Any) {
        tops.remove(key)
    }
}

/** App-level [ToastAnchors]; the app root provides one shared instance. */
val LocalToastAnchors = staticCompositionLocalOf { ToastAnchors() }

/** Marks a bottom-pinned element (the [BottomNavBar] does this itself) so toasts stack above it. */
fun Modifier.toastAnchor(): Modifier = this then ToastAnchorElement

private data object ToastAnchorElement : ModifierNodeElement<ToastAnchorNode>() {
    override fun create() = ToastAnchorNode()

    override fun update(node: ToastAnchorNode) = Unit

    override fun InspectorInfo.inspectableProperties() {
        name = "toastAnchor"
    }
}

private class ToastAnchorNode : Modifier.Node(), CompositionLocalConsumerModifierNode, GlobalPositionAwareModifierNode {
    private var anchors: ToastAnchors? = null

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val target = anchors ?: currentValueOf(LocalToastAnchors).also { anchors = it }
        target.update(this, coordinates.boundsInRoot().top)
    }

    override fun onDetach() {
        anchors?.remove(this)
        anchors = null
    }
}

/**
 * Bottom-anchored toast host. Place it in a root Box (aligned BottomCenter) that fills the screen and pass that
 * Box's height as [rootHeightPx]. It sits 12dp above the highest [toastAnchor] (the nav bar), or 16dp above the
 * system navigation bar / keyboard when no anchor is on screen, with 16dp side margins.
 */
@Composable
fun AppToastHost(hostState: SnackbarHostState, rootHeightPx: Int, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val dimens = DictTheme.dimens
    val anchorTop = LocalToastAnchors.current.top
    val systemBottom = WindowInsets.navigationBars.union(WindowInsets.ime).asPaddingValues().calculateBottomPadding()
    val bottom = if (anchorTop != null) {
        with(density) { (rootHeightPx - anchorTop).coerceAtLeast(0f).toDp() } + dimens.toastAboveNav
    } else {
        systemBottom + dimens.toastSideMargin
    }
    SnackbarHost(
        hostState = hostState,
        modifier = modifier.padding(start = dimens.toastSideMargin, end = dimens.toastSideMargin, bottom = bottom),
    ) { data -> DictToast(data) }
}

/**
 * Shows toasts (with an optional action such as Undo or Change) on a [SnackbarHostState]. Timeouts are shorter
 * than Material's and stretched to the platform's recommended value when an accessibility service is on.
 */
class Toaster(private val host: SnackbarHostState, private val accessibility: AccessibilityManager? = null) {
    /**
     * Shows [message] (Tibetan runs draw in Jomolhari) for 2 s, 3.5 s with an [action], 8 s when [long]; when
     * [action] is tapped, [onAction] runs. Replaces any toast on screen and suspends until this one closes by
     * tap, swipe, action or timeout.
     */
    suspend fun show(
        message: CharSequence,
        action: String? = null,
        long: Boolean = false,
        onAction: (suspend () -> Unit)? = null,
    ) {
        host.currentSnackbarData?.dismiss()
        val base = when {
            long -> LONG_MS
            action != null -> WITH_ACTION_MS
            else -> PLAIN_MS
        }
        val timeout = accessibility?.calculateRecommendedTimeoutMillis(
            base,
            containsIcons = false,
            containsText = true,
            containsControls = action != null,
        ) ?: base
        // Indefinite: the timeout below owns the lifetime, and cancelling showSnackbar clears the toast.
        val result = withTimeoutOrNull(timeout) {
            host.showSnackbar(DictToastVisuals(message, action, SnackbarDuration.Indefinite))
        }
        if (result == SnackbarResult.ActionPerformed) onAction?.invoke()
    }

    companion object {
        const val PLAIN_MS = 2_000L
        const val WITH_ACTION_MS = 3_500L
        const val LONG_MS = 8_000L
    }
}

/** App-level [Toaster]; defaults to one that shows nothing until the app root provides the real one. */
val LocalToaster = staticCompositionLocalOf { Toaster(SnackbarHostState()) }

@PreviewLightDark
@BoPreview
@Composable
private fun ToastPreview() {
    ComponentPreview {
        val samples = listOf(
            DictToastVisuals("Saved བཀྲ་ཤིས། to Daily", "Change", SnackbarDuration.Indefinite),
            DictToastVisuals("Removed", "Undo", SnackbarDuration.Indefinite),
            DictToastVisuals("This entry is no longer in the dictionary.", null, SnackbarDuration.Indefinite),
        )
        samples.forEach { DictToast(PreviewSnackbarData(it)) }
    }
}

/** Static [SnackbarData] for previews. */
private class PreviewSnackbarData(override val visuals: SnackbarVisuals) : SnackbarData {
    override fun performAction() = Unit

    override fun dismiss() = Unit
}
