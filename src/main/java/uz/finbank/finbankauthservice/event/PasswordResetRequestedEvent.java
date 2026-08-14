package uz.finbank.finbankauthservice.event;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record PasswordResetRequestedEvent(
        String userId,
        String email,
        String resetToken,
        LocalDateTime expiresAt
) {
    public static final String TOPIC = "password-reset-requested";
}
