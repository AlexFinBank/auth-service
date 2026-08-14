package uz.finbank.finbankauthservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class FinbankAuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinbankAuthServiceApplication.class, args);
    }

}
