package uz.finbank.finbankauthservice.event;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record LoginSucceededEvent(
        String userId,
        String sessionId,
        String ipAddress,
        String deviceLabel,
        LocalDateTime loggedInAt
) {
    public static final String TOPIC = "login-succeeded";
}
