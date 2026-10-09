package eu.strictworkout.promotion;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class PromotionConfiguration {
    @Bean
    PromotionalTrialService promotionalTrialService(JpaPromotionalTrialStore store) {
        return new PromotionalTrialService(store);
    }
}
