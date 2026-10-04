package eu.strictworkout.auth;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/api/v1/auth/google")
    public AuthService.SessionIssued google(@Valid @RequestBody GoogleSignInRequest request) {
        return auth.login(request.idToken());
    }

    @GetMapping("/api/v1/me")
    public CurrentUserResponse me() {
        return new CurrentUserResponse(StrictRequests.current().userId());
    }

    @DeleteMapping("/api/v1/auth/session")
    public ResponseEntity<Void> logout() {
        auth.logout(StrictRequests.current());
        return ResponseEntity.noContent().build();
    }
}
