package uz.finbank.finbankauthservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Kept off FinbankAuthServiceApplication itself -- same reasoning as @EnableJpaAuditing living in
// AuditingConfig: @Enable* annotations on the primary @SpringBootApplication class aren't
// filtered out by @WebMvcTest slices the way ordinary @Configuration classes are.
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
