package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.ui.model.FolderUi
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderPickerSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val folders = listOf(FolderUi(1, "Dharma", 14), FolderUi(2, "Daily", 11))

    private var creating by mutableStateOf(false)
    private var name by mutableStateOf("")
    private var error by mutableStateOf<String?>(null)
    private val created = mutableListOf<String>()
    private val picked = mutableListOf<Long?>()

    /** A caller that owns the new-folder state, clears the error on edit and resets on cancel. */
    private fun setSheet() {
        compose.setContent {
            DictTheme(darkTheme = false) {
                FolderPickerSheet(
                    folders = folders,
                    selectedId = 1,
                    onPick = { picked += it },
                    creating = creating,
                    newFolderName = name,
                    onNewFolderNameChange = {
                        name = it
                        error = null
                    },
                    onStartCreate = { creating = true },
                    onCancelCreate = {
                        creating = false
                        name = ""
                        error = null
                    },
                    onCreate = { created += it },
                    onDismiss = {},
                    createError = error,
                )
            }
        }
    }

    @Test
    fun `the sheet lists All and every folder under its title`() {
        setSheet()

        compose.onNodeWithText("Save to folder").assertIsDisplayed()
        compose.onNodeWithText("Daily").performClick()
        compose.onNodeWithText("All").performClick()

        assertThat(picked).containsExactly(2L, null).inOrder()
    }

    @Test
    fun `New folder asks the caller to start creating`() {
        setSheet()
        compose.onNodeWithTag(NAME_FIELD).assertDoesNotExist()

        compose.onNodeWithText("New folder").performClick()

        assertThat(creating).isTrue()
        compose.onNodeWithTag(NAME_FIELD).assertIsDisplayed()
        compose.onNodeWithText("Folder name").assertIsDisplayed()
    }

    @Test
    fun `typing reports the name so the caller can clear a stale error`() {
        creating = true
        error = "A folder with this name exists."
        setSheet()
        compose.onNodeWithText("A folder with this name exists.").assertIsDisplayed()

        compose.onNodeWithTag(NAME_FIELD).performTextInput("Grammar")

        assertThat(name).isEqualTo("Grammar")
        compose.onNodeWithText("A folder with this name exists.").assertDoesNotExist()
    }

    @Test
    fun `Save passes the trimmed name and is disabled while the name is blank`() {
        creating = true
        name = "   "
        setSheet()
        compose.onNodeWithText("Save").assertIsNotEnabled()

        name = "  Grammar  "
        compose.onNodeWithText("Save").assertIsEnabled().performClick()

        assertThat(created).containsExactly("Grammar")
    }

    @Test
    fun `cancel hands the reset to the caller`() {
        creating = true
        name = "Gram"
        setSheet()

        compose.onNodeWithContentDescription("Close").performClick()

        assertThat(creating).isFalse()
        assertThat(name).isEmpty()
        compose.onNodeWithTag(NAME_FIELD).assertDoesNotExist()
    }

    private companion object {
        const val NAME_FIELD = "folder_name_field"
    }
}
