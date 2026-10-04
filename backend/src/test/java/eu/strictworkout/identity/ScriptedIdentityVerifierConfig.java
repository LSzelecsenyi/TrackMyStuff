package eu.strictworkout.identity;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class ScriptedIdentityVerifierConfig {

    @Bean
    @Primary
    ScriptedIdentityVerifier scriptedIdentityVerifier() {
        return new ScriptedIdentityVerifier();
    }
}
