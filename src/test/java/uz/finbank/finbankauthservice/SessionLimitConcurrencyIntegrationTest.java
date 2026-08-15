package uz.finbank.finbankauthservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uz.finbank.finbankauthservice.dto.request.LoginRequest;
import uz.finbank.finbankauthservice.dto.request.RegisterRequest;
import uz.finbank.finbankauthservice.dto.response.LoginResponse;
import uz.finbank.finbankauthservice.dto.response.SessionResponse;
import uz.finbank.finbankauthservice.dto.response.UserResponse;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the 5-device session limit (AuthServiceImpl.evictOldestSessionIfLimitReached, guarded by
 * UserRepository.lockById's pessimistic row lock) actually holds under real concurrency -- not
 * just when logins happen one at a time. Without the lock, N simultaneous logins for the same
 * user can all read the same pre-eviction count and all proceed to insert, leaving more than
 * maxDevices (5) sessions ACTIVE at once.
 */
class SessionLimitConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final int MAX_DEVICES = 5;
    private static final int CONCURRENT_LOGINS = 8;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void should_capActiveSessionsAtMaxDevices_when_manyLoginsHappenSimultaneously() throws InterruptedException {
        String suffix = UUID.randomUUID().toString();
        String email = "race" + suffix + "@test.local";
        String password = "longenoughpassword";

        RegisterRequest registerRequest = RegisterRequest.builder()
                .username("race" + suffix)
                .email(email)
                .password(password)
                .build();
        assertThat(restTemplate.postForEntity("/register", registerRequest, UserResponse.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        LoginRequest loginRequest = LoginRequest.builder().email(email).password(password).build();

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_LOGINS);
        CountDownLatch readyLatch = new CountDownLatch(CONCURRENT_LOGINS);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();

        List<Runnable> tasks = IntStream.range(0, CONCURRENT_LOGINS)
                .<Runnable>mapToObj(i -> () -> {
                    readyLatch.countDown();
                    awaitUninterruptibly(startLatch);
                    ResponseEntity<LoginResponse> response =
                            restTemplate.postForEntity("/login", loginRequest, LoginResponse.class);
                    if (response.getStatusCode() == HttpStatus.OK) {
                        successCount.incrementAndGet();
                    }
                })
                .toList();

        tasks.forEach(executor::execute);
        readyLatch.await(10, TimeUnit.SECONDS);
        startLatch.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(successCount.get()).isEqualTo(CONCURRENT_LOGINS);

        // The point of the test: no matter how many logins race in at once, the eviction must
        // keep the ACTIVE count pinned at exactly MAX_DEVICES -- never more.
        LoginResponse anyLoginResponse =
                restTemplate.postForEntity("/login", loginRequest, LoginResponse.class).getBody();
        assertThat(anyLoginResponse).isNotNull();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(anyLoginResponse.accessToken());

        ResponseEntity<SessionResponse[]> sessionsResponse = restTemplate.exchange(
                "/sessions", HttpMethod.GET, new HttpEntity<>(headers), SessionResponse[].class);
        assertThat(sessionsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sessionsResponse.getBody()).hasSize(MAX_DEVICES);
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
