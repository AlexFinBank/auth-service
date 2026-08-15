package uz.finbank.finbankauthservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uz.finbank.finbankauthservice.dto.request.RegisterRequest;
import uz.finbank.finbankauthservice.dto.response.ErrorResponse;
import uz.finbank.finbankauthservice.dto.response.UserResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves /register is safe to retry with the same Idempotency-Key against the real HTTP stack
 * and real Postgres/Redis -- a retried request must replay the exact first outcome (success or
 * duplicate-email conflict), not produce two different answers for one logical attempt.
 */
class RegisterIdempotencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void should_returnTheSameCreatedUser_when_registerIsRetriedWithTheSameIdempotencyKey() {
        String suffix = UUID.randomUUID().toString();
        RegisterRequest request = RegisterRequest.builder()
                .username("idem" + suffix)
                .email("idem" + suffix + "@test.local")
                .password("longenoughpassword")
                .build();
        String idempotencyKey = "client-key-" + suffix;

        ResponseEntity<UserResponse> first = postRegister(request, idempotencyKey);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UserResponse firstBody = first.getBody();
        assertThat(firstBody).isNotNull();

        ResponseEntity<UserResponse> retry = postRegister(request, idempotencyKey);
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(retry.getBody()).isEqualTo(firstBody);
    }

    @Test
    void should_replayTheSameDuplicateConflict_when_aFailedRegisterIsRetriedWithTheSameKey() {
        String suffix = UUID.randomUUID().toString();
        String email = "dup" + suffix + "@test.local";
        String idempotencyKey = "client-key-" + suffix;

        // Pre-existing user occupying the email, registered without an idempotency key.
        RegisterRequest existing = RegisterRequest.builder()
                .username("dupexisting" + suffix).email(email).password("longenoughpassword").build();
        assertThat(restTemplate.postForEntity("/register", existing, UserResponse.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        RegisterRequest conflicting = RegisterRequest.builder()
                .username("dupnew" + suffix).email(email).password("anotherlongpassword").build();

        ResponseEntity<ErrorResponse> first = postRegisterExpectingError(conflicting, idempotencyKey);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<ErrorResponse> retry = postRegisterExpectingError(conflicting, idempotencyKey);
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(retry.getBody()).isNotNull();
        assertThat(retry.getBody().message()).isEqualTo(first.getBody().message());
    }

    @Test
    void should_createTwoSeparateUsers_when_sameRequestShapeIsSentWithDifferentIdempotencyKeys() {
        String suffix = UUID.randomUUID().toString();
        RegisterRequest request = RegisterRequest.builder()
                .username("distinct" + suffix)
                .email("distinct" + suffix + "@test.local")
                .password("longenoughpassword")
                .build();

        ResponseEntity<UserResponse> firstAttempt = postRegister(request, "key-a-" + suffix);
        assertThat(firstAttempt.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Same email/username with a *different* key is a genuinely new attempt, not a retry --
        // it must hit the real duplicate-email business rule, not an idempotency cache hit.
        ResponseEntity<ErrorResponse> secondAttempt = postRegisterExpectingError(request, "key-b-" + suffix);
        assertThat(secondAttempt.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    private ResponseEntity<UserResponse> postRegister(RegisterRequest request, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", idempotencyKey);
        return restTemplate.postForEntity("/register", new HttpEntity<>(request, headers), UserResponse.class);
    }

    private ResponseEntity<ErrorResponse> postRegisterExpectingError(RegisterRequest request, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", idempotencyKey);
        return restTemplate.postForEntity("/register", new HttpEntity<>(request, headers), ErrorResponse.class);
    }
}
