package eu.strictworkout.auth;

import java.util.UUID;

public record StrictPrincipal(UUID userId, UUID sessionId) {
}
