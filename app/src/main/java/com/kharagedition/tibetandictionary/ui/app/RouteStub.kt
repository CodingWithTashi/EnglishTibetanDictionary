package com.kharagedition.tibetandictionary.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/**
 * Placeholder body for Learn, the one destination without a screen yet (its tab shows only with `Features.LEARN`,
 * off in release 1): the [title], centred. Delete it when Learn gets its screen.
 */
@Composable
internal fun RouteStub(title: String) {
    Column(
        Modifier
            .fillMaxSize()
            .background(DictTheme.colors.background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = DictTheme.type.screenTitle, color = DictTheme.colors.ink)
    }
}
