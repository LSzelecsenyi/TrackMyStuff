package eu.strictworkout.account;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DeletionMarkerService implements OneTimeMarkers {

    private final MarkerKeyRing keys;
    private final AccountDeletionStore store;
    private final JdbcTemplate jdbc;

    public DeletionMarkerService(MarkerKeyRing keys, AccountDeletionStore store, JdbcTemplate jdbc) {
        this.keys = keys;
        this.store = store;
        this.jdbc = jdbc;
    }

    @Override
    public void requireWritable() {
        if (!keys.configured() || untrustworthy()) {
            throw new AccountDeletionException(
                    AccountDeletionException.Code.MARKER_UNAVAILABLE,
                    "Account deletion is unavailable until the marker key is configured and matches its stored checksum."
            );
        }
    }

    @Override
    public boolean untrustworthy() {
        return MarkerKeyRing.untrustworthy(keys, storedVerifiers());
    }

    @Override
    @Transactional
    public boolean present(String googleSubject) {
        return find(googleSubject).isPresent();
    }

    @Override
    @Transactional
    public void write(String googleSubject, Instant deletedAt, AccountDeletionStore.Usage usage) {
        requireWritable();
        List<AccountDeletionStore.MarkerRow> existing = load(googleSubject);
        AccountDeletionStore.MarkerRow merged = merge(existing);
        AccountDeletionStore.Usage combined = new AccountDeletionStore.Usage(
                usage.founder() || (merged != null && merged.founder()),
                usage.proDiscovery() || (merged != null && merged.proDiscovery()),
                usage.welcomeBack() || (merged != null && merged.welcomeBack()),
                usage.earlyAdopter() || (merged != null && merged.earlyAdopter())
        );
        String active = keys.markerHash(googleSubject);
        store.insertMarker(active, deletedAt, combined);
        for (AccountDeletionStore.MarkerRow row : existing) {
            if (!active.equals(row.subjectHash())) {
                store.deleteMarker(row.subjectHash());
            }
        }
        rememberVerifier();
    }

    @Override
    @Transactional
    public Optional<AccountDeletionStore.MarkerRow> find(String googleSubject) {
        if (googleSubject == null || googleSubject.isBlank() || untrustworthy()) {
            return Optional.empty();
        }
        List<AccountDeletionStore.MarkerRow> rows = load(googleSubject);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        if (!keys.configured()) {
            return Optional.of(merge(rows));
        }
        return Optional.of(migrate(googleSubject, rows));
    }

    private AccountDeletionStore.MarkerRow migrate(String googleSubject, List<AccountDeletionStore.MarkerRow> rows) {
        String active = keys.markerHash(googleSubject);
        if (rows.size() == 1 && active.equals(rows.get(0).subjectHash())) {
            return rows.get(0);
        }
        AccountDeletionStore.MarkerRow merged = merge(rows);
        store.insertMarker(active, merged.deletedAt(), new AccountDeletionStore.Usage(
                merged.founder(),
                merged.proDiscovery(),
                merged.welcomeBack(),
                merged.earlyAdopter()
        ));
        for (AccountDeletionStore.MarkerRow row : rows) {
            if (!active.equals(row.subjectHash())) {
                store.deleteMarker(row.subjectHash());
            }
        }
        rememberVerifier();
        return store.findMarker(active).orElseThrow();
    }

    private List<AccountDeletionStore.MarkerRow> load(String googleSubject) {
        List<String> hashes = new ArrayList<>();
        if (keys.configured()) {
            hashes.addAll(keys.markerHashes(googleSubject));
        }
        String legacy = GoogleSubjectHash.of(googleSubject);
        if (!hashes.contains(legacy)) {
            hashes.add(legacy);
        }
        List<AccountDeletionStore.MarkerRow> rows = new ArrayList<>();
        for (String hash : hashes) {
            store.findMarker(hash).ifPresent(rows::add);
        }
        return rows;
    }

    private static AccountDeletionStore.MarkerRow merge(List<AccountDeletionStore.MarkerRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        AccountDeletionStore.MarkerRow merged = rows.get(0);
        for (int i = 1; i < rows.size(); i++) {
            AccountDeletionStore.MarkerRow row = rows.get(i);
            Instant deletedAt = row.deletedAt().isAfter(merged.deletedAt()) ? row.deletedAt() : merged.deletedAt();
            merged = new AccountDeletionStore.MarkerRow(
                    merged.subjectHash(),
                    deletedAt,
                    merged.founder() || row.founder(),
                    merged.proDiscovery() || row.proDiscovery(),
                    merged.welcomeBack() || row.welcomeBack(),
                    merged.earlyAdopter() || row.earlyAdopter()
            );
        }
        return merged;
    }

    private void rememberVerifier() {
        jdbc.update(
                """
                insert into marker_key_verifier (key_id, verifier)
                values (?, ?)
                on conflict (key_id) do nothing
                """,
                keys.activeKeyId(),
                keys.verifier()
        );
    }

    private Map<Integer, String> storedVerifiers() {
        Map<Integer, String> stored = new HashMap<>();
        jdbc.query(
                "select key_id, verifier from marker_key_verifier",
                rs -> {
                    stored.put(rs.getInt("key_id"), rs.getString("verifier"));
                }
        );
        return stored;
    }
}
