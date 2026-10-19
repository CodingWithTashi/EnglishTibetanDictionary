package com.kharagedition.tibetandictionary.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.kharagedition.tibetandictionary.ui.components.TextButton
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/**
 * Placeholder body for a destination whose lane has not landed: the route [title] and one text button per action
 * (label to callback), tagged `stub_{label in snake case}`, so the shell can be driven by hand and by tests.
 * Delete it once the last stub body is replaced.
 */
@Composable
internal fun RouteStub(title: String, vararg actions: Pair<String, () -> Unit>) {
    Column(
        Modifier
            .fillMaxSize()
            .background(DictTheme.colors.background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = DictTheme.type.screenTitle, color = DictTheme.colors.ink)
        actions.forEach { (label, onClick) ->
            TextButton(label, onClick, Modifier.testTag("stub_" + label.lowercase().replace(' ', '_')))
        }
    }
}
