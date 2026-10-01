package com.platecheck.app

import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds only: uses the App Check debug provider so emulators and dev phones work.
 * On first run, logcat (tag "DebugAppCheckProvider") prints a debug secret. Add it in
 * Firebase console > App Check > Apps > (overflow menu) > Manage debug tokens.
 * Keep that token private and never commit it.
 */
internal fun installAppCheckProvider() {
    Firebase.appCheck.installAppCheckProviderFactory(
        DebugAppCheckProviderFactory.getInstance(),
    )
}
