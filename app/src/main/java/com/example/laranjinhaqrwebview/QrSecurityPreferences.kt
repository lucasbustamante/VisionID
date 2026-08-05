package com.example.laranjinhaqrwebview

import android.content.Context

object QrSecurityPreferences {
    private const val PREFERENCES_NAME = "visionid_security_preferences"
    private const val KEY_QR_DOMAIN_LOCK_ENABLED = "qr_domain_lock_enabled"

    fun isDomainLockEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_QR_DOMAIN_LOCK_ENABLED, true)

    fun setDomainLockEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_QR_DOMAIN_LOCK_ENABLED, enabled)
            .apply()
    }
}
