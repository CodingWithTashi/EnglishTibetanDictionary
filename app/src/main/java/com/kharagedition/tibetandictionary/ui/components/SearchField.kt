package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.text.TibetanScript
import com.kharagedition.tibetandictionary.ui.theme.DictIcons
import com.kharagedition.tibetandictionary.ui.theme.DictShapes
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/** Search field heights: Home 56 (tap opens Search), Focused 52 (Search route), Compact 48 (search within Saved). */
enum class SearchFieldVariant { Home, Focused, Compact }

/** Start padding before the search icon or text. */
private val FieldStartPadding = 16.dp

/** End padding: Home 8 and Focused 6 (a 40dp mic/clear button sits there), Compact 16. */
private fun SearchFieldVariant.endPadding(): Dp = when (this) {
    SearchFieldVariant.Home -> 8.dp
    SearchFieldVariant.Focused -> 6.dp
    SearchFieldVariant.Compact -> 16.dp
}

/** Gap between icon, text and trailing button: 12 (Home, Compact), 10 (Focused). */
private fun SearchFieldVariant.gap(): Dp = if (this == SearchFieldVariant.Focused) 10.dp else 12.dp

@Composable
private fun SearchFieldVariant.height(): Dp = when (this) {
    SearchFieldVariant.Home -> DictTheme.dimens.searchFieldHome
    SearchFieldVariant.Focused -> DictTheme.dimens.searchFieldFocused
    SearchFieldVariant.Compact -> DictTheme.dimens.searchFieldCompact
}

/** Typed-text role: Home 16, Focused 17, Compact 15; Tibetan runs draw in Jomolhari 18 (16 on Compact). */
@Composable
private fun SearchFieldVariant.textStyles(): Pair<TextStyle, TextStyle> = when (this) {
    SearchFieldVariant.Home -> DictTheme.type.fieldTextHome to DictTheme.type.tibetanGloss
    SearchFieldVariant.Focused -> DictTheme.type.fieldText to DictTheme.type.tibetanGloss
    SearchFieldVariant.Compact -> DictTheme.type.body to DictTheme.type.tibetanInline16
}

/** Placeholder role: 16 (15 on Compact) muted, with Jomolhari 16 for its Tibetan half. */
@Composable
private fun SearchFieldVariant.placeholderStyle(): TextStyle =
    if (this == SearchFieldVariant.Compact) DictTheme.type.body else DictTheme.type.fieldPlaceholder

/** Draws the Tibetan runs of the typed query in Jomolhari without changing the text or cursor positions. */
private class TibetanRunsTransformation(private val tibetan: SpanStyle) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (!TibetanScript.hasTibetan(text)) return TransformedText(text, OffsetMapping.Identity)
        val styled = buildAnnotatedString {
            append(text)
            TibetanScript.runs(text).forEach { addStyle(tibetan, it.first, it.last + 1) }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }

    override fun equals(other: Any?): Boolean = other is TibetanRunsTransformation && other.tibetan == tibetan

    override fun hashCode(): Int = tibetan.hashCode()
}

/**
 * Pill search field on surface1 with a muted 24dp search icon ([leadingSearchIcon]) and muted [placeholder]
 * (may mix scripts). While [focused], or while it actually has focus, it shows a 2dp accent inner border and the
 * caret is accent. A clear button (`search_clear`) shows whenever there is text and calls [onClear]; the mic
 * (`search_mic`) shows only when [showMic] and the field is empty. With [onClick] the field is a button that
 * shows [value] read-only (the Home field, which opens Search); otherwise it edits [value] (`search_field`) and
 * the keyboard's Search key calls [onSearch].
 */
@Composable
fun SearchField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    variant: SearchFieldVariant = SearchFieldVariant.Focused,
    focused: Boolean = false,
    showMic: Boolean = false,
    onMic: () -> Unit = {},
    onClear: () -> Unit = { onValueChange(TextFieldValue()) },
    leadingSearchIcon: Boolean = variant != SearchFieldVariant.Focused,
    focusRequester: FocusRequester? = null,
    onClick: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
) {
    val colors = DictTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hasFocus by interaction.collectIsFocusedAsState()
    val showFocus = focused || (onClick == null && hasFocus)
    val shape = DictShapes.pill
    val isEmpty = value.text.isEmpty()
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = variant.height())
            .clip(shape)
            .background(colors.surface1)
            .then(if (showFocus) Modifier.border(DictTheme.dimens.focusBorder, colors.accent, shape) else Modifier)
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable(role = Role.Button, onClick = onClick)
                        .testTag(FIELD_TAG)
                } else {
                    Modifier
                },
            )
            .padding(start = FieldStartPadding, end = variant.endPadding()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(variant.gap()),
    ) {
        if (leadingSearchIcon) {
            Icon(DictIcons.Search, contentDescription = null, Modifier.size(DictTheme.dimens.iconLarge), tint = colors.muted)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (onClick != null) {
                DisplayText(value.text, placeholder, variant)
            } else {
                EditableText(value, onValueChange, placeholder, variant, interaction, focusRequester, onSearch)
            }
        }
        TrailingButton(isEmpty, editable = onClick == null, showMic, onMic, onClear)
    }
}

/** [SearchField] over a plain string; selection is kept locally. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    variant: SearchFieldVariant = SearchFieldVariant.Focused,
    focused: Boolean = false,
    showMic: Boolean = false,
    onMic: () -> Unit = {},
    onClear: () -> Unit = { onValueChange("") },
    leadingSearchIcon: Boolean = variant != SearchFieldVariant.Focused,
    focusRequester: FocusRequester? = null,
    onClick: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
) {
    var local by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val current = if (local.text == value) local else TextFieldValue(value, TextRange(value.length))
    SearchField(
        value = current,
        onValueChange = {
            local = it
            if (it.text != value) onValueChange(it.text)
        },
        placeholder = placeholder,
        modifier = modifier,
        variant = variant,
        focused = focused,
        showMic = showMic,
        onMic = onMic,
        onClear = onClear,
        leadingSearchIcon = leadingSearchIcon,
        focusRequester = focusRequester,
        onClick = onClick,
        onSearch = onSearch,
    )
}

@Composable
private fun Placeholder(placeholder: String, variant: SearchFieldVariant) {
    MixedText(
        placeholder,
        variant.placeholderStyle(),
        DictTheme.type.tibetanInline16,
        color = DictTheme.colors.muted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun DisplayText(text: String, placeholder: String, variant: SearchFieldVariant) {
    if (text.isEmpty()) {
        Placeholder(placeholder, variant)
    } else {
        val (latin, tibetan) = variant.textStyles()
        MixedText(text, latin, tibetan, color = DictTheme.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EditableText(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    variant: SearchFieldVariant,
    interaction: MutableInteractionSource,
    focusRequester: FocusRequester?,
    onSearch: (() -> Unit)?,
) {
    val colors = DictTheme.colors
    val (latin, tibetan) = variant.textStyles()
    val transformation = remember(tibetan) { TibetanRunsTransformation(tibetan.toRunSpan()) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .testTag(FIELD_TAG),
        textStyle = latin.copy(color = colors.ink),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
        visualTransformation = transformation,
        interactionSource = interaction,
        cursorBrush = SolidColor(colors.accent),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.text.isEmpty()) Placeholder(placeholder, variant)
                inner()
            }
        },
    )
}

@Composable
private fun TrailingButton(isEmpty: Boolean, editable: Boolean, showMic: Boolean, onMic: () -> Unit, onClear: () -> Unit) {
    val dimens = DictTheme.dimens
    val muted = DictTheme.colors.muted
    when {
        !isEmpty && editable -> DictIconButton(
            DictIcons.Close,
            stringResource(R.string.clear),
            onClear,
            Modifier.testTag(CLEAR_TAG),
            size = dimens.iconButtonMedium,
            iconSize = dimens.iconSmall,
            tint = muted,
        )
        isEmpty && showMic -> DictIconButton(
            DictIcons.Mic,
            stringResource(R.string.voice_search),
            onMic,
            Modifier.testTag(MIC_TAG),
            size = dimens.iconButtonMedium,
            tint = muted,
        )
    }
}

/**
 * The Search route header: a 48dp back button beside the [field] (a [SearchField] in the Focused variant), and
 * the optional [switcher] (a [DirectionSwitcher]) below with 4dp side insets.
 */
@Composable
fun SearchHeader(
    onBack: () -> Unit,
    field: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    switcher: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(start = HeaderSide, top = HeaderTop, end = HeaderSide, bottom = HeaderSide),
        verticalArrangement = Arrangement.spacedBy(HeaderGap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DictTheme.dimens.switcherPadding)) {
            DictIconButton(DictIcons.Back, stringResource(R.string.back), onBack)
            field()
        }
        if (switcher != null) Box(Modifier.padding(horizontal = DictTheme.dimens.switcherPadding)) { switcher() }
    }
}

/** Search header padding (8 top, 12 sides and bottom) and the gap between its rows (10). */
private val HeaderTop = 8.dp
private val HeaderSide = 12.dp
private val HeaderGap = 10.dp

private const val FIELD_TAG = "search_field"
private const val CLEAR_TAG = "search_clear"
private const val MIC_TAG = "search_mic"

@PreviewLightDark
@BoPreview
@Composable
private fun SearchFieldPreview() {
    val placeholder = stringResource(R.string.search_placeholder)
    ComponentPreview {
        SearchField("", {}, placeholder, variant = SearchFieldVariant.Home, showMic = true, onClick = {})
        SearchField("bkra sh", {}, placeholder, variant = SearchFieldVariant.Home, focused = true, onClick = {})
        SearchHeader(
            onBack = {},
            field = { SearchField("bkra", {}, placeholder, Modifier.weight(1f), focused = true) },
            switcher = { DirectionSwitcher(PreviewSamples.threeSlots, PreviewSamples.boEn, onSelect = {}) },
        )
        SearchField("", {}, placeholder, focused = true, showMic = true)
        SearchField("བཀྲ་ཤིས", {}, placeholder, focused = true)
        SearchField("", {}, stringResource(R.string.search_placeholder_saved), variant = SearchFieldVariant.Compact)
    }
}
