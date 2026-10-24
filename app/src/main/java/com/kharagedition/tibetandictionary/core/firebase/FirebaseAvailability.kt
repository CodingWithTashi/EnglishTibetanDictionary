package com.kharagedition.tibetandictionary.core.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Whether Firebase can be used at all. Every Firebase call goes through a class that checks this first. */
@Singleton
class FirebaseAvailability @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /** False where the default FirebaseApp was not created (Robolectric, missing google-services). */
    val isAvailable: Boolean by lazy { FirebaseApp.getApps(context).isNotEmpty() }
}
