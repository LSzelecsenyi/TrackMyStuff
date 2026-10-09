package eu.strictworkout.billing;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(BillingProperties.class)
class BillingConfiguration {

    @Bean
    PlayDeveloperApi playDeveloperApi(BillingProperties properties) {
        return new HttpPlayDeveloperApi(properties.serviceAccountFile());
    }

    @Bean
    PlaySubscriptionService playSubscriptionService(
            PlayDeveloperApi play,
            PlaySubscriptionRepository subscriptions,
            BillingProperties properties,
            Clock clock
    ) {
        Set<String> products = Arrays.stream(properties.productIds().split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        String packageName = properties.packageName().isEmpty()
                ? "com.strictworkout.app"
                : properties.packageName();
        return new PlaySubscriptionService(play, subscriptions, packageName, products, clock);
    }

    @Bean
    GooglePushAuthenticator googlePushAuthenticator(BillingProperties properties) {
        return new GooglePushAuthenticator(properties.rtdnAudience(), properties.rtdnServiceAccount());
    }
}
