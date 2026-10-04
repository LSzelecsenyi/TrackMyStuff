package eu.strictworkout.identity;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import eu.strictworkout.admin.AdminProperties;
import eu.strictworkout.auth.AuthProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.util.List;

@Configuration
@EnableConfigurationProperties({AuthProperties.class, GoogleProperties.class, AdminProperties.class})
class IdentityConfiguration {

    @Bean
    ExternalIdentityVerifier externalIdentityVerifier(GoogleProperties properties) throws GeneralSecurityException, IOException {
        if (properties.clientId() == null || properties.clientId().isBlank()) {
            return new RejectingIdentityVerifier();
        }
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance()
        )
                .setAudience(List.of(properties.clientId()))
                .build();
        return new GoogleIdentityVerifier(new GoogleCertIdTokenChecker(verifier));
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
