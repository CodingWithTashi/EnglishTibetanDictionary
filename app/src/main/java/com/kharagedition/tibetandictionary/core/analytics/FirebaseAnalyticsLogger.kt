package com.kharagedition.tibetandictionary.core.analytics

import android.content.Context
import androidx.core.os.bundleOf
import com.google.firebase.analytics.FirebaseAnalytics
import com.kharagedition.tibetandictionary.core.firebase.FirebaseAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** [Analytics] that forwards to Firebase Analytics; collection is off in debug builds. */
@Singleton
class FirebaseAnalyticsLogger @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val availability: FirebaseAvailability,
) : Analytics {
    override fun log(event: AnalyticsEvent) {
        if (!availability.isAvailable) return
        runCatching {
            FirebaseAnalytics.getInstance(context)
                .logEvent(event.name, bundleOf(*event.params.toList().toTypedArray()))
        }
    }
}
