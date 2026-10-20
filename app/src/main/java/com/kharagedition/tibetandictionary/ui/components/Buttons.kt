package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetandictionary.ui.theme.DictIcons
import com.kharagedition.tibetandictionary.ui.theme.DictShapes
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/** Button heights: Cta 52 (onboarding Continue), Default 48, Medium 44 (empty-state CTA), Small 40, Xs 36. */
enum class ButtonSize { Cta, Default, Medium, Small, Xs }

/** Leading/trailing icon size inside a button. */
private val ButtonIcon = 16.dp

/** Disabled buttons draw at this opacity. */
private const val DISABLED_ALPHA = 0.5f

private enum class ButtonVariant { Filled, Tonal, Outlined, Text }

@Composable
private fun ButtonSize.height(): Dp = with(DictTheme.dimens) {
    when (this@height) {
        ButtonSize.Cta -> buttonCta
        ButtonSize.Default -> buttonDefault
        ButtonSize.Medium -> buttonMedium
        ButtonSize.Small -> buttonSmall
        ButtonSize.Xs -> buttonXs
    }
}

/** Side padding per the spec: 24 / 22 / 20 / 18 / 14 for pills, 12 / 12 / 12 / 8 / 6 for text buttons. */
private fun ButtonSize.horizontalPadding(variant: ButtonVariant): Dp = if (variant == ButtonVariant.Text) {
    when (this) {
        ButtonSize.Cta, ButtonSize.Default, ButtonSize.Medium -> 12.dp
        ButtonSize.Small -> 8.dp
        ButtonSize.Xs -> 6.dp
    }
} else {
    when (this) {
        ButtonSize.Cta -> 24.dp
        ButtonSize.Default -> 22.dp
        ButtonSize.Medium -> 20.dp
        ButtonSize.Small -> 18.dp
        ButtonSize.Xs -> 14.dp
    }
}

/**
 * Label roles: Cta 15/600; Default and Medium 14 (600 filled/tonal/accent-outlined, 500 outlined/text); Small and
 * Xs 13 with the same weights. Tibetan runs draw in Jomolhari 16 (15 on Small/Xs).
 */
@Composable
private fun ButtonSize.textStyles(semibold: Boolean): Pair<TextStyle, TextStyle> {
    val type = DictTheme.type
    return when (this) {
        ButtonSize.Cta -> type.buttonLarge to type.tibetanInline16
        ButtonSize.Default, ButtonSize.Medium ->
            (if (semibold) type.button else type.buttonOutlined) to type.tibetanInline16
        ButtonSize.Small, ButtonSize.Xs -> (if (semibold) type.labelSemibold else type.label) to type.tibetanInline15
    }
}

@Composable
private fun DictButton(
    text: String,
    onClick: () -> Unit,
    variant: ButtonVariant,
    modifier: Modifier,
    size: ButtonSize,
    enabled: Boolean,
    accentBorder: Boolean,
    leading: ImageVector?,
    trailing: ImageVector?,
    textContent: Color = Color.Unspecified,
) {
    val colors = DictTheme.colors
    val (container, content) = when (variant) {
        ButtonVariant.Filled -> colors.accent to colors.onAccent
        ButtonVariant.Tonal -> colors.accentContainer to colors.onAccentContainer
        ButtonVariant.Outlined -> Color.Transparent to (if (accentBorder) colors.accent else colors.ink)
        ButtonVariant.Text -> Color.Transparent to textContent.takeOrElse { colors.accent }
    }
    val semibold = variant == ButtonVariant.Filled || variant == ButtonVariant.Tonal || accentBorder
    val (latinStyle, tibetanStyle) = size.textStyles(semibold)
    val shape = DictShapes.pill
    val border = if (variant == ButtonVariant.Outlined) {
        Modifier.border(DictTheme.dimens.border, if (accentBorder) colors.accent else colors.outline, shape)
    } else {
        Modifier
    }
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = size.height())
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(shape)
            .background(container)
            .then(border)
            .clickable(
                interactionSource = null,
                indication = ripple(color = content),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = size.horizontalPadding(variant)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DictTheme.dimens.chipGap, Alignment.CenterHorizontally),
    ) {
        if (leading != null) Icon(leading, contentDescription = null, Modifier.size(ButtonIcon), tint = content)
        MixedText(
            text,
            latinStyle,
            tibetanStyle,
            Modifier.padding(vertical = DictTheme.dimens.chipGap),
            color = content,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (trailing != null) Icon(trailing, contentDescription = null, Modifier.size(ButtonIcon), tint = content)
    }
}

/** Primary action: accent pill, onAccent label. [text] may mix scripts ("Save today's word བཀྲ་ཤིས།"). */
@Composable
fun FilledButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: ButtonSize = ButtonSize.Default,
    enabled: Boolean = true,
    leading: ImageVector? = null,
    trailing: ImageVector? = null,
) = DictButton(text, onClick, ButtonVariant.Filled, modifier, size, enabled, false, leading, trailing)

/** Secondary emphasis: accentContainer pill, onAccentContainer label. */
@Composable
fun TonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: ButtonSize = ButtonSize.Default,
    enabled: Boolean = true,
    leading: ImageVector? = null,
    trailing: ImageVector? = null,
) = DictButton(text, onClick, ButtonVariant.Tonal, modifier, size, enabled, false, leading, trailing)

/**
 * Transparent pill with a 1dp outline border and ink label (500). With [accentBorder] the border and label are
 * accent and the label 600 ("Search in app ↗").
 */
@Composable
fun OutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: ButtonSize = ButtonSize.Default,
    enabled: Boolean = true,
    accentBorder: Boolean = false,
    leading: ImageVector? = null,
    trailing: ImageVector? = null,
) = DictButton(text, onClick, ButtonVariant.Outlined, modifier, size, enabled, accentBorder, leading, trailing)

/**
 * Borderless label ("Open", "Clear") in [contentColor], accent by default; a quiet action such as onboarding's Skip
 * passes muted (2b/2c). Icons and the ripple take the same colour.
 */
@Composable
fun TextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: ButtonSize = ButtonSize.Default,
    enabled: Boolean = true,
    leading: ImageVector? = null,
    trailing: ImageVector? = null,
    contentColor: Color = DictTheme.colors.accent,
) = DictButton(text, onClick, ButtonVariant.Text, modifier, size, enabled, false, leading, trailing, contentColor)

/**
 * Round icon button of [size] (48 / 40 / 36; the touch target is always at least 48) with an [iconSize] icon
 * (24, or 20 at 36) in [tint]. [filled] draws the circle in [background] (surface1 by default), as on the entry
 * screen's audio button.
 */
@Composable
fun DictIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = DictTheme.dimens.iconButtonLarge,
    iconSize: Dp = if (size <= DictTheme.dimens.iconButtonSmall) DictTheme.dimens.iconSmall else DictTheme.dimens.iconLarge,
    tint: Color = DictTheme.colors.ink,
    background: Color = DictTheme.colors.surface1,
    filled: Boolean = false,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .size(size)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(CircleShape)
            .background(if (filled) background else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(iconSize), tint = tint)
    }
}

@PreviewLightDark
@BoPreview
@Composable
private fun ButtonsPreview() {
    ComponentPreview {
        FilledButton("Continue", {}, Modifier.fillMaxWidth(), size = ButtonSize.Cta)
        Row(horizontalArrangement = Arrangement.spacedBy(DictTheme.dimens.chipGap)) {
            FilledButton("Filled", {})
            TonalButton("Tonal", {})
            OutlinedButton("Outlined", {})
            TextButton("Text", {})
        }
        FilledButton("Save today's word བཀྲ་ཤིས།", {}, size = ButtonSize.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(DictTheme.dimens.chipGap)) {
            OutlinedButton("Try EN →བོད instead", {}, size = ButtonSize.Small)
            OutlinedButton("Search in app", {}, size = ButtonSize.Small, accentBorder = true, trailing = DictIcons.OpenExternal)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DictTheme.dimens.chipGap)) {
            FilledButton("Sign in", {}, size = ButtonSize.Xs)
            FilledButton("Disabled", {}, size = ButtonSize.Xs, enabled = false)
            TextButton("Open", {}, size = ButtonSize.Xs)
            TextButton("Skip", {}, size = ButtonSize.Small, contentColor = DictTheme.colors.muted)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            DictIconButton(DictIcons.Speaker, "Play", {}, tint = DictTheme.colors.accent, filled = true)
            DictIconButton(DictIcons.Heart, "Save", {}, size = DictTheme.dimens.iconButtonMedium, tint = DictTheme.colors.muted)
            DictIconButton(
                DictIcons.Speaker,
                "Play",
                {},
                size = DictTheme.dimens.iconButtonSmall,
                tint = DictTheme.colors.accent,
                filled = true,
            )
            DictIconButton(DictIcons.Back, "Back", {})
        }
    }
}
