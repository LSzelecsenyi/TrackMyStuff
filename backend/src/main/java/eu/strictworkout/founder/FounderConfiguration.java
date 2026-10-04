package eu.strictworkout.founder;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class FounderConfiguration {

    @Bean
    FounderRules founderRules() {
        return FounderRules.PRODUCTION;
    }

    @Bean
    FounderStateMachine founderStateMachine() {
        return new FounderStateMachine();
    }
}
