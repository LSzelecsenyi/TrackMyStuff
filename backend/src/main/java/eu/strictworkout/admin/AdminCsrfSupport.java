package eu.strictworkout.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

public final class AdminCsrfSupport {

    private AdminCsrfSupport() {
    }

    public static CookieCsrfTokenRepository repository(boolean secure) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> cookie
                .path("/")
                .httpOnly(false)
                .secure(secure)
                .sameSite("Strict"));
        return repository;
    }

    /**
     * Angular reads the XSRF-TOKEN cookie and sends it back as X-XSRF-TOKEN.
     * That header is the raw token, so it must not be decoded as a BREACH XOR value.
     * Resolving the deferred token writes the cookie on the response.
     */
    public static CsrfTokenRequestHandler requestHandler() {
        return new CsrfTokenRequestHandler() {
            private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
            private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

            @Override
            public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
                xor.handle(request, response, csrfToken);
                csrfToken.get();
            }

            @Override
            public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
                if (StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()))) {
                    return plain.resolveCsrfTokenValue(request, csrfToken);
                }
                return xor.resolveCsrfTokenValue(request, csrfToken);
            }
        };
    }
}
