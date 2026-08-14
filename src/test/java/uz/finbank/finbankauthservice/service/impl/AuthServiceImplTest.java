package uz.finbank.finbankauthservice.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import uz.finbank.finbankauthservice.config.AppSecurityProperties;
import uz.finbank.finbankauthservice.dto.request.CreateStaffRequest;
import uz.finbank.finbankauthservice.dto.request.LoginRequest;
import uz.finbank.finbankauthservice.dto.request.RefreshRequest;
import uz.finbank.finbankauthservice.dto.request.RegisterRequest;
import uz.finbank.finbankauthservice.dto.response.LoginResponse;
import uz.finbank.finbankauthservice.dto.response.UserResponse;
import uz.finbank.finbankauthservice.entity.SessionEntity;
import uz.finbank.finbankauthservice.entity.UserEntity;
import uz.finbank.finbankauthservice.entity.enums.RoleEnum;
import uz.finbank.finbankauthservice.entity.enums.SessionStatusEnum;
import uz.finbank.finbankauthservice.entity.enums.UserStatusEnum;
import uz.finbank.finbankauthservice.event.LoginFailedEvent;
import uz.finbank.finbankauthservice.event.LoginSucceededEvent;
import uz.finbank.finbankauthservice.event.SuspiciousTokenReuseEvent;
import uz.finbank.finbankauthservice.event.UserRegisteredEvent;
import uz.finbank.finbankauthservice.exception.AccountDisabledException;
import uz.finbank.finbankauthservice.exception.AccountLockedException;
import uz.finbank.finbankauthservice.exception.DuplicateResourceException;
import uz.finbank.finbankauthservice.exception.InvalidCredentialsException;
import uz.finbank.finbankauthservice.exception.InvalidRefreshTokenException;
import uz.finbank.finbankauthservice.exception.InvalidRequestException;
import uz.finbank.finbankauthservice.mapper.UserMapper;
import uz.finbank.finbankauthservice.repository.SessionRepository;
import uz.finbank.finbankauthservice.repository.UserRepository;
import uz.finbank.finbankauthservice.security.JwtTokenProvider;
import uz.finbank.finbankauthservice.security.SecureTokenGenerator;
import uz.finbank.finbankauthservice.security.TokenHasher;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserMapper userMapper;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private SecureTokenGenerator secureTokenGenerator;
    @Mock
    private TokenHasher tokenHasher;

    private AppSecurityProperties securityProperties;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        securityProperties = new AppSecurityProperties();
        securityProperties.getJwt().setAccessTokenTtlMinutes(15);
        securityProperties.getRefreshToken().setTtlDays(7);
        securityProperties.getSession().setMaxDevices(5);
        securityProperties.getLogin().setMaxFailedAttempts(5);
        securityProperties.getLogin().setLockoutMinutes(30);

        authService = new AuthServiceImpl(
                userRepository,
                sessionRepository,
                passwordEncoder,
                userMapper,
                kafkaTemplate,
                securityProperties,
                jwtTokenProvider,
                secureTokenGenerator,
                tokenHasher
        );
    }

    private static UserEntity buildUser(String id, String email, String username, String encodedPassword,
                                         RoleEnum role, UserStatusEnum status, int failedAttempts,
                                         LocalDateTime lockedUntil) {
        UserEntity user = UserEntity.builder()
                .username(username)
                .email(email)
                .password(encodedPassword)
                .role(role)
                .status(status)
                .failedLoginAttempts(failedAttempts)
                .lockedUntil(lockedUntil)
                .build();
        user.setId(id);
        return user;
    }

    // ---------- register() ----------

    @Test
    void should_registerUser_when_emailAndUsernameAreUnique() {
        RegisterRequest request = RegisterRequest.builder()
                .username("john")
                .email("john@example.com")
                .password("plain-password")
                .build();

        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("john")).thenReturn(false);
        when(passwordEncoder.encode("plain-password")).thenReturn("encoded-password");

        UserEntity savedUser = buildUser("user-1", "john@example.com", "john", "encoded-password",
                RoleEnum.CUSTOMER, UserStatusEnum.ACTIVE, 0, null);
        when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);

        UserResponse mappedResponse = UserResponse.builder()
                .id("user-1").username("john").email("john@example.com")
                .role(RoleEnum.CUSTOMER).status(UserStatusEnum.ACTIVE).build();
        when(userMapper.toResponse(savedUser)).thenReturn(mappedResponse);

        UserResponse result = authService.register(request);

        assertThat(result).isEqualTo(mappedResponse);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        UserEntity toSave = captor.getValue();
        assertThat(toSave.getUsername()).isEqualTo("john");
        assertThat(toSave.getEmail()).isEqualTo("john@example.com");
        assertThat(toSave.getPassword()).isEqualTo("encoded-password");
        assertThat(toSave.getRole()).isEqualTo(RoleEnum.CUSTOMER);
        assertThat(toSave.getStatus()).isEqualTo(UserStatusEnum.ACTIVE);

        verify(kafkaTemplate).send(eq(UserRegisteredEvent.TOPIC), eq("user-1"), any(UserRegisteredEvent.class));
    }

    @Test
    void should_throwDuplicateResourceException_when_emailAlreadyExists() {
        RegisterRequest request = RegisterRequest.builder()
                .username("john").email("john@example.com").password("plain-password").build();

        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void should_throwDuplicateResourceException_when_usernameAlreadyExists() {
        RegisterRequest request = RegisterRequest.builder()
                .username("john").email("john@example.com").password("plain-password").build();

        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("john")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class);

        verify(userRepository, never()).save(any());
    }

    // ---------- createStaff() ----------

    @Test
    void should_createStaff_when_roleIsAdmin() {
        CreateStaffRequest request = CreateStaffRequest.builder()
                .username("staffadmin").email("admin@example.com").password("plain-password")
                .role(RoleEnum.ADMIN).build();

        when(userRepository.existsByEmail("admin@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("staffadmin")).thenReturn(false);
        when(passwordEncoder.encode("plain-password")).thenReturn("encoded-password");

        UserEntity savedUser = buildUser("user-2", "admin@example.com", "staffadmin", "encoded-password",
                RoleEnum.ADMIN, UserStatusEnum.ACTIVE, 0, null);
        when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);

        UserResponse mappedResponse = UserResponse.builder()
                .id("user-2").username("staffadmin").email("admin@example.com")
                .role(RoleEnum.ADMIN).status(UserStatusEnum.ACTIVE).build();
        when(userMapper.toResponse(savedUser)).thenReturn(mappedResponse);

        UserResponse result = authService.createStaff(request);

        assertThat(result).isEqualTo(mappedResponse);
        verify(userRepository).save(argThat(u -> u.getRole() == RoleEnum.ADMIN));
        verify(kafkaTemplate).send(eq(UserRegisteredEvent.TOPIC), eq("user-2"), any(UserRegisteredEvent.class));
    }

    @Test
    void should_createStaff_when_roleIsOperator() {
        CreateStaffRequest request = CreateStaffRequest.builder()
                .username("staffoperator").email("operator@example.com").password("plain-password")
                .role(RoleEnum.OPERATOR).build();

        when(userRepository.existsByEmail("operator@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("staffoperator")).thenReturn(false);
        when(passwordEncoder.encode("plain-password")).thenReturn("encoded-password");

        UserEntity savedUser = buildUser("user-3", "operator@example.com", "staffoperator", "encoded-password",
                RoleEnum.OPERATOR, UserStatusEnum.ACTIVE, 0, null);
        when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);

        UserResponse mappedResponse = UserResponse.builder()
                .id("user-3").username("staffoperator").email("operator@example.com")
                .role(RoleEnum.OPERATOR).status(UserStatusEnum.ACTIVE).build();
        when(userMapper.toResponse(savedUser)).thenReturn(mappedResponse);

        UserResponse result = authService.createStaff(request);

        assertThat(result).isEqualTo(mappedResponse);
        verify(userRepository).save(argThat(u -> u.getRole() == RoleEnum.OPERATOR));
    }

    @Test
    void should_createStaff_when_roleIsAuditor() {
        CreateStaffRequest request = CreateStaffRequest.builder()
                .username("staffauditor").email("auditor@example.com").password("plain-password")
                .role(RoleEnum.AUDITOR).build();

        when(userRepository.existsByEmail("auditor@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("staffauditor")).thenReturn(false);
        when(passwordEncoder.encode("plain-password")).thenReturn("encoded-password");

        UserEntity savedUser = buildUser("user-4", "auditor@example.com", "staffauditor", "encoded-password",
                RoleEnum.AUDITOR, UserStatusEnum.ACTIVE, 0, null);
        when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);

        UserResponse mappedResponse = UserResponse.builder()
                .id("user-4").username("staffauditor").email("auditor@example.com")
                .role(RoleEnum.AUDITOR).status(UserStatusEnum.ACTIVE).build();
        when(userMapper.toResponse(savedUser)).thenReturn(mappedResponse);

        UserResponse result = authService.createStaff(request);

        assertThat(result).isEqualTo(mappedResponse);
        verify(userRepository).save(argThat(u -> u.getRole() == RoleEnum.AUDITOR));
    }

    @Test
    void should_throwInvalidRequestException_when_creatingStaffWithCustomerRole() {
        CreateStaffRequest request = CreateStaffRequest.builder()
                .username("john").email("john@example.com").password("plain-password")
                .role(RoleEnum.CUSTOMER).build();

        assertThatThrownBy(() -> authService.createStaff(request))
                .isInstanceOf(InvalidRequestException.class);

        verifyNoInteractions(userRepository);
    }

    @Test
    void should_throwDuplicateResourceException_when_creatingStaffWithExistingEmail() {
        CreateStaffRequest request = CreateStaffRequest.builder()
                .username("newoperator").email("taken@example.com").password("plain-password")
                .role(RoleEnum.OPERATOR).build();

        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.createStaff(request))
                .isInstanceOf(DuplicateResourceException.class);

        verify(userRepository, never()).save(any());
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void should_throwDuplicateResourceException_when_creatingStaffWithExistingUsername() {
        CreateStaffRequest request = CreateStaffRequest.builder()
                .username("takenname").email("fresh@example.com").password("plain-password")
                .role(RoleEnum.ADMIN).build();

        when(userRepository.existsByEmail("fresh@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("takenname")).thenReturn(true);

        assertThatThrownBy(() -> authService.createStaff(request))
                .isInstanceOf(DuplicateResourceException.class);

        verify(userRepository, never()).save(any());
        verifyNoInteractions(kafkaTemplate);
    }

    // ---------- login() ----------

    @Test
    void should_loginSuccessfully_when_credentialsAreCorrectAndUnderSessionLimit() {
        UserEntity user = buildUser("user-1", "john@example.com", "john", "encoded-password",
                RoleEnum.CUSTOMER, UserStatusEnum.ACTIVE, 2, null);

        LoginRequest request = LoginRequest.builder()
                .email("john@example.com").password("plain-password").deviceLabel("Chrome on Mac").build();

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("plain-password", "encoded-password")).thenReturn(true);
        when(sessionRepository.countByUserIdAndStatus("user-1", SessionStatusEnum.ACTIVE)).thenReturn(1L);
        when(secureTokenGenerator.generate()).thenReturn("raw-token-1");
        when(tokenHasher.hash("raw-token-1")).thenReturn("hash-of-raw-token-1");
        when(jwtTokenProvider.generateAccessToken(user)).thenReturn("access-token-1");
        when(sessionRepository.save(any(SessionEntity.class))).thenAnswer(inv -> {
            SessionEntity s = inv.getArgument(0);
            s.setId("session-1");
            return s;
        });

        LoginResponse response = authService.login(request, "127.0.0.1");

        assertThat(response.accessToken()).isEqualTo("access-token-1");
        assertThat(response.refreshToken()).isEqualTo("raw-token-1");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(15 * 60);

        verify(userRepository).save(argThat(u -> u.getFailedLoginAttempts() == 0));
        verify(kafkaTemplate).send(eq(LoginSucceededEvent.TOPIC), eq("user-1"), any(LoginSucceededEvent.class));
        verify(sessionRepository, never()).findFirstByUserIdAndStatusOrderByLastUsedAtAsc(any(), any());
    }

    @Test
    void should_throwInvalidCredentialsException_when_emailNotFound() {
        LoginRequest request = LoginRequest.builder()
                .email("ghost@example.com").password("whatever").build();

        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request, "127.0.0.1"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void should_incrementFailedAttempts_when_passwordWrongAndBelowThreshold() {
        UserEntity user = buildUser("user-1", "john@example.com", "john", "encoded-password",
                RoleEnum.CUSTOMER, UserStatusEnum.ACTIVE, 2, null);

        LoginRequest request = LoginRequest.builder()
                .email("john@example.com").password("wrong-password").build();

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request, "10.0.0.1"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(userRepository).save(argThat(u ->
                u.getFailedLoginAttempts() == 3 && u.getStatus() == UserStatusEnum.ACTIVE));
        verify(kafkaTemplate).send(eq(LoginFailedEvent.TOPIC), eq("john@example.com"), any(LoginFailedEvent.class));
    }

    @Test
    void should_lockAccount_when_failedAttemptsReachMaxThreshold() {
        UserEntity user = buildUser("user-1", "john@example.com", "john", "encoded-password",
                RoleEnum.CUSTOMER, UserStatusEnum.ACTIVE, 4, null);

        LoginRequest request = LoginRequest.builder()
                .email("john@example.com").password("wrong-password").build();

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request, "10.0.0.1"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(userRepository).save(argThat(u ->
                u.getStatus() == UserStatusEnum.LOCKED
                        && u.getFailedLoginAttempts() == 0
                        && u.getLockedUntil() != null
                        && u.getLockedUntil().isAfter(LocalDateTime.now())));
    }

    @Test
    void should_rejectLogin_when_accountIsLockedAndLockNotExpired() {
        UserEntity user = buildUser("user-1", "john@example.com", "john", "encoded-password",
                RoleEnum.CUSTOMER, UserStatusEnum.LOCKED, 0, LocalDateTime.now().plusMinutes(10));

        LoginRequest request = LoginRequest.builder()
                .email("john@example.com").password("plain-password").build();

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(request, "10.0.0.1"))
                .isInstanceOf(AccountLockedException.class);

        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void should_autoUnlockAndProceed_when_accountIsLockedButLockExpired() {
        UserEntity user = buildUser("user-1", "john@example.com", "john", "encoded-password",
                RoleEnum.CUSTOMER, UserStatusEnum.LOCKED, 0, LocalDateTime.now().minusMinutes(1));

        LoginRequest request = LoginRequest.builder()
                .email("john@example.com").password("plain-password").build();

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("plain-password", "encoded-password")).thenReturn(true);
        when(sessionRepository.countByUserIdAndStatus("user-1", SessionStatusEnum.ACTIVE)).thenReturn(0L);
        when(secureTokenGenerator.generate()).thenReturn("raw-token-x");
        when(tokenHasher.hash("raw-token-x")).thenReturn("hash-x");
        when(jwtTokenProvider.generateAccessToken(user)).thenReturn("access-x");
        when(sessionRepository.save(any(SessionEntity.class))).thenAnswer(inv -> {
            SessionEntity s = inv.getArgument(0);
            s.setId("session-x");
            return s;
        });

        LoginResponse response = authService.login(request, "10.0.0.1");

        assertThat(response.accessToken()).isEqualTo("access-x");

        verify(userRepository, atLeastOnce()).save(argThat(u ->
                u.getStatus() == UserStatusEnum.ACTIVE && u.getLockedUntil() == null));
    }

    @Test
    void should_rejectLogin_when_accountIsDisabled() {
        UserEntity user = buildUser("user-1", "john@example.com", "john", "encoded-password",
                RoleEnum.CUSTOMER, UserStatusEnum.DISABLED, 0, null);

        LoginRequest request = LoginRequest.builder()
                .email("john@example.com").password("plain-password").build();

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(request, "10.0.0.1"))
                .isInstanceOf(AccountDisabledException.class);

        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void should_evictOldestSession_when_activeSessionCountReachesMaxDevices() {
        UserEntity user = buildUser("user-1", "john@example.com", "john", "encoded-password",
                RoleEnum.CUSTOMER, UserStatusEnum.ACTIVE, 0, null);

        LoginRequest request = LoginRequest.builder()
                .email("john@example.com").password("plain-password").build();

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("plain-password", "encoded-password")).thenReturn(true);
        when(sessionRepository.countByUserIdAndStatus("user-1", SessionStatusEnum.ACTIVE)).thenReturn(5L);

        SessionEntity oldestSession = SessionEntity.builder()
                .user(user).refreshTokenHash("old-hash").status(SessionStatusEnum.ACTIVE)
                .lastUsedAt(LocalDateTime.now().minusDays(3)).expiresAt(LocalDateTime.now().plusDays(4)).build();
        oldestSession.setId("oldest-session");
        when(sessionRepository.findFirstByUserIdAndStatusOrderByLastUsedAtAsc("user-1", SessionStatusEnum.ACTIVE))
                .thenReturn(Optional.of(oldestSession));

        when(secureTokenGenerator.generate()).thenReturn("raw-token-y");
        when(tokenHasher.hash("raw-token-y")).thenReturn("hash-y");
        when(jwtTokenProvider.generateAccessToken(user)).thenReturn("access-y");
        when(sessionRepository.save(any(SessionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.login(request, "10.0.0.1");

        assertThat(oldestSession.getStatus()).isEqualTo(SessionStatusEnum.REVOKED);
        verify(sessionRepository).save(oldestSession);
        verify(sessionRepository, times(2)).save(any(SessionEntity.class));
    }

    // ---------- refresh() ----------

    @Test
    void should_rotateRefreshToken_when_currentHashMatchesAndSessionIsActive() {
        UserEntity user = buildUser("user-1", "john@example.com", "john", "encoded-password",
                RoleEnum.CUSTOMER, UserStatusEnum.ACTIVE, 0, null);

        SessionEntity session = SessionEntity.builder()
                .user(user).refreshTokenHash("hash-of-current-token").previousRefreshTokenHash(null)
                .status(SessionStatusEnum.ACTIVE).lastUsedAt(LocalDateTime.now().minusHours(1))
                .expiresAt(LocalDateTime.now().plusDays(3)).build();
        session.setId("session-1");

        RefreshRequest request = RefreshRequest.builder().refreshToken("current-raw-token").build();

        when(tokenHasher.hash("current-raw-token")).thenReturn("hash-of-current-token");
        when(sessionRepository.findByRefreshTokenHashOrPreviousRefreshTokenHash(
                "hash-of-current-token", "hash-of-current-token")).thenReturn(Optional.of(session));
        when(secureTokenGenerator.generate()).thenReturn("new-raw-token");
        when(tokenHasher.hash("new-raw-token")).thenReturn("hash-of-new-token");
        when(jwtTokenProvider.generateAccessToken(user)).thenReturn("new-access-token");

        LoginResponse response = authService.refresh(request, "10.0.0.1");

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-raw-token");
        assertThat(session.getPreviousRefreshTokenHash()).isEqualTo("hash-of-current-token");
        assertThat(session.getRefreshTokenHash()).isEqualTo("hash-of-new-token");
        verify(sessionRepository).save(session);
        verify(sessionRepository, never()).revokeAllActiveByUserId(any());
    }

    @Test
    void should_detectReuseAndRevokeAllSessions_when_presentedHashMatchesPreviousHash() {
        UserEntity user = buildUser("user-1", "john@example.com", "john", "encoded-password",
                RoleEnum.CUSTOMER, UserStatusEnum.ACTIVE, 0, null);

        SessionEntity session = SessionEntity.builder()
                .user(user).refreshTokenHash("hash-of-newer-token").previousRefreshTokenHash("hash-of-stolen-token")
                .status(SessionStatusEnum.ACTIVE).lastUsedAt(LocalDateTime.now().minusMinutes(5))
                .expiresAt(LocalDateTime.now().plusDays(3)).build();
        session.setId("session-1");

        RefreshRequest request = RefreshRequest.builder().refreshToken("stolen-raw-token").build();

        when(tokenHasher.hash("stolen-raw-token")).thenReturn("hash-of-stolen-token");
        when(sessionRepository.findByRefreshTokenHashOrPreviousRefreshTokenHash(
                "hash-of-stolen-token", "hash-of-stolen-token")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> authService.refresh(request, "10.0.0.1"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(sessionRepository).revokeAllActiveByUserId("user-1");
        verify(kafkaTemplate).send(eq(SuspiciousTokenReuseEvent.TOPIC), eq("user-1"), any(SuspiciousTokenReuseEvent.class));

        assertThat(session.getRefreshTokenHash()).isEqualTo("hash-of-newer-token");
        verify(sessionRepository, never()).save(any());
        verify(secureTokenGenerator, never()).generate();
    }

    @Test
    void should_throwInvalidRefreshTokenException_when_noSessionMatchesEitherHash() {
        RefreshRequest request = RefreshRequest.builder().refreshToken("unknown-raw-token").build();

        when(tokenHasher.hash("unknown-raw-token")).thenReturn("hash-of-unknown-token");
        when(sessionRepository.findByRefreshTokenHashOrPreviousRefreshTokenHash(
                "hash-of-unknown-token", "hash-of-unknown-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(request, "10.0.0.1"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(sessionRepository, never()).revokeAllActiveByUserId(any());
    }

    @Test
    void should_throwInvalidRefreshTokenException_when_sessionMatchesCurrentHashButExpired() {
        UserEntity user = buildUser("user-1", "john@example.com", "john", "encoded-password",
                RoleEnum.CUSTOMER, UserStatusEnum.ACTIVE, 0, null);

        SessionEntity session = SessionEntity.builder()
                .user(user).refreshTokenHash("hash-of-expired-token").previousRefreshTokenHash(null)
                .status(SessionStatusEnum.ACTIVE).lastUsedAt(LocalDateTime.now().minusDays(10))
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();
        session.setId("session-1");

        RefreshRequest request = RefreshRequest.builder().refreshToken("expired-raw-token").build();

        when(tokenHasher.hash("expired-raw-token")).thenReturn("hash-of-expired-token");
        when(sessionRepository.findByRefreshTokenHashOrPreviousRefreshTokenHash(
                "hash-of-expired-token", "hash-of-expired-token")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> authService.refresh(request, "10.0.0.1"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(sessionRepository, never()).save(any());
        verify(secureTokenGenerator, never()).generate();
    }
}
