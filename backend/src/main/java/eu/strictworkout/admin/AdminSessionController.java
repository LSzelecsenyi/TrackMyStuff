package eu.strictworkout.admin;

import eu.strictworkout.auth.GoogleSignInRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/session")
public class AdminSessionController {

    private final AdminSessionService sessions;

    public AdminSessionController(AdminSessionService sessions) {
        this.sessions = sessions;
    }

    @PostMapping
    public AdminSessionResponse login(@Valid @RequestBody GoogleSignInRequest request) {
        AdminSessionService.IssuedAdminSession issued = sessions.login(request.idToken());
        return new AdminSessionResponse(
                issued.accessToken(),
                "Bearer",
                issued.expiresAt(),
                new AdminRef(issued.adminId())
        );
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        sessions.revoke(AdminRequests.current());
    }

    public record AdminSessionResponse(String accessToken, String tokenType, Instant expiresAt, AdminRef admin) {
    }

    public record AdminRef(UUID id) {
    }
}
