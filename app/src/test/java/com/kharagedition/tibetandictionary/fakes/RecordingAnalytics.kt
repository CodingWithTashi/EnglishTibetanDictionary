package com.kharagedition.tibetandictionary.fakes

import com.kharagedition.tibetandictionary.core.analytics.Analytics
import com.kharagedition.tibetandictionary.core.analytics.AnalyticsEvent

/** [Analytics] that keeps every logged event in [events]. */
class RecordingAnalytics : Analytics {
    val events = mutableListOf<AnalyticsEvent>()

    override fun log(event: AnalyticsEvent) {
        events += event
    }
}
