package com.kharagedition.tibetandictionary.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.text.TibetanNumerals
import com.kharagedition.tibetandictionary.data.model.descriptionRes
import com.kharagedition.tibetandictionary.data.model.entryCountRes
import com.kharagedition.tibetandictionary.data.model.glossRes
import com.kharagedition.tibetandictionary.data.model.nameRes
import com.kharagedition.tibetandictionary.ui.components.DirectionLabel
import com.kharagedition.tibetandictionary.ui.components.FlagStrand
import com.kharagedition.tibetandictionary.ui.components.FlagStrandSize
import com.kharagedition.tibetandictionary.ui.components.LineSpacingSlider
import com.kharagedition.tibetandictionary.ui.components.MixedText
import com.kharagedition.tibetandictionary.ui.components.OptionCard
import com.kharagedition.tibetandictionary.ui.components.PreviewCard
import com.kharagedition.tibetandictionary.ui.components.SliderHeader
import com.kharagedition.tibetandictionary.ui.components.TextSizeSlider
import com.kharagedition.tibetandictionary.ui.components.TibetanText
import com.kharagedition.tibetandictionary.ui.components.directionDescription
import com.kharagedition.tibetandictionary.ui.components.lineSpacingLabel
import com.kharagedition.tibetandictionary.ui.components.textSizeLabel
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/** Step 1 (2a): the strand 28 under the top bar, the greeting 14 under it and the title 4 under that. */
private val StrandTop = 28.dp
private val GreetingTop = 14.dp
private val TitleUnderGreeting = 4.dp

/** Steps without the strand: the title sits 28 under the top bar. */
private val TitleTop = 28.dp

/** Body copy 10 under the title. */
private val BodyTop = 10.dp

/** Direction cards: 28 under the body, 10 apart. */
private val CardsTop = 28.dp
private val CardGap = 10.dp

/** Step 3 (2c): preview 24 under the body; controls 28 under it, 14 apart, 6 more above the second slider's header. */
private val PreviewTop = 24.dp
private val ControlsTop = 28.dp
private val ControlGap = 14.dp
private val SecondControlTop = 6.dp

/** "Tashi delek", in Tibetan in every UI language. */
private const val GREETING = "བཀྲ་ཤིས་བདེ་ལེགས།"

private fun stepTag(step: Int) = "onboarding_step_$step"

/**
 * Step 1 (2a): the prayer-flag strand, the greeting, and an option card per dictionary pack with the selected one
 * checked: its name with the name in the other language, and a description with the entry count. Test tags:
 * `onboarding_step_1`, `onboarding_pack_{id}`.
 */
@Composable
internal fun DictionaryStep(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit, modifier: Modifier = Modifier) {
    val type = DictTheme.type
    val language = DictTheme.language
    Column(modifier.testTag(stepTag(OnboardingStep.DICTIONARY))) {
        FlagStrand(FlagStrandSize.Onboarding, Modifier.padding(top = StrandTop))
        TibetanText(GREETING, Modifier.padding(top = GreetingTop), style = type.tibetanGreeting, color = DictTheme.colors.accent)
        StepHeader(
            title = stringResource(R.string.onboarding_dictionary_title),
            body = stringResource(R.string.onboarding_dictionary_body),
            titleTop = TitleUnderGreeting,
        )
        Column(
            Modifier
                .padding(top = CardsTop)
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(CardGap),
        ) {
            state.packs.forEach { pack ->
                OptionCard(
                    selected = pack == state.selectedPack,
                    onClick = { onEvent(OnboardingEvent.SelectDictionary(pack)) },
                    modifier = Modifier.testTag("onboarding_pack_${pack.id}"),
                    description = stringResource(
                        pack.descriptionRes(),
                        TibetanNumerals.formatCount(integerResource(pack.entryCountRes()), language),
                    ),
                ) {
                    MixedText(
                        stringResource(pack.nameRes()) + " · " + stringResource(pack.glossRes()),
                        type.buttonLarge,
                        type.tibetanGloss,
                    )
                }
            }
        }
    }
}

/**
 * Step 2 (2b): an option card per visible direction with the selected one checked. Test tags:
 * `onboarding_step_2`, `onboarding_direction_{id}`.
 */
@Composable
internal fun DirectionStep(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit, modifier: Modifier = Modifier) {
    val type = DictTheme.type
    Column(modifier.testTag(stepTag(OnboardingStep.DIRECTION))) {
        StepHeader(
            title = stringResource(R.string.onboarding_direction_title),
            body = stringResource(R.string.onboarding_direction_body),
            titleTop = TitleTop,
        )
        Column(
            Modifier
                .padding(top = CardsTop)
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(CardGap),
        ) {
            state.directions.forEach { direction ->
                OptionCard(
                    selected = direction.id == state.selectedDirection?.id,
                    onClick = { onEvent(OnboardingEvent.SelectDirection(direction.id)) },
                    modifier = Modifier.testTag("onboarding_direction_${direction.id}"),
                    description = directionDescription(direction),
                ) {
                    DirectionLabel(direction, latinStyle = type.buttonLarge, tibetanStyle = type.tibetanGloss)
                }
            }
        }
    }
}

/**
 * Step 3 (2c): a live [PreviewCard] at the chosen text size and line spacing, and a slider for each. Test tags:
 * `onboarding_step_3`, `onboarding_text_size`, `onboarding_line_spacing`.
 */
@Composable
internal fun ReadingStep(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.testTag(stepTag(OnboardingStep.READING))) {
        StepHeader(
            title = stringResource(R.string.onboarding_textsize_title),
            body = stringResource(R.string.onboarding_textsize_body),
            titleTop = TitleTop,
        )
        PreviewCard(state.textSize, state.lineSpacing, Modifier.padding(top = PreviewTop))
        Column(Modifier.padding(top = ControlsTop), verticalArrangement = Arrangement.spacedBy(ControlGap)) {
            SliderHeader(stringResource(R.string.text_size), textSizeLabel(state.textSize))
            TextSizeSlider(
                state.textSize,
                onValueChange = { onEvent(OnboardingEvent.SetTextSize(it)) },
                modifier = Modifier.testTag("onboarding_text_size"),
            )
            SliderHeader(
                stringResource(R.string.line_spacing),
                lineSpacingLabel(state.lineSpacing, DictTheme.language),
                Modifier.padding(top = SecondControlTop),
            )
            LineSpacingSlider(
                state.lineSpacing,
                onValueChange = { onEvent(OnboardingEvent.SetLineSpacing(it)) },
                modifier = Modifier.testTag("onboarding_line_spacing"),
            )
        }
    }
}

/** Step 4 (2d, only with accounts): why to sign in; its buttons sit at the bottom of the screen. */
@Composable
internal fun AccountStep(modifier: Modifier = Modifier) {
    Column(modifier.testTag(stepTag(OnboardingStep.ACCOUNT))) {
        StepHeader(
            title = stringResource(R.string.onboarding_account_title),
            body = stringResource(R.string.onboarding_account_body),
            titleTop = TitleTop,
        )
    }
}

/** A step's title (26/600, Tibetan 26/1.7) and its muted body copy 10 below. */
@Composable
private fun StepHeader(title: String, body: String, titleTop: Dp) {
    val type = DictTheme.type
    MixedText(
        title,
        type.onboardingTitle,
        type.tibetanGreeting,
        Modifier
            .padding(top = titleTop)
            .semantics { heading() },
        color = DictTheme.colors.ink,
    )
    MixedText(body, type.bodySmall, type.tibetanInline16, Modifier.padding(top = BodyTop), color = DictTheme.colors.muted)
}
