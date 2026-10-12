package app.mymusclemap.data.founder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.data.auth.FounderEntitlementCall
import app.mymusclemap.data.auth.OkHttpStrictBackendApi
import app.mymusclemap.data.auth.StoredStrictSession
import app.mymusclemap.data.auth.StrictBearerToken
import app.mymusclemap.data.auth.StrictSessionStore
import app.mymusclemap.data.preferences.FounderEntitlementCache
import app.mymusclemap.domain.achievements.AccountAchievementAuthority
import app.mymusclemap.domain.achievements.AchievementBoardAssembler
import app.mymusclemap.domain.achievements.AchievementId
import app.mymusclemap.domain.billing.VerifiedPaidSubscription
import app.mymusclemap.domain.billing.paidSubscriptionEntitlement
import app.mymusclemap.domain.entitlement.EntitlementResolver
import app.mymusclemap.domain.entitlement.EntitlementSources
import app.mymusclemap.domain.entitlement.FounderProgramStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class AccountAuthorityRefreshTest {
    private val userId = "8b26550a-0cb2-4b49-a1b9-a536b479bbe7"
    private val now = Instant.parse("2026-10-11T19:00:00Z")
    private val early = Instant.parse("2026-01-15T00:00:00Z")
    private val developer = Instant.parse("2026-02-01T00:00:00Z")
    private val zone = ZoneOffset.UTC

    @Test
    fun authenticatedNonFounderReceivesSpecialGrantsWithoutFounderOrPro() = runBlocking {
        withRefresh(founderOwned = false, workoutsPending = true) { server, sessions, cache, refresh, applied, scheduled ->
            sessions.write(session())
            server.enqueue(specialsResponse())
            refresh.refresh()

            assertEquals(1, server.requestCount)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/api/v1/entitlements", request.path)
            assertEquals("Bearer bearer-token", request.getHeader("Authorization"))
            assertTrue(applied.isEmpty())
            assertEquals(0, scheduled.get())

            val trusted = cache.current().trusted(now)
            assertEquals(listOf("EARLY_ADOPTER", "DEVELOPER"), trusted.specialGrants.map { it.key })
            assertEquals(early, trusted.specialGrants[0].grantedAt)
            assertEquals(developer, trusted.specialGrants[1].grantedAt)
            assertFalse(trusted.founderLifetime)
            assertFalse(trusted.founderRecognized)
            assertFalse(trusted.temporaryFounderPro)
            val specialsOnly = EntitlementResolver.resolve(
                EntitlementSources.of(backendFounder = trusted),
                now
            )
            assertFalse(specialsOnly.grantsPro)
            assertFalse(specialsOnly.founderLifetime)
            assertFalse(specialsOnly.founderRecognized)

            val paid = paidSubscriptionEntitlement(
                VerifiedPaidSubscription(
                    productId = "debug.local.pro",
                    expiresAt = now.plusSeconds(3_600),
                    autoRenewing = false,
                    state = "DEBUG_LOCAL",
                    entitled = true
                )
            )
            val withSubscription = EntitlementResolver.resolve(
                EntitlementSources.of(subscription = paid, backendFounder = trusted),
                now
            )
            assertTrue(withSubscription.grantsPro)
            assertTrue(withSubscription.subscriptionValid)
            assertFalse(withSubscription.founderLifetime)
            assertFalse(withSubscription.founderRecognized)
            assertFalse(withSubscription.temporaryTesterPro)

            val board = AchievementBoardAssembler.assemble(
                completedWorkoutCount = 0,
                unlocks = emptyList(),
                events = emptyList(),
                grantsPro = withSubscription.grantsPro,
                accountAuthority = AccountAchievementAuthority(
                    earlyAdopterGrantedAtMillis = trusted.specialGrantedAtMillis("EARLY_ADOPTER"),
                    developerGrantedAtMillis = trusted.specialGrantedAtMillis("DEVELOPER")
                )
            )
            assertTrue(board.items.single { it.id == AchievementId.EARLY_ADOPTER }.unlocked)
            assertTrue(board.items.single { it.id == AchievementId.DEVELOPER }.unlocked)
            assertFalse(board.items.single { it.id == AchievementId.FOUNDER }.unlocked)
            assertEquals(early.toEpochMilli(), board.items.single { it.id == AchievementId.EARLY_ADOPTER }.unlockedAt)
            assertTrue(board.pending.isEmpty())
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun noSessionDoesNotFetchEntitlementsOrDropTheCache() = runBlocking {
        withRefresh(founderOwned = false, workoutsPending = true) { server, sessions, cache, refresh, _, scheduled ->
            cache.save(
                temporaryFounderPro = false,
                founderLifetime = false,
                validUntil = now.plus(FounderEntitlementCache.TRUST),
                userId = userId,
                specialGrants = listOf(
                    app.mymusclemap.domain.entitlement.SpecialAchievementGrant("EARLY_ADOPTER", early),
                    app.mymusclemap.domain.entitlement.SpecialAchievementGrant("DEVELOPER", developer)
                )
            )
            refresh.refresh()
            assertEquals(0, server.requestCount)
            assertEquals(0, scheduled.get())
            assertNull(sessions.read())
            sessions.write(session())
            val trusted = cache.current().trusted(now)
            assertEquals(listOf("EARLY_ADOPTER", "DEVELOPER"), trusted.specialGrants.map { it.key })
            assertFalse(trusted.founderLifetime)
        }
    }

    @Test
    fun founderParticipantReceivesTheSnapshotAndEntitlements() = runBlocking {
        withRefresh(founderOwned = true, workoutsPending = true) { server, sessions, cache, refresh, applied, scheduled ->
            sessions.write(session())
            server.enqueue(founderResponse(temporaryPro = true))
            server.enqueue(
                specialsResponse(
                    temporaryFounderPro = true,
                    founderRecognized = false
                )
            )
            refresh.refresh()

            assertEquals(listOf("/api/v1/founder", "/api/v1/entitlements"), paths(server))
            assertEquals(FounderProgramStatus.ActivePro, applied.single().status)
            assertTrue(applied.single().temporaryProActive)
            assertEquals(1, scheduled.get())
            val trusted = cache.current().trusted(now)
            assertTrue(trusted.temporaryFounderPro)
            assertFalse(trusted.founderLifetime)
            assertEquals(listOf("EARLY_ADOPTER", "DEVELOPER"), trusted.specialGrants.map { it.key })
            val resolved = EntitlementResolver.resolve(
                EntitlementSources.of(backendFounder = trusted),
                now
            )
            assertTrue(resolved.temporaryTesterPro)
            assertTrue(resolved.grantsPro)
            assertFalse(resolved.founderLifetime)
        }
    }

    @Test
    fun founderSnapshotMissStillStoresAccountEntitlements() = runBlocking {
        withRefresh(founderOwned = true, workoutsPending = false) { server, sessions, cache, refresh, applied, scheduled ->
            sessions.write(session())
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(specialsResponse())
            refresh.refresh()

            assertEquals(listOf("/api/v1/founder", "/api/v1/entitlements"), paths(server))
            assertTrue(applied.isEmpty())
            assertEquals(0, scheduled.get())
            assertEquals("bearer-token", sessions.read()?.accessToken?.value)
            assertEquals(listOf("EARLY_ADOPTER", "DEVELOPER"), cache.current().specialGrants.map { it.key })
            assertFalse(cache.current().founderLifetime)
        }
    }

    @Test
    fun failedNetworkRefreshPreservesTheTrustedCache() = runBlocking {
        withRefresh(founderOwned = false, workoutsPending = false) { server, sessions, cache, refresh, _, _ ->
            sessions.write(session())
            cache.save(
                temporaryFounderPro = false,
                founderLifetime = false,
                validUntil = now.plus(FounderEntitlementCache.TRUST),
                userId = userId,
                specialGrants = listOf(
                    app.mymusclemap.domain.entitlement.SpecialAchievementGrant("DEVELOPER", developer)
                )
            )
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            refresh.refresh()

            assertEquals(1, server.requestCount)
            assertEquals("bearer-token", sessions.read()?.accessToken?.value)
            val trusted = cache.current().trusted(now)
            assertEquals(listOf("DEVELOPER"), trusted.specialGrants.map { it.key })
            assertEquals(developer, trusted.specialGrants.single().grantedAt)
            assertFalse(trusted.founderLifetime)
            assertFalse(
                EntitlementResolver.resolve(EntitlementSources.of(backendFounder = trusted), now).grantsPro
            )
        }
    }

    @Test
    fun unauthorizedEntitlementResponseClearsTheSessionAndDropsTheCache() = runBlocking {
        withRefresh(founderOwned = false, workoutsPending = false) { server, sessions, cache, refresh, _, _ ->
            sessions.write(session())
            cache.save(
                temporaryFounderPro = false,
                founderLifetime = false,
                validUntil = now.plus(FounderEntitlementCache.TRUST),
                userId = userId,
                specialGrants = listOf(
                    app.mymusclemap.domain.entitlement.SpecialAchievementGrant("EARLY_ADOPTER", early)
                )
            )
            server.enqueue(MockResponse().setResponseCode(401))
            refresh.refresh()

            assertEquals(1, server.requestCount)
            assertEquals("/api/v1/entitlements", server.takeRequest().path)
            assertNull(sessions.read())
            sessions.write(session())
            assertTrue(cache.current().specialGrants.isEmpty())
            assertFalse(cache.current().founderLifetime)
        }
    }

    private fun paths(server: MockWebServer): List<String> {
        return List(server.requestCount) { server.takeRequest().path!! }
    }

    private suspend fun withRefresh(
        founderOwned: Boolean,
        workoutsPending: Boolean,
        block: suspend (
            MockWebServer,
            MemorySession,
            FounderEntitlementCache,
            AccountAuthorityRefresh,
            MutableList<BackendFounderSnapshot>,
            java.util.concurrent.atomic.AtomicInteger
        ) -> Unit
    ) {
        val server = MockWebServer()
        server.start()
        val sessions = MemorySession()
        val http = OkHttpClient.Builder()
            .callTimeout(2, TimeUnit.SECONDS)
            .build()
        val api = OkHttpStrictBackendApi(server.url("/").toString(), http, sessions)
        val cache = FounderEntitlementCache(
            context = ApplicationProvider.getApplicationContext<Context>(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            sessionUserId = { sessions.read()?.userId }
        )
        sessions.onCleared = { cache.drop() }
        val applied = mutableListOf<BackendFounderSnapshot>()
        val scheduled = java.util.concurrent.atomic.AtomicInteger()
        val refresh = AccountAuthorityRefresh(
            api = api,
            zone = zone,
            founderBackendOwned = { founderOwned },
            applyFounderSnapshot = { applied += it },
            saveEntitlements = { loaded -> save(cache, sessions, loaded) },
            dropEntitlementCache = { cache.drop() },
            founderWorkoutsPending = { workoutsPending },
            scheduleFounderWorkoutSync = { scheduled.incrementAndGet() }
        )
        try {
            block(server, sessions, cache, refresh, applied, scheduled)
        } finally {
            server.shutdown()
            http.dispatcher.executorService.shutdown()
            http.connectionPool.evictAll()
        }
    }

    private suspend fun save(
        cache: FounderEntitlementCache,
        sessions: MemorySession,
        loaded: FounderEntitlementCall.Loaded
    ) {
        val signedIn = sessions.read()?.userId ?: return
        cache.save(
            temporaryFounderPro = loaded.temporaryFounderPro,
            founderLifetime = loaded.founderLifetime,
            validUntil = now.plus(FounderEntitlementCache.TRUST),
            userId = signedIn,
            founderGrantedAt = loaded.founderGrantedAt,
            specialGrants = loaded.specialAchievements,
            founderRecognized = loaded.founderRecognized,
            founderProExpiresAt = loaded.founderProExpiresAt
        )
    }

    private fun session(): StoredStrictSession {
        return StoredStrictSession(StrictBearerToken("bearer-token"), "2026-11-11T00:00:00Z", userId)
    }

    private fun specialsResponse(
        temporaryFounderPro: Boolean = false,
        founderRecognized: Boolean = false
    ): MockResponse {
        return MockResponse().setBody(
            """
            {
              "access":"FREE",
              "founderLifetime":false,
              "temporaryFounderPro":$temporaryFounderPro,
              "founderRecognized":$founderRecognized,
              "specialAchievements":[
                {"key":"EARLY_ADOPTER","grantedAt":"$early"},
                {"key":"DEVELOPER","grantedAt":"$developer"},
                {"key":"FOUNDER","grantedAt":"2020-01-01T00:00:00Z"}
              ]
            }
            """.trimIndent()
        )
    }

    private fun founderResponse(temporaryPro: Boolean): MockResponse {
        return MockResponse().setBody(
            """
            {
              "status":"ACTIVE_PRO",
              "enrolledAt":"2026-06-01T00:00:00Z",
              "deadlineAt":"2026-07-16T00:00:00Z",
              "progress":{
                "qualifyingWorkouts":4,
                "requiredWorkouts":10,
                "distinctWorkoutDays":3,
                "requiredDistinctWorkoutDays":6
              },
              "temporaryProActive":$temporaryPro,
              "trainingRequirementsComplete":false,
              "feedback":{"required":true,"submitted":false},
              "testerReport":{"required":true,"submitted":false},
              "nextAction":"KEEP_TRAINING"
            }
            """.trimIndent()
        )
    }

    private class MemorySession : StrictSessionStore {
        private var current: StoredStrictSession? = null
        var onCleared: (() -> Unit)? = null

        override fun read(): StoredStrictSession? = current

        override fun write(session: StoredStrictSession) {
            current = session
        }

        override fun clear() {
            current = null
            onCleared?.invoke()
        }
    }
}
