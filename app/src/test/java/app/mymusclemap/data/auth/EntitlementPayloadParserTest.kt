package app.mymusclemap.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class EntitlementPayloadParserTest {
    @Test
    fun additiveSpecialFieldsStayOptional() {
        val legacy = parseEntitlementPayload(
            """{"access":"FREE","founderLifetime":false,"temporaryFounderPro":false}"""
        ) as FounderEntitlementCall.Loaded
        assertEquals(false, legacy.founderLifetime)
        assertNull(legacy.founderGrantedAt)
        assertTrue(legacy.specialAchievements.isEmpty())
    }

    @Test
    fun founderTimestampAndSpecialGrantsAreParsed() {
        val loaded = parseEntitlementPayload(
            """
            {
              "access":"FREE",
              "founderLifetime":true,
              "temporaryFounderPro":false,
              "founderGrantedAt":"2024-03-01T00:00:00Z",
              "specialAchievements":[
                {"key":"EARLY_ADOPTER","grantedAt":"2026-01-15T00:00:00Z"},
                {"key":"DEVELOPER","grantedAt":"2026-02-01T00:00:00Z"},
                {"key":"FOUNDER","grantedAt":"2020-01-01T00:00:00Z"}
              ]
            }
            """.trimIndent()
        ) as FounderEntitlementCall.Loaded
        assertEquals(Instant.parse("2024-03-01T00:00:00Z"), loaded.founderGrantedAt)
        assertEquals(false, loaded.founderRecognized)
        assertNull(loaded.founderProExpiresAt)
        assertEquals(listOf("EARLY_ADOPTER", "DEVELOPER"), loaded.specialAchievements.map { it.key })
        assertEquals(Instant.parse("2026-02-01T00:00:00Z"), loaded.specialAchievements[1].grantedAt)
    }

    @Test
    fun founderProExpirationIsParsedWithoutTreatingItAsLifetime() {
        val loaded = parseEntitlementPayload(
            """
            {
              "access":"PRO",
              "founderLifetime":false,
              "temporaryFounderPro":false,
              "founderRecognized":true,
              "founderGrantedAt":"2026-03-15T18:45:01Z",
              "founderProExpiresAt":"2027-03-15T18:45:01Z"
            }
            """.trimIndent()
        ) as FounderEntitlementCall.Loaded
        assertEquals(false, loaded.founderLifetime)
        assertEquals(true, loaded.founderRecognized)
        assertEquals(Instant.parse("2027-03-15T18:45:01Z"), loaded.founderProExpiresAt)
        assertEquals(Instant.parse("2026-03-15T18:45:01Z"), loaded.founderGrantedAt)
    }

    @Test
    fun missingRequiredFlagsStillFailClosed() {
        assertNull(parseEntitlementPayload("""{"access":"PRO"}"""))
    }
}
