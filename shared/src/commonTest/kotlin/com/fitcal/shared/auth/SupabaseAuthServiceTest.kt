package com.fitcal.shared.auth

import com.fitcal.shared.telemetry.TelemetryUploader
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class SupabaseAuthServiceTest {

    private class FakeAuthService(
        var mockUserId: String? = null,
        var failuresBeforeSuccess: Int = 0
    ) : SupabaseAuthService() {
        var signInAttempts: Int = 0
        var sessionLoaded = true
        var savedSessionUserId: String? = null
        var restoreFailures = 0

        override fun getUserId(): String? = if (sessionLoaded) mockUserId else null

        override suspend fun awaitSessionLoaded() {
            sessionLoaded = true
        }

        override suspend fun restoreSavedSession(): String? {
            val saved = savedSessionUserId ?: return null
            if (restoreFailures-- > 0) throw RuntimeException("Simulated refresh failure")
            mockUserId = saved
            return saved
        }

        override suspend fun signInAnonymously(): String {
            signInAttempts++
            if (signInAttempts <= failuresBeforeSuccess) {
                throw RuntimeException("Simulated auth failure #$signInAttempts")
            }
            val id = "anon-user-$signInAttempts"
            mockUserId = id
            return id
        }
    }

    @BeforeTest
    fun setup() {
        TelemetryUploader.resetForTesting()
    }

    @AfterTest
    fun tearDown() {
        TelemetryUploader.resetForTesting()
    }

    @Test
    fun testEnsureSignedInReturnsExistingUserIdWithoutSigningIn() = runBlocking {
        val fake = FakeAuthService(mockUserId = "cached-user-999")
        val userId = fake.ensureSignedIn()

        assertEquals("cached-user-999", userId)
        assertEquals(0, fake.signInAttempts)
        assertEquals("cached-user-999", TelemetryUploader.userIdProvider())
    }

    @Test
    fun testEnsureSignedInCallsSignInAnonymouslyWhenNoUser() = runBlocking {
        val fake = FakeAuthService(mockUserId = null, failuresBeforeSuccess = 0)
        val userId = fake.ensureSignedIn()

        assertEquals("anon-user-1", userId)
        assertEquals(1, fake.signInAttempts)
        assertEquals("anon-user-1", TelemetryUploader.userIdProvider())
    }

    @Test
    fun testEnsureSignedInRetriesAndSucceedsOnSecondAttempt() = runBlocking {
        val fake = FakeAuthService(mockUserId = null, failuresBeforeSuccess = 1)
        val reportedFailures = mutableListOf<Pair<Int, String>>()

        val userId = fake.ensureSignedIn(
            maxAttempts = 3,
            backoffsMs = listOf(1L, 1L),
            onAttemptFailed = { attempt, err ->
                reportedFailures.add(attempt to (err.message ?: ""))
            }
        )

        assertEquals("anon-user-2", userId)
        assertEquals(2, fake.signInAttempts)
        assertEquals(1, reportedFailures.size)
        assertEquals(1, reportedFailures[0].first)
        assertTrue(reportedFailures[0].second.contains("Simulated auth failure #1"))
        assertEquals("anon-user-2", TelemetryUploader.userIdProvider())
    }

    @Test
    fun testEnsureSignedInThrowsAfterExhaustingMaxAttempts() = runBlocking {
        val fake = FakeAuthService(mockUserId = null, failuresBeforeSuccess = 10)
        val reportedFailures = mutableListOf<Int>()

        val ex = assertFailsWith<RuntimeException> {
            fake.ensureSignedIn(
                maxAttempts = 3,
                backoffsMs = listOf(1L, 1L),
                onAttemptFailed = { attempt, _ ->
                    reportedFailures.add(attempt)
                }
            )
        }

        assertTrue(ex.message?.contains("Simulated auth failure #3") == true)
        assertEquals(3, fake.signInAttempts)
        assertEquals(listOf(1, 2, 3), reportedFailures)
    }

    @Test
    fun keepsTheSavedAccountWhenTheSessionIsStillLoading() = runBlocking {
        // Startup race: the account is saved but not loaded yet. Must not become a guest.
        val fake = FakeAuthService(mockUserId = "account-123").apply { sessionLoaded = false }
        assertEquals("account-123", fake.ensureSignedIn())
        assertEquals(0, fake.signInAttempts)
    }

    @Test
    fun retriesTheSavedAccountInsteadOfCreatingAGuest() = runBlocking {
        // Refresh failed earlier (offline): the saved account comes back, no guest is made.
        val fake = FakeAuthService().apply { savedSessionUserId = "account-123"; restoreFailures = 1 }
        assertEquals("account-123", fake.ensureSignedIn(maxAttempts = 2, backoffsMs = listOf(0L)))
        assertEquals(0, fake.signInAttempts)
    }
}
