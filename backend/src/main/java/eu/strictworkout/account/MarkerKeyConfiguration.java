package eu.strictworkout.account;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MarkerKeyConfiguration.IdentityProperties.class)
class MarkerKeyConfiguration {

    @Bean
    MarkerKeyRing markerKeyRing(IdentityProperties properties) {
        return MarkerKeyRing.parse(properties.markerHmacKeys(), properties.markerHmacActiveKey());
    }

    @ConfigurationProperties(prefix = "strict.identity")
    public record IdentityProperties(String markerHmacKeys, String markerHmacActiveKey) {
        public IdentityProperties {
            markerHmacKeys = markerHmacKeys == null ? "" : markerHmacKeys.trim();
            markerHmacActiveKey = markerHmacActiveKey == null ? "" : markerHmacActiveKey.trim();
        }
    }
}
