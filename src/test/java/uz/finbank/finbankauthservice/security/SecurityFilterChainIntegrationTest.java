package uz.finbank.finbankauthservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import uz.finbank.finbankauthservice.AbstractIntegrationTest;
import uz.finbank.finbankauthservice.dto.request.CreateStaffRequest;
import uz.finbank.finbankauthservice.dto.request.LoginRequest;
import uz.finbank.finbankauthservice.dto.request.RegisterRequest;
import uz.finbank.finbankauthservice.dto.response.ErrorResponse;
import uz.finbank.finbankauthservice.dto.response.LoginResponse;
import uz.finbank.finbankauthservice.dto.response.SessionResponse;
import uz.finbank.finbankauthservice.dto.response.UserResponse;
import uz.finbank.finbankauthservice.entity.UserEntity;
import uz.finbank.finbankauthservice.entity.enums.RoleEnum;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the SecurityConfig / JwtAuthenticationFilter / JwtAuthenticationEntryPoint /
 * JwtAccessDeniedHandler chain end-to-end against a real running app. Tokens for
 * roles are minted directly via the real JwtTokenProvider bean against a throwaway,
 * never-persisted UserEntity — the filter only trusts JWT claims, it never looks the
 * user up in the DB, so this isolates the authorization boundary from user provisioning.
 */
class SecurityFilterChainIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void should_return401_when_noAuthorizationHeaderOnProtectedEndpoint() {
        ResponseEntity<ErrorResponse> response = restTemplate.getForEntity("/sessions", ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(401);
        assertThat(response.getBody().message()).isNotBlank();
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    void should_return200_when_validCustomerTokenOnProtectedEndpoint() {
        HttpHeaders headers = bearerHeaders(jwtTokenProvider.generateAccessToken(fabricatedUser(RoleEnum.CUSTOMER)));

        ResponseEntity<SessionResponse[]> response = restTemplate.exchange(
                "/sessions", HttpMethod.GET, new HttpEntity<>(headers), SessionResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void should_return403_when_customerRoleCallsAdminOnlyEndpoint() {
        HttpHeaders headers = bearerHeaders(jwtTokenProvider.generateAccessToken(fabricatedUser(RoleEnum.CUSTOMER)));
        headers.setContentType(MediaType.APPLICATION_JSON);

        CreateStaffRequest body = newStaffRequest();

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/internal/staff", HttpMethod.POST, new HttpEntity<>(body, headers), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(403);
        assertThat(response.getBody().message()).isNotBlank();
    }

    @Test
    void should_return201_when_adminRoleCallsAdminOnlyEndpoint() {
        HttpHeaders headers = bearerHeaders(jwtTokenProvider.generateAccessToken(fabricatedUser(RoleEnum.ADMIN)));
        headers.setContentType(MediaType.APPLICATION_JSON);

        CreateStaffRequest body = newStaffRequest();

        ResponseEntity<UserResponse> response = restTemplate.exchange(
                "/internal/staff", HttpMethod.POST, new HttpEntity<>(body, headers), UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().role()).isEqualTo(RoleEnum.OPERATOR);
    }

    @Test
    void should_allowCreatedStaffAccountToLogIn_when_adminCreatesStaffViaInternalEndpoint() {
        HttpHeaders adminHeaders = bearerHeaders(jwtTokenProvider.generateAccessToken(fabricatedUser(RoleEnum.ADMIN)));
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);

        CreateStaffRequest staffRequest = newStaffRequest();

        ResponseEntity<UserResponse> createResponse = restTemplate.exchange(
                "/internal/staff", HttpMethod.POST, new HttpEntity<>(staffRequest, adminHeaders), UserResponse.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        LoginRequest loginRequest = LoginRequest.builder()
                .email(staffRequest.email())
                .password(staffRequest.password())
                .build();
        ResponseEntity<LoginResponse> loginResponse =
                restTemplate.postForEntity("/login", loginRequest, LoginResponse.class);

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        LoginResponse loginBody = loginResponse.getBody();
        assertThat(loginBody).isNotNull();
        assertThat(loginBody.accessToken()).isNotBlank();

        HttpHeaders staffAuthHeaders = bearerHeaders(loginBody.accessToken());
        ResponseEntity<ErrorResponse> staffCallsAdminEndpoint = restTemplate.exchange(
                "/internal/staff", HttpMethod.POST, new HttpEntity<>(newStaffRequest(), staffAuthHeaders),
                ErrorResponse.class);
        assertThat(staffCallsAdminEndpoint.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void should_allowRegisterAndLogin_when_noAuthorizationHeader() {
        String suffix = UUID.randomUUID().toString();
        String email = "pub" + suffix + "@test.local";
        String password = "longenoughpassword";

        RegisterRequest registerRequest = RegisterRequest.builder()
                .username("pub" + suffix)
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
    }

    private CreateStaffRequest newStaffRequest() {
        String suffix = UUID.randomUUID().toString();
        return CreateStaffRequest.builder()
                .username("op" + suffix)
                .email("op" + suffix + "@test.local")
                .password("longenoughpassword")
                .role(RoleEnum.OPERATOR)
                .build();
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private UserEntity fabricatedUser(RoleEnum role) {
        UserEntity user = UserEntity.builder()
                .email("fabricated@test.local")
                .role(role)
                .build();
        user.setId("fabricated-" + UUID.randomUUID());
        return user;
    }
}
