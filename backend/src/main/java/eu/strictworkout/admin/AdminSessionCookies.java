package eu.strictworkout.admin;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class AdminSessionCookies {

    public static final String NAME = "STRICT_ADMIN_SESSION";
    public static final String PATH = "/api/v1/admin";

    private final boolean secure;
    private final Clock clock;

    public AdminSessionCookies(AdminProperties properties, Environment environment, Clock clock) {
        this.secure = properties.secureCookie(environment);
        this.clock = clock;
    }

    public boolean secure() {
        return secure;
    }

    public void write(HttpServletResponse response, String token, Instant expiresAt) {
        long seconds = Math.max(0, Duration.between(clock.instant(), expiresAt).toSeconds());
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(token, seconds).toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie("", 0).toString());
    }

    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String value = null;
        for (Cookie cookie : cookies) {
            if (!NAME.equals(cookie.getName())) {
                continue;
            }
            if (value != null) {
                return null;
            }
            value = cookie.getValue();
        }
        return value == null || value.isBlank() ? null : value;
    }

    private ResponseCookie sessionCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
    }
}
