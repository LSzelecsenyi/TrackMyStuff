package eu.strictworkout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class StrictApplication {

    private static final Logger log = LoggerFactory.getLogger(StrictApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(StrictApplication.class, args);
    }

    @Bean
    ApplicationRunner startup(Environment environment) {
        return args -> {
            String profiles = environment.getActiveProfiles().length == 0
                    ? "(default)"
                    : String.join(",", environment.getActiveProfiles());
            log.info("Strict backend is up. Health endpoint: /api/v1/health. Active profiles: {}", profiles);
        };
    }
}
