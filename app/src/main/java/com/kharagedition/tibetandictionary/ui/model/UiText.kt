package com.kharagedition.tibetandictionary.ui.model

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

/** A string resource and its format [args], resolved by the UI in the app language. */
@Immutable
data class UiText(@param:StringRes val id: Int, val args: List<String> = emptyList()) {
    fun resolve(resources: Resources): String =
        if (args.isEmpty()) resources.getString(id) else resources.getString(id, *args.toTypedArray())
}
