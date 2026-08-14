package uz.finbank.finbankauthservice.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record SessionResponse(
        String id,
        String deviceLabel,
        String ipAddress,
        LocalDateTime lastUsedAt,
        LocalDateTime createdAt,
        LocalDateTime expiresAt
) {
}
