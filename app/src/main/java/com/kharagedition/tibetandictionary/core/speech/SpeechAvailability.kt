package com.kharagedition.tibetandictionary.core.speech

import android.content.Context
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Whether voice search can run on this device (a speech recognition service is installed). */
fun interface SpeechAvailability {
    fun isAvailable(): Boolean
}

@Singleton
class SystemSpeechAvailability @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SpeechAvailability {
    override fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)
}
