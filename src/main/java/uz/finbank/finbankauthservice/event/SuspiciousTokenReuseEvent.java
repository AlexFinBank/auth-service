package uz.finbank.finbankauthservice.event;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record SuspiciousTokenReuseEvent(
        String userId,
        String sessionId,
        String ipAddress,
        LocalDateTime detectedAt
) {
    public static final String TOPIC = "suspicious-token-reuse";
}
