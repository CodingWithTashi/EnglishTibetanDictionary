package com.kharagedition.tibetandictionary.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetandictionary.core.locale.AppLanguage

/**
 * Component sizes, paddings and gaps on the 8pt grid. Heights of anything that wraps text are *minimums*, so
 * larger text sizes and font scales can grow it. Two instances, [En] and [Bo]: Tibetan UI text needs taller
 * settings rows.
 */
@Immutable
class DictDimens private constructor(val isTibetan: Boolean) {
    /** Minimum interactive size (accessibility). */
    val minTouch: Dp = 48.dp

    /** Hairline borders of cards, chips and outlined controls. */
    val border: Dp = 1.dp

    /** Inner border of a focused search field. */
    val focusBorder: Dp = 2.dp

    // Search field (corner radius = height / 2, i.e. DictShapes.pill)
    val searchFieldHome: Dp = 56.dp
    val searchFieldFocused: Dp = 52.dp
    val searchFieldCompact: Dp = 48.dp

    // Direction switcher
    val switcher: Dp = 40.dp
    val switcherCompact: Dp = 36.dp
    val switcherPadding: Dp = 4.dp
    val switcherSegmentRadius: Dp = 16.dp

    // Chips
    val chipLarge: Dp = 36.dp
    val chipSmall: Dp = 32.dp

    // Buttons (ButtonSize Cta / Default / Medium / Small / Xs)
    val buttonCta: Dp = 52.dp
    val buttonDefault: Dp = 48.dp
    val buttonMedium: Dp = 44.dp
    val buttonSmall: Dp = 40.dp
    val buttonXs: Dp = 36.dp

    // Icon buttons and the icon inside each
    val iconButtonLarge: Dp = 48.dp
    val iconButtonMedium: Dp = 40.dp
    val iconButtonSmall: Dp = 36.dp
    val iconLarge: Dp = 24.dp
    val iconMedium: Dp = 24.dp
    val iconSmall: Dp = 20.dp

    // Bottom navigation
    val navBar: Dp = 72.dp
    val navIndicatorWidth: Dp = 56.dp
    val navIndicatorHeight: Dp = 28.dp

    // Rows
    val entryRowPaddingVertical: Dp = 12.dp
    val entryRowPaddingHorizontal: Dp = 20.dp
    val savedRowPaddingVertical: Dp = 10.dp
    val savedRowPaddingHorizontal: Dp = 20.dp
    val seeAlsoRow: Dp = 52.dp
    val settingsRow: Dp = if (isTibetan) 56.dp else 52.dp
    val historyRow: Dp = 48.dp

    // Screen padding and gaps
    val screenPadding: Dp = 20.dp
    val onboardingPadding: Dp = 24.dp
    val sectionGap: Dp = 20.dp
    val blockGap: Dp = 12.dp
    val chipGap: Dp = 8.dp

    // Cards
    val cardPaddingVertical: Dp = 14.dp
    val cardPaddingHorizontal: Dp = 16.dp

    /** Word-of-the-day body padding under the prayer-flag strand: 10 top, 20 sides, 14 bottom. */
    val wotdBodyPaddingTop: Dp = 10.dp
    val wotdBodyPaddingHorizontal: Dp = 20.dp
    val wotdBodyPaddingBottom: Dp = 14.dp

    // Badges and pills
    val badgePaddingVertical: Dp = 3.dp
    val badgePaddingHorizontalTonal: Dp = 7.dp
    val badgePaddingHorizontalOutline: Dp = 6.dp
    val badgeRadius: Dp = 6.dp
    val streakPillPaddingVertical: Dp = 4.dp
    val streakPillPaddingHorizontal: Dp = 10.dp

    // Toast
    val toastHeight: Dp = 48.dp
    val toastAboveNav: Dp = 12.dp
    val toastSideMargin: Dp = 16.dp

    // Slider
    val sliderTrack: Dp = 4.dp
    val sliderThumb: Dp = 20.dp

    // Page indicator (current = dash, others = dot) and progress dots
    val pageDashWidth: Dp = 20.dp
    val pageDashHeight: Dp = 4.dp
    val pageDotWidth: Dp = 8.dp
    val pageDotHeight: Dp = 4.dp
    val progressDot: Dp = 8.dp

    // Skeleton bars
    val skeletonBarLarge: Dp = 22.dp
    val skeletonBarSmall: Dp = 12.dp
    val skeletonRadius: Dp = 6.dp

    /** App icon on the splash / installing screen. */
    val splashIcon: Dp = 84.dp

    // Empty-state circles
    val emptyStateCircleLarge: Dp = 72.dp
    val emptyStateCircleMedium: Dp = 64.dp
    val emptyStateCircleSmall: Dp = 40.dp

    companion object {
        val En = DictDimens(isTibetan = false)
        val Bo = DictDimens(isTibetan = true)

        /** Dimens for the UI [language]. */
        fun forLanguage(language: AppLanguage): DictDimens = if (language == AppLanguage.BO) Bo else En
    }
}

/** Corner radii (6 / 12 / 16 / 20 / 28), the bottom-sheet shape and the pill; [material] is the M3 bridge. */
object DictShapes {
    val extraSmall = RoundedCornerShape(6.dp)
    val small = RoundedCornerShape(12.dp)
    val medium = RoundedCornerShape(16.dp)
    val large = RoundedCornerShape(20.dp)
    val extraLarge = RoundedCornerShape(28.dp)

    /** Bottom sheets: top corners only. */
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    /** Fully rounded ends (search fields, pills, dots). */
    val pill = CircleShape

    val material = Shapes(
        extraSmall = extraSmall,
        small = small,
        medium = medium,
        large = large,
        extraLarge = extraLarge,
    )
}

/** Current dimens. */
val LocalDictDimens = staticCompositionLocalOf { DictDimens.En }
