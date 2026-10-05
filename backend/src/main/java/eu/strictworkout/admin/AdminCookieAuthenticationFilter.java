package eu.strictworkout.admin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public final class AdminCookieAuthenticationFilter extends OncePerRequestFilter {

    private final AdminSessionService sessions;
    private final AdminSessionCookies cookies;

    public AdminCookieAuthenticationFilter(AdminSessionService sessions, AdminSessionCookies cookies) {
        this.sessions = sessions;
        this.cookies = cookies;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod()) && "/api/v1/admin/session".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = cookies.read(request);
        if (token != null) {
            sessions.authenticate(token).ifPresent(principal ->
                    SecurityContextHolder.getContext().setAuthentication(new AdminAuthentication(principal))
            );
        }
        filterChain.doFilter(request, response);
    }
}
