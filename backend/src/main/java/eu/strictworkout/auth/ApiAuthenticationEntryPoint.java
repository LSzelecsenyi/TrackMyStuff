package eu.strictworkout.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    static final String BODY = "{\"errorCode\":\"UNAUTHENTICATED\",\"message\":\"Authentication is required.\"}";

    private final boolean advertiseBearer;

    ApiAuthenticationEntryPoint(boolean advertiseBearer) {
        this.advertiseBearer = advertiseBearer;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        if (advertiseBearer) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        response.getWriter().write(BODY);
    }
}
