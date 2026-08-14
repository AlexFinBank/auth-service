package uz.finbank.finbankauthservice.event;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record PasswordChangedEvent(
        String userId,
        LocalDateTime changedAt
) {
    public static final String TOPIC = "password-changed";
}
