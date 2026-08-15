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
import uz.finbank.finbankauthservice.entity.PasswordResetTokenEntity;
import uz.finbank.finbankauthservice.entity.UserEntity;
import uz.finbank.finbankauthservice.entity.enums.PasswordResetTokenStatusEnum;
import uz.finbank.finbankauthservice.event.PasswordChangedEvent;
import uz.finbank.finbankauthservice.event.PasswordResetRequestedEvent;
import uz.finbank.finbankauthservice.exception.InvalidResetTokenException;
import uz.finbank.finbankauthservice.repository.PasswordResetTokenRepository;
import uz.finbank.finbankauthservice.repository.UserRepository;
import uz.finbank.finbankauthservice.security.SecureTokenGenerator;
import uz.finbank.finbankauthservice.security.TokenHasher;
import uz.finbank.finbankauthservice.service.SessionService;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock
    private SessionService sessionService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private SecureTokenGenerator secureTokenGenerator;
    @Mock
    private TokenHasher tokenHasher;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private AppSecurityProperties securityProperties;
    private PasswordResetServiceImpl service;

    @BeforeEach
    void setUp() {
        securityProperties = new AppSecurityProperties();
        securityProperties.getPasswordReset().setTtlMinutes(15);
        service = new PasswordResetServiceImpl(
                userRepository,
                passwordResetTokenRepository,
                sessionService,
                passwordEncoder,
                secureTokenGenerator,
                tokenHasher,
                kafkaTemplate,
                securityProperties);
    }

    private UserEntity userWithId(String id, String email) {
        UserEntity user = UserEntity.builder().username("u").email(email).password("old-hash").build();
        user.setId(id);
        return user;
    }

    @Test
    void requestReset_shouldIssueTokenAndPublishEvent_whenUserExists() {
        UserEntity user = userWithId("user-1", "user@test.local");
        when(userRepository.findByEmail("user@test.local")).thenReturn(Optional.of(user));
        when(secureTokenGenerator.generate()).thenReturn("raw-token");
        when(tokenHasher.hash("raw-token")).thenReturn("hashed-token");

        service.requestReset("user@test.local");

        ArgumentCaptor<PasswordResetTokenEntity> tokenCaptor = ArgumentCaptor.forClass(PasswordResetTokenEntity.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        PasswordResetTokenEntity saved = tokenCaptor.getValue();
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getTokenHash()).isEqualTo("hashed-token");
        assertThat(saved.getStatus()).isEqualTo(PasswordResetTokenStatusEnum.ACTIVE);
        assertThat(saved.getExpiresAt())
                .isAfter(LocalDateTime.now().plusMinutes(14))
                .isBefore(LocalDateTime.now().plusMinutes(16));

        ArgumentCaptor<PasswordResetRequestedEvent> eventCaptor = ArgumentCaptor.forClass(PasswordResetRequestedEvent.class);
        verify(kafkaTemplate).send(eq(PasswordResetRequestedEvent.TOPIC), eq("user-1"), eventCaptor.capture());
        assertThat(eventCaptor.getValue().resetToken()).isEqualTo("raw-token");
        assertThat(eventCaptor.getValue().email()).isEqualTo("user@test.local");
    }

    @Test
    void requestReset_shouldDoNothingSilently_whenUserDoesNotExist() {
        when(userRepository.findByEmail("missing@test.local")).thenReturn(Optional.empty());

        service.requestReset("missing@test.local");

        verify(passwordResetTokenRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void confirmReset_shouldUpdatePasswordRevokeSessionsAndPublishEvent_whenTokenValid() {
        UserEntity user = userWithId("user-1", "user@test.local");
        PasswordResetTokenEntity resetToken = PasswordResetTokenEntity.builder()
                .user(user)
                .tokenHash("hashed-token")
                .status(PasswordResetTokenStatusEnum.ACTIVE)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
        resetToken.setId("token-1");

        when(tokenHasher.hash("raw-token")).thenReturn("hashed-token");
        when(passwordResetTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        service.confirmReset("raw-token", "new-password");

        assertThat(user.getPassword()).isEqualTo("new-hash");
        verify(userRepository).save(user);

        assertThat(resetToken.getStatus()).isEqualTo(PasswordResetTokenStatusEnum.USED);
        verify(passwordResetTokenRepository).save(resetToken);

        verify(sessionService).revokeAllActiveSessions("user-1");

        ArgumentCaptor<PasswordChangedEvent> eventCaptor = ArgumentCaptor.forClass(PasswordChangedEvent.class);
        verify(kafkaTemplate).send(eq(PasswordChangedEvent.TOPIC), eq("user-1"), eventCaptor.capture());
        assertThat(eventCaptor.getValue().userId()).isEqualTo("user-1");
    }

    @Test
    void confirmReset_shouldThrow_whenTokenHashNotFound() {
        when(tokenHasher.hash("raw-token")).thenReturn("hashed-token");
        when(passwordResetTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmReset("raw-token", "new-password"))
                .isInstanceOf(InvalidResetTokenException.class);

        verify(userRepository, never()).save(any());
        verify(sessionService, never()).revokeAllActiveSessions(any());
    }

    @Test
    void confirmReset_shouldThrow_whenTokenAlreadyUsed() {
        UserEntity user = userWithId("user-1", "user@test.local");
        PasswordResetTokenEntity resetToken = PasswordResetTokenEntity.builder()
                .user(user)
                .tokenHash("hashed-token")
                .status(PasswordResetTokenStatusEnum.USED)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();

        when(tokenHasher.hash("raw-token")).thenReturn("hashed-token");
        when(passwordResetTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(resetToken));

        assertThatThrownBy(() -> service.confirmReset("raw-token", "new-password"))
                .isInstanceOf(InvalidResetTokenException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void confirmReset_shouldThrow_whenTokenExpired() {
        UserEntity user = userWithId("user-1", "user@test.local");
        PasswordResetTokenEntity resetToken = PasswordResetTokenEntity.builder()
                .user(user)
                .tokenHash("hashed-token")
                .status(PasswordResetTokenStatusEnum.ACTIVE)
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();

        when(tokenHasher.hash("raw-token")).thenReturn("hashed-token");
        when(passwordResetTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(resetToken));

        assertThatThrownBy(() -> service.confirmReset("raw-token", "new-password"))
                .isInstanceOf(InvalidResetTokenException.class);

        verify(userRepository, never()).save(any());
    }
}
