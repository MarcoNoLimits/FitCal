package com.fitcal.app.auth

import com.fitcal.app.loadPreference
import com.fitcal.app.savePreference

/**
 * The one-time "Create a free account" card shown on first open (AGENTS.md 5.2a).
 * Shown once per install, to guests only, after the age gate and consent and once the boot
 * sign-in has finished, so a returning account holder is never mistaken for a guest.
 */
object SignInCardPolicy {
    private const val KEY_SHOWN = "signin_card_shown"

    var preferenceReader: (key: String, defaultValue: String) -> String = { key, default -> loadPreference(key, default) }
    var preferenceWriter: (key: String, value: String) -> Unit = { key, value -> savePreference(key, value) }

    fun resetToDefaults() {
        preferenceReader = { key, default -> loadPreference(key, default) }
        preferenceWriter = { key, value -> savePreference(key, value) }
    }

    /**
     * @param isPermanentUser true for a signed-in account (confirmed email or linked identity)
     * @param signInSettled true once the boot sign-in has succeeded and the account type is known
     */
    fun shouldShow(isPermanentUser: Boolean, signInSettled: Boolean): Boolean =
        signInSettled && !isPermanentUser && !hasBeenShown()

    fun hasBeenShown(): Boolean = preferenceReader(KEY_SHOWN, "false").toBoolean()

    /** Call as the card appears, so it never shows a second time, even if the app is closed on it. */
    fun recordShown() {
        preferenceWriter(KEY_SHOWN, "true")
    }
}
