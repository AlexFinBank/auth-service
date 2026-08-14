package uz.finbank.finbankauthservice.event;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record LoginFailedEvent(
        String email,
        String ipAddress,
        String reason,
        LocalDateTime attemptedAt
) {
    public static final String TOPIC = "login-failed";
}
