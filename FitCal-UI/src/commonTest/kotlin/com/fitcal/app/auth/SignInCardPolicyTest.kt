package com.fitcal.app.auth

import com.fitcal.app.ui.screens.auth.SIGN_IN_CARD_BODY
import com.fitcal.app.ui.screens.auth.SIGN_IN_CARD_TITLE
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignInCardPolicyTest {

    private val prefs = mutableMapOf<String, String>()

    @BeforeTest
    fun setUp() {
        prefs.clear()
        SignInCardPolicy.preferenceReader = { key, default -> prefs[key] ?: default }
        SignInCardPolicy.preferenceWriter = { key, value -> prefs[key] = value }
    }

    @AfterTest
    fun tearDown() {
        SignInCardPolicy.resetToDefaults()
    }

    @Test
    fun showsToAGuestOnFirstOpen() {
        assertTrue(SignInCardPolicy.shouldShow(isPermanentUser = false, signInSettled = true))
    }

    @Test
    fun showsOnlyOnce() {
        assertTrue(SignInCardPolicy.shouldShow(isPermanentUser = false, signInSettled = true))
        SignInCardPolicy.recordShown()
        assertFalse(SignInCardPolicy.shouldShow(isPermanentUser = false, signInSettled = true))
    }

    @Test
    fun neverShowsToSignedInUsers() {
        assertFalse(SignInCardPolicy.shouldShow(isPermanentUser = true, signInSettled = true))
        assertFalse(SignInCardPolicy.hasBeenShown())
    }

    @Test
    fun waitsForTheBootSignIn() {
        // Before the session is known, a returning account holder could look like a guest.
        assertFalse(SignInCardPolicy.shouldShow(isPermanentUser = false, signInSettled = false))
    }

    @Test
    fun cardCopyMatchesTheSpec() {
        assertEquals("Create a free account", SIGN_IN_CARD_TITLE)
        assertEquals(
            "No ads for your first week, plus 1 extra scan every day. Everything you log comes with you.",
            SIGN_IN_CARD_BODY,
        )
    }
}
