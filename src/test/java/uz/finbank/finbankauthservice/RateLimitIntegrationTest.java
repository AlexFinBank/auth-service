package uz.finbank.finbankauthservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import uz.finbank.finbankauthservice.dto.request.LoginRequest;
import uz.finbank.finbankauthservice.dto.response.ErrorResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the rate limiter actually enforces its limit against a real Redis (Testcontainers),
 * not just against mocks -- a low login-max-per-email is configured just for this test class
 * so it doesn't need to burn through the production default of 10 attempts.
 */
@TestPropertySource(properties = {
        "app.security.rate-limit.login-max-per-email=3",
        "app.security.rate-limit.login-window-minutes=5"
})
class RateLimitIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void should_returnTooManyRequests_afterExceedingLoginAttemptsForTheSameEmail() {
        String email = "ratelimit" + UUID.randomUUID() + "@test.local";
        LoginRequest loginRequest = LoginRequest.builder().email(email).password("wrong-password").build();

        for (int attempt = 1; attempt <= 3; attempt++) {
            ResponseEntity<ErrorResponse> response =
                    restTemplate.postForEntity("/login", loginRequest, ErrorResponse.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        ResponseEntity<ErrorResponse> fourthAttempt =
                restTemplate.postForEntity("/login", loginRequest, ErrorResponse.class);
        assertThat(fourthAttempt.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
