package eu.strictworkout.admin;

import eu.strictworkout.auth.GoogleSignInRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/session")
public class AdminSessionController {

    private final AdminSessionService sessions;
    private final AdminSessionCookies cookies;

    public AdminSessionController(AdminSessionService sessions, AdminSessionCookies cookies) {
        this.sessions = sessions;
        this.cookies = cookies;
    }

    @GetMapping
    public AdminSessionView current() {
        return sessions.current(AdminRequests.current());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void login(
            @Valid @RequestBody GoogleSignInRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        AdminSessionService.IssuedAdminSession issued = sessions.login(request.idToken());
        sessions.revokePresented(cookies.read(httpRequest));
        cookies.write(httpResponse, issued.accessToken(), issued.expiresAt());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletResponse httpResponse) {
        sessions.revoke(AdminRequests.current());
        cookies.clear(httpResponse);
    }
}
