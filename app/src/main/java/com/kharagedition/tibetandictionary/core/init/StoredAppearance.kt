package com.kharagedition.tibetandictionary.core.init

import androidx.appcompat.app.AppCompatDelegate
import com.kharagedition.tibetandictionary.core.error.ErrorReporter
import com.kharagedition.tibetandictionary.data.model.Appearance
import com.kharagedition.tibetandictionary.data.prefs.UserPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

/**
 * Applies the stored [Appearance] as AppCompat's default night mode before the first activity is created
 * (`DictionaryApplication.onCreate`), so a cold start with Light or Dark chosen draws the splash, the installing
 * screen and the first frame in that mode. Applying it only once the prefs reached the activity recreated the
 * activity on every such start, which showed as a flash. `MainActivity` keeps applying later changes; setting the
 * mode already in force does nothing.
 */
@Singleton
class StoredAppearance @Inject constructor(
    private val prefs: UserPreferencesRepository,
    private val errors: ErrorReporter,
) {
    /**
     * Reads the stored appearance and applies it. Blocks the caller for that first read of the small prefs file (a
     * few milliseconds; DataStore then serves every later read from memory). A failed read keeps the system mode.
     */
    fun applyBlocking() {
        val appearance = try {
            runBlocking { prefs.snapshot().appearance }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errors.record(e)
            Appearance.SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(appearance.nightMode())
    }
}

/** AppCompat's night mode for this appearance. */
fun Appearance.nightMode(): Int = when (this) {
    Appearance.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    Appearance.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
    Appearance.DARK -> AppCompatDelegate.MODE_NIGHT_YES
}
