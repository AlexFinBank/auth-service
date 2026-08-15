package uz.finbank.finbankauthservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.finbank.finbankauthservice.config.AppSecurityProperties;
import uz.finbank.finbankauthservice.entity.PasswordResetTokenEntity;
import uz.finbank.finbankauthservice.entity.UserEntity;
import uz.finbank.finbankauthservice.entity.enums.PasswordResetTokenStatusEnum;
import uz.finbank.finbankauthservice.event.EventPublisher;
import uz.finbank.finbankauthservice.event.PasswordChangedEvent;
import uz.finbank.finbankauthservice.event.PasswordResetRequestedEvent;
import uz.finbank.finbankauthservice.exception.InvalidResetTokenException;
import uz.finbank.finbankauthservice.repository.PasswordResetTokenRepository;
import uz.finbank.finbankauthservice.repository.UserRepository;
import uz.finbank.finbankauthservice.security.RateLimiterService;
import uz.finbank.finbankauthservice.security.SecureTokenGenerator;
import uz.finbank.finbankauthservice.security.TokenHasher;
import uz.finbank.finbankauthservice.service.PasswordResetService;
import uz.finbank.finbankauthservice.service.SessionService;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final SessionService sessionService;
    private final PasswordEncoder passwordEncoder;
    private final SecureTokenGenerator secureTokenGenerator;
    private final TokenHasher tokenHasher;
    private final EventPublisher eventPublisher;
    private final AppSecurityProperties securityProperties;
    private final RateLimiterService rateLimiterService;

    @Override
    @Transactional
    public void requestReset(String email, String ipAddress) {
        AppSecurityProperties.RateLimit rateLimit = securityProperties.getRateLimit();
        Duration window = Duration.ofMinutes(rateLimit.getPasswordResetWindowMinutes());
        rateLimiterService.enforce("password-reset:email:" + email.toLowerCase(),
                rateLimit.getPasswordResetMaxPerEmail(), window);
        rateLimiterService.enforce("password-reset:ip:" + ipAddress,
                rateLimit.getPasswordResetMaxPerIp(), window);

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

        sessionService.revokeAllActiveSessions(user.getId());

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
        eventPublisher.publish(PasswordResetRequestedEvent.TOPIC, user.getId(), event);
    }

    private void publishPasswordChangedEvent(UserEntity user) {
        PasswordChangedEvent event = PasswordChangedEvent.builder()
                .userId(user.getId())
                .changedAt(LocalDateTime.now())
                .build();
        eventPublisher.publish(PasswordChangedEvent.TOPIC, user.getId(), event);
    }
}
