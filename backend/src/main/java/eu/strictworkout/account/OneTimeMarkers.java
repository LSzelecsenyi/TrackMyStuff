package eu.strictworkout.account;

import java.time.Instant;
import java.util.Optional;

/**
 * One-time Founder and promotion protection. The Google subject is an input to the
 * hash and is not retained.
 */
public interface OneTimeMarkers {

    void requireWritable();

    boolean untrustworthy();

    boolean present(String googleSubject);

    void write(String googleSubject, Instant deletedAt, AccountDeletionStore.Usage usage);

    Optional<AccountDeletionStore.MarkerRow> find(String googleSubject);
}
