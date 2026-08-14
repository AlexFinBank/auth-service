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
import uz.finbank.finbankauthservice.dto.request.RefreshRequest;
import uz.finbank.finbankauthservice.dto.request.RegisterRequest;
import uz.finbank.finbankauthservice.dto.response.ErrorResponse;
import uz.finbank.finbankauthservice.dto.response.LoginResponse;
import uz.finbank.finbankauthservice.dto.response.SessionResponse;
import uz.finbank.finbankauthservice.dto.response.UserResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end round trip through the real HTTP stack, real Postgres/Kafka/Redis:
 * register -> login -> refresh (rotation) -> replay the rotated-away refresh token
 * (reuse detection) -> confirm all sessions were revoked as a result.
 */
class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void should_detectRefreshTokenReuseAndRevokeAllSessions_afterRotation() {
        String suffix = UUID.randomUUID().toString();
        String email = "flow" + suffix + "@test.local";
        String password = "longenoughpassword";

        RegisterRequest registerRequest = RegisterRequest.builder()
                .username("flow" + suffix)
                .email(email)
                .password(password)
                .build();
        ResponseEntity<UserResponse> registerResponse =
                restTemplate.postForEntity("/register", registerRequest, UserResponse.class);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        LoginRequest loginRequest = LoginRequest.builder().email(email).password(password).build();
        ResponseEntity<LoginResponse> loginResponse =
                restTemplate.postForEntity("/login", loginRequest, LoginResponse.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        LoginResponse loginBody = loginResponse.getBody();
        assertThat(loginBody).isNotNull();
        String firstAccessToken = loginBody.accessToken();
        String firstRefreshToken = loginBody.refreshToken();
        assertThat(firstAccessToken).isNotBlank();
        assertThat(firstRefreshToken).isNotBlank();

        HttpHeaders firstAuthHeaders = new HttpHeaders();
        firstAuthHeaders.setBearerAuth(firstAccessToken);

        ResponseEntity<SessionResponse[]> sessionsAfterLogin = restTemplate.exchange(
                "/sessions", HttpMethod.GET, new HttpEntity<>(firstAuthHeaders), SessionResponse[].class);
        assertThat(sessionsAfterLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sessionsAfterLogin.getBody()).hasSize(1);

        RefreshRequest refreshRequest = RefreshRequest.builder().refreshToken(firstRefreshToken).build();
        ResponseEntity<LoginResponse> refreshResponse =
                restTemplate.postForEntity("/refresh", refreshRequest, LoginResponse.class);
        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        LoginResponse refreshBody = refreshResponse.getBody();
        assertThat(refreshBody).isNotNull();
        assertThat(refreshBody.refreshToken()).isNotBlank().isNotEqualTo(firstRefreshToken);

        // Replaying the now-rotated-away original refresh token must be treated as reuse.
        ResponseEntity<ErrorResponse> replayResponse =
                restTemplate.postForEntity("/refresh", refreshRequest, ErrorResponse.class);
        assertThat(replayResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // The access token itself is still cryptographically valid (JWTs aren't server-side
        // revoked), but the underlying session must be gone because reuse revokes everything.
        ResponseEntity<SessionResponse[]> sessionsAfterReuse = restTemplate.exchange(
                "/sessions", HttpMethod.GET, new HttpEntity<>(firstAuthHeaders), SessionResponse[].class);
        assertThat(sessionsAfterReuse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sessionsAfterReuse.getBody()).isEmpty();
    }
}
