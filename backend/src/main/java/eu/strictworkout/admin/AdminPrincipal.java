package eu.strictworkout.admin;

import java.util.UUID;

public record AdminPrincipal(UUID adminId, UUID sessionId) {
}
