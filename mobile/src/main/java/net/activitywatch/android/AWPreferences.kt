package net.activitywatch.android

import android.content.Context
import android.content.SharedPreferences

class AWPreferences(context: Context) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("AWPreferences", Context.MODE_PRIVATE)

    // To check if it is the first time the app is being run
    // Set to false when user finishes onboarding
    fun isFirstTime(): Boolean {
        return sharedPreferences.getBoolean("isFirstTime", true)
    }

    // To set the first time flag to false after the first run
    fun setFirstTimeRunFlag() {
        val editor = sharedPreferences.edit()
        editor.putBoolean("isFirstTime", false)
        editor.apply()
    }

    // Optional: To reset the first time flag to true (for debugging, perhaps)
    fun resetFirstTimeRunFlag() {
        val editor = sharedPreferences.edit()
        editor.putBoolean("isFirstTime", true)
        editor.apply()
    }

    // Whether the server should bind to all interfaces (0.0.0.0) so it is
    // reachable over the device's tailnet, instead of loopback only.
    // Off by default for security.
    fun isRemoteAccessEnabled(): Boolean {
        return sharedPreferences.getBoolean("remoteAccessEnabled", false)
    }

    fun setRemoteAccessEnabled(enabled: Boolean) {
        val editor = sharedPreferences.edit()
        editor.putBoolean("remoteAccessEnabled", enabled)
        editor.apply()
    }

    // Whether the server should be brought up automatically on BOOT_COMPLETED.
    // On by default — the obvious user expectation post-fork is autonomous
    // operation, but still toggleable for users who prefer manual control.
    fun isStartOnBootEnabled(): Boolean {
        return sharedPreferences.getBoolean("startOnBootEnabled", true)
    }

    fun setStartOnBootEnabled(enabled: Boolean) {
        val editor = sharedPreferences.edit()
        editor.putBoolean("startOnBootEnabled", enabled)
        editor.apply()
    }
}