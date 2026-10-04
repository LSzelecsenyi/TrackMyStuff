package eu.strictworkout.founder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FounderWorkoutFact(UUID clientWorkoutId, Instant completedAt, LocalDate localDate) {
}
