package uz.finbank.finbankauthservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.finbank.finbankauthservice.config.AppSecurityProperties;
import uz.finbank.finbankauthservice.entity.PasswordResetTokenEntity;
import uz.finbank.finbankauthservice.entity.UserEntity;
import uz.finbank.finbankauthservice.entity.enums.PasswordResetTokenStatusEnum;
import uz.finbank.finbankauthservice.event.PasswordChangedEvent;
import uz.finbank.finbankauthservice.event.PasswordResetRequestedEvent;
import uz.finbank.finbankauthservice.exception.InvalidResetTokenException;
import uz.finbank.finbankauthservice.repository.PasswordResetTokenRepository;
import uz.finbank.finbankauthservice.repository.SessionRepository;
import uz.finbank.finbankauthservice.repository.UserRepository;
import uz.finbank.finbankauthservice.security.SecureTokenGenerator;
import uz.finbank.finbankauthservice.security.TokenHasher;
import uz.finbank.finbankauthservice.service.PasswordResetService;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureTokenGenerator secureTokenGenerator;
    private final TokenHasher tokenHasher;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AppSecurityProperties securityProperties;

    @Override
    @Transactional
    public void requestReset(String email) {
        userRepository.findByEmail(email).ifPresent(this::issueResetToken);
    }

    @Override
    @Transactional
    public void confirmReset(String rawToken, String newPassword) {
        String hash = tokenHasher.hash(rawToken);
        LocalDateTime now = LocalDateTime.now();

        PasswordResetTokenEntity resetToken = passwordResetTokenRepository.findByTokenHash(hash)
                .filter(t -> t.getStatus() == PasswordResetTokenStatusEnum.ACTIVE)
                .filter(t -> t.getExpiresAt().isAfter(now))
                .orElseThrow(() -> new InvalidResetTokenException("Token noto'g'ri yoki muddati tugagan"));

        UserEntity user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setStatus(PasswordResetTokenStatusEnum.USED);
        passwordResetTokenRepository.save(resetToken);

        sessionRepository.revokeAllActiveByUserId(user.getId());

        publishPasswordChangedEvent(user);
    }

    private void issueResetToken(UserEntity user) {
        String rawToken = secureTokenGenerator.generate();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(securityProperties.getPasswordReset().getTtlMinutes());

        PasswordResetTokenEntity resetToken = PasswordResetTokenEntity.builder()
                .user(user)
                .tokenHash(tokenHasher.hash(rawToken))
                .status(PasswordResetTokenStatusEnum.ACTIVE)
                .expiresAt(expiresAt)
                .build();
        passwordResetTokenRepository.save(resetToken);

        publishPasswordResetRequestedEvent(user, rawToken, expiresAt);
    }

    private void publishPasswordResetRequestedEvent(UserEntity user, String rawToken, LocalDateTime expiresAt) {
        PasswordResetRequestedEvent event = PasswordResetRequestedEvent.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .resetToken(rawToken)
                .expiresAt(expiresAt)
                .build();
        kafkaTemplate.send(PasswordResetRequestedEvent.TOPIC, user.getId(), event);
    }

    private void publishPasswordChangedEvent(UserEntity user) {
        PasswordChangedEvent event = PasswordChangedEvent.builder()
                .userId(user.getId())
                .changedAt(LocalDateTime.now())
                .build();
        kafkaTemplate.send(PasswordChangedEvent.TOPIC, user.getId(), event);
    }
}
