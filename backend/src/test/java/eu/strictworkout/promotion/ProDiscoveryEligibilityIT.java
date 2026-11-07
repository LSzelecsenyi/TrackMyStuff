package eu.strictworkout.promotion;

import eu.strictworkout.identity.ScriptedIdentityVerifier;
import eu.strictworkout.identity.ScriptedIdentityVerifierConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.json.BasicJsonParser;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "strict.auth.session-lifetime=P365D")
@Import(ScriptedIdentityVerifierConfig.class)
@Testcontainers
class ProDiscoveryEligibilityIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @LocalServerPort
    private int port;

    @Autowired
    private ScriptedIdentityVerifier identities;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProDiscoveryActivityCleanup cleanup;

    @BeforeEach
    void proDiscoveryIsOpen() {
        jdbc.update("update promotional_program set lifecycle = 'ACTIVE' where program = 'PRO_DISCOVERY'");
    }

    @Test
    @DisplayName("Given a user with no completed native workouts, when activation is attempted directly, then the backend rejects the grant")
    void noWorkoutsCannotActivate() {
        String token = login("none-" + UUID.randomUUID());
        ResponseEntity<String> activation = post("/api/v1/promotions/pro-discovery/activate", "{}", token);
        assertEquals(HttpStatus.CONFLICT, activation.getStatusCode());
        assertEquals("NOT_ELIGIBLE", parse(activation).get("status"));
        assertEquals(0, trials(token));
    }

    @Test
    @DisplayName("Given one native workout older than six months, when eligibility is evaluated, then the user is not eligible")
    void oneOldWorkoutIsNotEnough() {
        String token = login("one-" + UUID.randomUUID());
        union(token, List.of(today().minusYears(2).toString()));
        assertEquals("NOT_ELIGIBLE", status(token).get("status"));
        assertEquals(1, number(status(token), "qualifyingDays"));
    }

    @Test
    @DisplayName("Given 24 distinct workout days but the first workout is younger than six calendar months, when eligibility is evaluated, then the user is not eligible")
    void twentyFourYoungDaysAreNotEnough() {
        String token = login("young-" + UUID.randomUUID());
        LocalDate first = today().minusDays(40);
        List<String> dates = new ArrayList<>();
        for (int index = 0; index < 24; index++) {
            dates.add(first.plusDays(index).toString());
        }
        union(token, dates);
        Map<String, Object> body = status(token);
        assertEquals("NOT_ELIGIBLE", body.get("status"));
        assertEquals(24, number(body, "qualifyingDays"));
        assertTrue(LocalDate.parse(String.valueOf(body.get("matureOn"))).isAfter(today()));
    }

    @Test
    @DisplayName("Given 24 distinct workout days older than six months but no workout in the last 30 days, when eligibility is evaluated, then the user is not eligible")
    void noRecentActivityIsNotEnough() {
        String token = login("stale-" + UUID.randomUUID());
        union(token, oldDays(24));
        Map<String, Object> body = status(token);
        assertEquals("NOT_ELIGIBLE", body.get("status"));
        assertEquals(0, number(body, "recentDays"));
    }

    @Test
    @DisplayName("Given at least six calendar months, 24 distinct workout days, and two distinct recent days, when eligibility is evaluated, then the user is eligible")
    void aQualifyingHistoryIsEligible() {
        String token = login("ready-" + UUID.randomUUID());
        union(token, qualifyingDays());
        Map<String, Object> body = status(token);
        assertEquals("ELIGIBLE", body.get("status"));
        assertEquals(24, number(body, "qualifyingDays"));
        assertTrue(number(body, "recentDays") >= 2);
    }

    @Test
    @DisplayName("Given two completed workouts on the same date, when activity is synchronized, then they count as one qualifying workout day")
    void theSameDateCountsOnce() {
        String token = login("same-" + UUID.randomUUID());
        union(token, List.of("2020-01-01", "2020-01-01"));
        assertEquals(1, number(status(token), "qualifyingDays"));
        assertEquals(1, count("select count(*) from pro_discovery_device_day where user_id = ?::uuid", userId(token)));
    }

    @Test
    @DisplayName("Given a completed workout is deleted, when activity is synchronized, then the corresponding day disappears if no other qualifying workout exists on that date")
    void aDeletedDayIsRemoved() {
        String token = login("delete-day-" + UUID.randomUUID());
        long revision = revision(union(token, List.of("2020-01-01", "2020-01-02")));
        replace(token, revision, List.of("2020-01-01"));
        assertEquals(1, number(status(token), "qualifyingDays"));
        assertEquals(0, count(
                "select count(*) from pro_discovery_device_day where user_id = ?::uuid and workout_date = '2020-01-02'",
                userId(token)
        ));
    }

    @Test
    @DisplayName("Given a qualifying user, when they manually activate Pro Discovery, then exactly one 14-day grant is created")
    void manualActivationCreatesOneGrant() {
        String token = login("grant-" + UUID.randomUUID());
        union(token, qualifyingDays());
        Instant before = Instant.now();
        ResponseEntity<String> activation = post("/api/v1/promotions/pro-discovery/activate", "{}", token);
        assertEquals(HttpStatus.OK, activation.getStatusCode(), activation.getBody());
        assertEquals("ACTIVE", parse(activation).get("status"));
        Instant expires = Instant.parse(String.valueOf(parse(activation).get("expiresAt")));
        assertFalse(expires.isBefore(before.plus(PromotionalTrialService.LENGTH).minusSeconds(5)));
        assertFalse(expires.isAfter(Instant.now().plus(PromotionalTrialService.LENGTH).plusSeconds(5)));
        assertEquals(1, trials(token));
        assertEquals(0, count("select count(*) from pro_discovery_device_day where user_id = ?::uuid", userId(token)));
        assertEquals(0, count("select count(*) from pro_discovery_device where user_id = ?::uuid", userId(token)));
    }

    @Test
    @DisplayName("Given two concurrent activation requests, when both reach the backend, then the original grant is returned without extension")
    void concurrentActivationDoesNotExtend() throws Exception {
        String token = login("race-" + UUID.randomUUID());
        union(token, qualifyingDays());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<String>> first = pool.submit(() -> post("/api/v1/promotions/pro-discovery/activate", "{}", token));
            Future<ResponseEntity<String>> second = pool.submit(() -> post("/api/v1/promotions/pro-discovery/activate", "{}", token));
            String expires = String.valueOf(parse(first.get()).get("expiresAt"));
            String again = String.valueOf(parse(second.get()).get("expiresAt"));
            assertEquals(expires, again);
            assertEquals(1, trials(token));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Given an active trial and a fresh installation, when the user signs in, then the original trial is restored with its original expiry")
    void statusRestoresTheOriginalExpiry() {
        String token = login("restore-" + UUID.randomUUID());
        union(token, qualifyingDays());
        String expires = String.valueOf(parse(post("/api/v1/promotions/pro-discovery/activate", "{}", token)).get("expiresAt"));
        Map<String, Object> restored = status(token);
        assertEquals("ACTIVE_TRIAL", restored.get("status"));
        assertEquals(expires, restored.get("expiresAt"));
    }

    @Test
    @DisplayName("Given a used trial followed by account deletion and re-registration with the same Google identity, when activation is attempted, then the user cannot claim another trial")
    void deletionDoesNotAllowAnotherTrial() {
        String subject = "reuse-" + UUID.randomUUID();
        String token = login(subject);
        union(token, qualifyingDays());
        assertEquals(HttpStatus.OK, post("/api/v1/promotions/pro-discovery/activate", "{}", token).getStatusCode());
        identities.accept("delete-" + subject, subject, subject + "@example.com", true);
        ResponseEntity<String> deleted = send(
                HttpMethod.DELETE,
                "/api/v1/account",
                "{\"idToken\":\"delete-" + subject + "\",\"confirmed\":true}",
                token
        );
        assertEquals(HttpStatus.NO_CONTENT, deleted.getStatusCode(), deleted.getBody());
        String returned = login(subject + "-again", subject);
        union(returned, qualifyingDays());
        ResponseEntity<String> activation = post("/api/v1/promotions/pro-discovery/activate", "{}", returned);
        assertEquals(HttpStatus.CONFLICT, activation.getStatusCode(), activation.getBody());
        assertEquals("ALREADY_USED", parse(activation).get("status"));
        assertEquals(0, trials(returned));
    }

    @Test
    @DisplayName("Given a program in INACTIVE or RETIRED, when activation is attempted, then no new grant is issued")
    void closedProgramsDoNotGrant() {
        String inactive = login("inactive-" + UUID.randomUUID());
        union(inactive, qualifyingDays());
        jdbc.update("update promotional_program set lifecycle = 'INACTIVE' where program = 'PRO_DISCOVERY'");
        ResponseEntity<String> paused = post("/api/v1/promotions/pro-discovery/activate", "{}", inactive);
        assertEquals(HttpStatus.FORBIDDEN, paused.getStatusCode());
        assertEquals("CLOSED", parse(paused).get("status"));
        jdbc.update("update promotional_program set lifecycle = 'RETIRED' where program = 'PRO_DISCOVERY'");
        String retired = login("retired-" + UUID.randomUUID());
        union(retired, qualifyingDays());
        ResponseEntity<String> closed = post("/api/v1/promotions/pro-discovery/activate", "{}", retired);
        assertEquals(HttpStatus.FORBIDDEN, closed.getStatusCode());
        assertEquals("CLOSED", parse(closed).get("status"));
        assertEquals(0, trials(inactive) + trials(retired));
    }

    @Test
    @DisplayName("Given an existing legitimate trial, when the new implementation is deployed, then its original expiry remains unchanged")
    void anExistingTrialKeepsItsExpiry() {
        String token = login("legacy-row-" + UUID.randomUUID());
        Instant expires = Instant.parse("2026-12-01T00:00:00Z");
        jdbc.update(
                "insert into promotional_trial (user_id, promotion_type, activated_at, expires_at) values (?::uuid, 'PRO_DISCOVERY', ?::timestamptz, ?::timestamptz)",
                userId(token),
                "2026-11-17T00:00:00Z",
                "2026-12-01T00:00:00Z"
        );
        assertEquals(expires.toString(), status(token).get("expiresAt"));
        cleanup.cleanup();
        assertEquals("2026-12-01T00:00:00Z", jdbc.queryForObject(
                "select to_char(expires_at at time zone 'utc', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"') from promotional_trial where user_id = ?::uuid",
                String.class,
                userId(token)
        ));
    }

    @Test
    @DisplayName("Given a stale activity synchronization request, when a newer state has already been applied, then the stale request cannot resurrect deleted days")
    void aStaleSnapshotCannotResurrectADay() {
        String token = login("stale-sync-" + UUID.randomUUID());
        long first = revision(union(token, List.of("2020-03-01", "2020-03-02")));
        long second = revision(replace(token, first, List.of("2020-03-01")));
        ResponseEntity<String> stale = put(token, deviceBody(first, List.of("2020-03-01", "2020-03-02")));
        assertEquals(HttpStatus.CONFLICT, stale.getStatusCode(), stale.getBody());
        assertEquals("ACTIVITY_REVISION_STALE", parse(stale).get("errorCode"));
        assertEquals(second, number(parse(stale), "activityRevision"));
        assertEquals(0, count(
                "select count(*) from pro_discovery_device_day where user_id = ?::uuid and workout_date = '2020-03-02'",
                userId(token)
        ));
    }

    @Test
    @DisplayName("Given the account is deleted, when deletion completes, then Pro Discovery activity data is removed")
    void accountDeletionRemovesActivity() {
        String subject = "activity-delete-" + UUID.randomUUID();
        String token = login(subject);
        union(token, List.of("2020-04-01", "2020-04-02"));
        String userId = userId(token);
        identities.accept("delete-" + subject, subject, subject + "@example.com", true);
        ResponseEntity<String> deleted = send(
                HttpMethod.DELETE,
                "/api/v1/account",
                "{\"idToken\":\"delete-" + subject + "\",\"confirmed\":true}",
                token
        );
        assertEquals(HttpStatus.NO_CONTENT, deleted.getStatusCode(), deleted.getBody());
        assertEquals(0, count("select count(*) from pro_discovery_device_day where user_id = ?::uuid", userId));
        assertEquals(0, count("select count(*) from pro_discovery_device where user_id = ?::uuid", userId));
        assertEquals(0, count("select count(*) from pro_discovery_activity_permission where user_id = ?::uuid", userId));
        assertEquals(0, count("select count(*) from pro_discovery_activity_day where user_id = ?::uuid", userId));
        assertEquals(0, count("select count(*) from pro_discovery_activity_sync where user_id = ?::uuid", userId));
    }

    @Test
    @DisplayName("Given a trial has been activated and its activity records are no longer needed, when cleanup runs, then detailed activity data is removed while the trial remains")
    void cleanupRemovesActivityAfterATrial() {
        String token = login("cleanup-" + UUID.randomUUID());
        jdbc.update(
                "insert into promotional_trial (user_id, promotion_type, activated_at, expires_at) values (?::uuid, 'PRO_DISCOVERY', now(), now() + interval '14 days')",
                userId(token)
        );
        jdbc.update(
                "insert into pro_discovery_activity_day (user_id, workout_date, historical, server_first_observed_at) values (?::uuid, '2020-05-01', true, now())",
                userId(token)
        );
        jdbc.update(
                "insert into pro_discovery_activity_sync (user_id, revision, updated_at) values (?::uuid, 4, now())",
                userId(token)
        );
        assertTrue(cleanup.cleanup() >= 2);
        assertEquals(1, trials(token));
        assertEquals(0, count("select count(*) from pro_discovery_activity_day where user_id = ?::uuid", userId(token)));
        assertEquals(0, count("select count(*) from pro_discovery_activity_sync where user_id = ?::uuid", userId(token)));
    }

    @Test
    @DisplayName("Given a legacy device-trial migration request, when it attempts to bypass entitlement conflicts or create a fresh unauthorized grant, then it cannot issue unauthorized Pro access")
    void migrateCannotMintAFreshGrant() {
        String token = login("migrate-" + UUID.randomUUID());
        Instant now = Instant.now();
        String body = "{\"activatedAt\":\"" + now.minusSeconds(60) + "\",\"expiresAt\":\"" + now.plusSeconds(86_400) + "\"}";
        ResponseEntity<String> fresh = post("/api/v1/promotions/pro-discovery/migrate", body, token);
        assertEquals(HttpStatus.CONFLICT, fresh.getStatusCode(), fresh.getBody());
        assertEquals("INVALID", parse(fresh).get("status"));
        assertEquals(0, trials(token));

        jdbc.update(
                """
                insert into founder_application (
                    id, user_id, status, enrolled_at, deadline_at, created_at, updated_at
                ) values (?, ?::uuid, 'PENDING_APPROVAL', now(), now() + interval '30 days', now(), now())
                """,
                UUID.randomUUID(),
                userId(token)
        );
        ResponseEntity<String> blocked = post("/api/v1/promotions/pro-discovery/migrate", body, token);
        assertEquals(HttpStatus.CONFLICT, blocked.getStatusCode(), blocked.getBody());
        assertEquals("INCOMPATIBLE", parse(blocked).get("status"));
        assertEquals(0, trials(token));
    }

    @Test
    @DisplayName("Given an invalid future date or oversized activity payload, when synchronization is attempted, then validation rejects it safely")
    void invalidActivityIsRejected() {
        String token = login("invalid-" + UUID.randomUUID());
        ResponseEntity<String> future = put(token, "{\"baseRevision\":0,\"mode\":\"UNION\",\"dates\":[\"" + today().plusDays(5) + "\"]}");
        assertEquals(HttpStatus.BAD_REQUEST, future.getStatusCode(), future.getBody());
        assertEquals("ACTIVITY_INVALID", parse(future).get("errorCode"));
        StringBuilder dates = new StringBuilder("[");
        for (int index = 0; index < ProDiscoveryEligibility.MAX_DATES + 1; index++) {
            if (index > 0) {
                dates.append(',');
            }
            dates.append('"').append(LocalDate.of(2010, 1, 1).plusDays(index)).append('"');
        }
        dates.append(']');
        ResponseEntity<String> oversized = put(token, "{\"baseRevision\":0,\"mode\":\"UNION\",\"dates\":" + dates + "}");
        assertEquals(HttpStatus.BAD_REQUEST, oversized.getStatusCode());
        assertEquals(0, count("select count(*) from pro_discovery_activity_day where user_id = ?::uuid", userId(token)));
    }

    @Test
    @DisplayName("Given a device timezone or clock manipulation, when eligibility is evaluated, then server-side evaluation cannot be bypassed solely by changing the device clock")
    void theDeviceClockDoesNotDecideEligibility() {
        String token = login("clock-" + UUID.randomUUID());
        ResponseEntity<String> shifted = put(
                token,
                "{\"baseRevision\":0,\"mode\":\"UNION\",\"dates\":[\"" + today().plusYears(1) + "\"]}"
        );
        assertEquals(HttpStatus.BAD_REQUEST, shifted.getStatusCode(), shifted.getBody());
        union(token, List.of(today().toString()));
        Map<String, Object> body = status(token);
        assertEquals("NOT_ELIGIBLE", body.get("status"));
        assertEquals(today().toString(), body.get("evaluationDate"));
        assertNotEquals(today().plusYears(1).toString(), body.get("evaluationDate"));
    }

    private final UUID installation = UUID.randomUUID();
    private final UUID otherInstallation = UUID.randomUUID();
    private long generation;

    @Test
    @DisplayName("Given a new account with default settings, when activity synchronization is attempted, then no activity dates are stored")
    void aNewAccountDoesNotUploadActivity() {
        String token = login("default-off-" + UUID.randomUUID());
        ResponseEntity<String> rejected = put(token, deviceBody(0, List.of("2020-06-01")));
        assertEquals(HttpStatus.FORBIDDEN, rejected.getStatusCode(), rejected.getBody());
        assertEquals("ACTIVITY_NOT_PERMITTED", parse(rejected).get("errorCode"));
        assertEquals(0, count("select count(*) from pro_discovery_device_day where user_id = ?::uuid", userId(token)));
    }

    @Test
    @DisplayName("Given synchronization is enabled, when the user withdraws permission, then backend activity data is deleted and future uploads are rejected")
    void withdrawalDeletesActivityAndRejectsUploads() {
        String token = login("withdraw-" + UUID.randomUUID());
        union(token, List.of("2020-06-01", "2020-06-02"));
        long grantedGeneration = generation;
        ResponseEntity<String> withdrawn = post(
                "/api/v1/promotions/pro-discovery/activity/permission",
                "{\"enabled\":false,\"scopeVersion\":1}",
                token
        );
        assertEquals(HttpStatus.OK, withdrawn.getStatusCode(), withdrawn.getBody());
        assertEquals(false, Boolean.parseBoolean(String.valueOf(parse(withdrawn).get("enabled"))));
        assertEquals(0, count("select count(*) from pro_discovery_device_day where user_id = ?::uuid", userId(token)));
        assertEquals(0, count("select count(*) from pro_discovery_device where user_id = ?::uuid", userId(token)));
        generation = grantedGeneration;
        ResponseEntity<String> replay = put(token, deviceBody(1, List.of("2020-06-01", "2020-06-02")));
        assertEquals(HttpStatus.FORBIDDEN, replay.getStatusCode(), replay.getBody());
        assertEquals(0, count("select count(*) from pro_discovery_device_day where user_id = ?::uuid", userId(token)));
        ResponseEntity<String> again = post(
                "/api/v1/promotions/pro-discovery/activity/permission",
                "{\"enabled\":false,\"scopeVersion\":1}",
                token
        );
        assertEquals(HttpStatus.OK, again.getStatusCode(), again.getBody());
        assertEquals(number(parse(withdrawn), "generation"), number(parse(again), "generation"));
    }

    @Test
    @DisplayName("Given an old upload request races with withdrawal, when withdrawal completes, then the old request cannot recreate activity data")
    void anOldGenerationCannotRecreateWithdrawnActivity() {
        String token = login("generation-race-" + UUID.randomUUID());
        union(token, List.of("2020-07-01"));
        long oldGeneration = generation;
        post("/api/v1/promotions/pro-discovery/activity/permission", "{\"enabled\":false,\"scopeVersion\":1}", token);
        generation = oldGeneration;
        ResponseEntity<String> late = put(token, deviceBody(0, List.of("2020-07-01")));
        assertEquals(HttpStatus.FORBIDDEN, late.getStatusCode(), late.getBody());
        assertEquals(0, count("select count(*) from pro_discovery_device_day where user_id = ?::uuid", userId(token)));
    }

    @Test
    @DisplayName("Given the user enables synchronization again, when a new synchronization generation starts, then old requests cannot overwrite the new state")
    void aNewGenerationRejectsThePreviousGeneration() {
        String token = login("generation-restart-" + UUID.randomUUID());
        union(token, List.of("2020-08-01"));
        long firstGeneration = generation;
        post("/api/v1/promotions/pro-discovery/activity/permission", "{\"enabled\":false,\"scopeVersion\":1}", token);
        permit(token);
        assertNotEquals(firstGeneration, generation);
        Map<String, Object> fresh = sync(token, 0, List.of("2021-01-01"));
        generation = firstGeneration;
        ResponseEntity<String> old = put(token, deviceBody(1, List.of("2020-08-01")));
        assertEquals(HttpStatus.CONFLICT, old.getStatusCode(), old.getBody());
        assertEquals("ACTIVITY_GENERATION_STALE", parse(old).get("errorCode"));
        assertEquals(1, count(
                "select count(*) from pro_discovery_device_day where user_id = ?::uuid and workout_date = '2021-01-01'",
                userId(token)
        ));
        assertEquals(0, count(
                "select count(*) from pro_discovery_device_day where user_id = ?::uuid and workout_date = '2020-08-01'",
                userId(token)
        ));
        assertTrue(revision(fresh) >= 1);
    }

    @Test
    @DisplayName("Given two devices have different workout dates, when both synchronize, then eligibility uses the union of both sets")
    void twoDevicesContributeAUnion() {
        String token = login("two-devices-" + UUID.randomUUID());
        permit(token);
        syncInstallation(token, installation, 0, List.of("2019-01-01"));
        syncInstallation(token, otherInstallation, 0, List.of("2019-02-01"));
        assertEquals(2, number(status(token), "qualifyingDays"));
    }

    @Test
    @DisplayName("Given both devices report the same workout date, when eligibility is evaluated, then that date counts once")
    void theSameDateFromTwoDevicesCountsOnce() {
        String token = login("shared-date-" + UUID.randomUUID());
        permit(token);
        syncInstallation(token, installation, 0, List.of("2019-03-01"));
        syncInstallation(token, otherInstallation, 0, List.of("2019-03-01"));
        assertEquals(1, number(status(token), "qualifyingDays"));
        assertEquals(2, count("select count(*) from pro_discovery_device_day where user_id = ?::uuid", userId(token)));
    }

    @Test
    @DisplayName("Given device A deletes its only workout on a date, when device A synchronizes, then device B's matching date remains counted")
    void deletingADateOnOneDeviceKeepsTheOtherDevice() {
        String token = login("device-delete-" + UUID.randomUUID());
        permit(token);
        long revision = revision(syncInstallation(token, installation, 0, List.of("2019-04-01")));
        syncInstallation(token, otherInstallation, 0, List.of("2019-04-01"));
        syncInstallation(token, installation, revision, List.of());
        assertEquals(1, number(status(token), "qualifyingDays"));
        assertEquals(0, count(
                "select count(*) from pro_discovery_device_day where user_id = ?::uuid and installation_id = ?::uuid",
                userId(token),
                installation
        ));
        assertEquals(1, count(
                "select count(*) from pro_discovery_device_day where user_id = ?::uuid and installation_id = ?::uuid",
                userId(token),
                otherInstallation
        ));
    }

    @Test
    @DisplayName("Given an old Android client calls the legacy account-wide REPLACE endpoint, when the new device-scoped model is active, then it cannot overwrite other devices' data")
    void aLegacyReplaceCannotOverwriteAnotherDevice() {
        String token = login("legacy-replace-" + UUID.randomUUID());
        permit(token);
        syncInstallation(token, installation, 0, List.of("2019-05-01", "2019-05-02"));
        ResponseEntity<String> legacy = put(
                token,
                "{\"baseRevision\":0,\"mode\":\"REPLACE\",\"dates\":[\"2019-05-01\"]}"
        );
        assertEquals(HttpStatus.CONFLICT, legacy.getStatusCode(), legacy.getBody());
        assertEquals("ACTIVITY_DEVICE_REQUIRED", parse(legacy).get("errorCode"));
        assertEquals(2, count("select count(*) from pro_discovery_device_day where user_id = ?::uuid", userId(token)));
        assertEquals(2, number(status(token), "qualifyingDays"));
    }

    @Test
    @DisplayName("Given a device is stale beyond the retention threshold, when automatic cleanup runs, then only that extra stale device is removed")
    void staleExtraDeviceSnapshotsAreRemoved() {
        String token = login("stale-device-" + UUID.randomUUID());
        permit(token);
        syncInstallation(token, installation, 0, List.of("2018-01-01"));
        syncInstallation(token, otherInstallation, 0, List.of("2018-02-01"));
        jdbc.update(
                "update pro_discovery_device set updated_at = now() - interval '19 months' where user_id = ?::uuid and installation_id = ?::uuid",
                userId(token),
                installation
        );
        assertTrue(cleanup.cleanup() >= 1);
        assertEquals(0, count(
                "select count(*) from pro_discovery_device_day where user_id = ?::uuid and installation_id = ?::uuid",
                userId(token),
                installation
        ));
        assertEquals(1, count(
                "select count(*) from pro_discovery_device_day where user_id = ?::uuid and installation_id = ?::uuid",
                userId(token),
                otherInstallation
        ));
        assertEquals(1, number(status(token), "qualifyingDays"));
    }

    @Test
    @DisplayName("Given only one device snapshot exists and it is older than the retention threshold, when cleanup runs, then that snapshot is kept")
    void theOnlyDeviceSnapshotIsKept() {
        String token = login("sole-device-" + UUID.randomUUID());
        permit(token);
        syncInstallation(token, installation, 0, List.of("2018-03-01"));
        jdbc.update(
                "update pro_discovery_device set updated_at = now() - interval '19 months' where user_id = ?::uuid",
                userId(token)
        );
        cleanup.cleanup();
        assertEquals(1, count("select count(*) from pro_discovery_device_day where user_id = ?::uuid", userId(token)));
    }

    @Test
    @DisplayName("Given an existing trial is active, when permission is withdrawn, then the trial continues until its original expiry")
    void withdrawalLeavesAnActiveTrial() {
        String token = login("withdraw-trial-" + UUID.randomUUID());
        union(token, qualifyingDays());
        String expires = String.valueOf(parse(post("/api/v1/promotions/pro-discovery/activate", "{}", token)).get("expiresAt"));
        ResponseEntity<String> withdrawn = post(
                "/api/v1/promotions/pro-discovery/activity/permission",
                "{\"enabled\":false,\"scopeVersion\":1}",
                token
        );
        assertEquals(HttpStatus.OK, withdrawn.getStatusCode(), withdrawn.getBody());
        Map<String, Object> body = status(token);
        assertEquals("ACTIVE_TRIAL", body.get("status"));
        assertEquals(expires, body.get("expiresAt"));
        assertEquals(1, trials(token));
    }

    private List<String> qualifyingDays() {
        List<String> dates = oldDays(22);
        dates.add(today().minusDays(1).toString());
        dates.add(today().toString());
        return dates;
    }

    private List<String> oldDays(int count) {
        LocalDate first = today().minusYears(2);
        List<String> dates = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            dates.add(first.plusDays(index).toString());
        }
        return dates;
    }

    private LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private Map<String, Object> union(String token, List<String> dates) {
        permit(token);
        return sync(token, 0, dates);
    }

    private Map<String, Object> replace(String token, long revision, List<String> dates) {
        return sync(token, revision, dates);
    }

    private void permit(String token) {
        ResponseEntity<String> response = post(
                "/api/v1/promotions/pro-discovery/activity/permission",
                "{\"enabled\":true,\"scopeVersion\":1}",
                token
        );
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        generation = number(parse(response), "generation");
    }

    private Map<String, Object> sync(String token, long revision, List<String> dates) {
        return syncInstallation(token, installation, revision, dates);
    }

    private Map<String, Object> syncInstallation(String token, UUID device, long revision, List<String> dates) {
        ResponseEntity<String> response = put(token, deviceBody(device, revision, dates));
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        return parse(response);
    }

    private String deviceBody(long revision, List<String> dates) {
        return deviceBody(installation, revision, dates);
    }

    private String deviceBody(UUID device, long revision, List<String> dates) {
        return "{\"installationId\":\"" + device + "\",\"baseRevision\":" + revision
                + ",\"generation\":" + generation + ",\"dates\":" + json(dates) + "}";
    }

    private static String json(List<String> dates) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < dates.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append('"').append(dates.get(index)).append('"');
        }
        return builder.append(']').toString();
    }

    private Map<String, Object> status(String token) {
        ResponseEntity<String> response = send(HttpMethod.GET, "/api/v1/promotions/pro-discovery", null, token);
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        return parse(response);
    }

    private int trials(String token) {
        return count("select count(*) from promotional_trial where user_id = ?::uuid", userId(token));
    }

    private String login(String subject) {
        return login(subject, subject);
    }

    private String login(String idToken, String subject) {
        identities.accept(idToken, subject, subject + "@example.com", true);
        ResponseEntity<String> response = post("/api/v1/auth/google", "{\"idToken\":\"" + idToken + "\"}", null);
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        return String.valueOf(parse(response).get("accessToken"));
    }

    private String userId(String token) {
        ResponseEntity<String> response = send(HttpMethod.GET, "/api/v1/me", null, token);
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        return String.valueOf(parse(response).get("id"));
    }

    private long revision(Map<String, Object> body) {
        return number(body, "revision");
    }

    private static long number(Map<String, Object> body, String key) {
        return ((Number) body.get(key)).longValue();
    }

    private ResponseEntity<String> post(String path, String json, String token) {
        return send(HttpMethod.POST, path, json, token);
    }

    private ResponseEntity<String> put(String token, String json) {
        return send(HttpMethod.PUT, "/api/v1/promotions/pro-discovery/activity", json, token);
    }

    private ResponseEntity<String> send(HttpMethod method, String path, String json, String token) {
        RestClient.RequestBodySpec request = client().method(method).uri(path).contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        RestClient.RequestHeadersSpec<?> spec = json == null ? request : request.body(json);
        return spec.exchange((httpRequest, response) -> {
            byte[] body = response.getBody().readAllBytes();
            return ResponseEntity.status(response.getStatusCode()).body(new String(body, StandardCharsets.UTF_8));
        });
    }

    private RestClient client() {
        return RestClient.builder().baseUrl("http://127.0.0.1:" + port).build();
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private static Map<String, Object> parse(ResponseEntity<String> response) {
        return new BasicJsonParser().parseMap(response.getBody());
    }
}
