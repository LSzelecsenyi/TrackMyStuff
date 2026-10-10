package eu.strictworkout.auth;

import eu.strictworkout.admin.AdminCookieAuthenticationFilter;
import eu.strictworkout.admin.AdminCsrfSupport;
import eu.strictworkout.admin.AdminSessionCookies;
import eu.strictworkout.admin.AdminSessionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
class SecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain adminSecurityFilterChain(
            HttpSecurity http,
            AdminSessionService adminSessions,
            AdminSessionCookies cookies
    ) throws Exception {
        http
                .securityMatcher("/api/v1/admin/**")
                .csrf(csrf -> csrf
                        .csrfTokenRepository(AdminCsrfSupport.repository(cookies.secure()))
                        .csrfTokenRequestHandler(AdminCsrfSupport.requestHandler())
                )
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(new ApiAuthenticationEntryPoint(false))
                        .accessDeniedHandler(new ApiAccessDeniedHandler())
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/session").permitAll()
                        .anyRequest().hasAuthority("ADMIN")
                )
                .addFilterBefore(
                        new AdminCookieAuthenticationFilter(adminSessions, cookies),
                        UsernamePasswordAuthenticationFilter.class
                );
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain memberSecurityFilterChain(HttpSecurity http, AuthSessionService sessions) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(new ApiAuthenticationEntryPoint(true))
                        .accessDeniedHandler(new ApiAccessDeniedHandler())
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/promotions/availability").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/billing/rtdn").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/google").permitAll()
                        .requestMatchers(HttpMethod.GET, "/account/delete").permitAll()
                        .requestMatchers(HttpMethod.GET, "/privacy", "/privacy/en", "/privacy/hu").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/account/deletion").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/account/deletion-preview").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll()
                )
                .addFilterBefore(new OpaqueBearerAuthenticationFilter(sessions), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
