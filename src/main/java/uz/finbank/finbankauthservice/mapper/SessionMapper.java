package uz.finbank.finbankauthservice.mapper;

import org.springframework.stereotype.Component;
import uz.finbank.finbankauthservice.dto.response.SessionResponse;
import uz.finbank.finbankauthservice.entity.SessionEntity;

@Component
public class SessionMapper {

    public SessionResponse toResponse(SessionEntity session) {
        return SessionResponse.builder()
                .id(session.getId())
                .deviceLabel(session.getDeviceLabel())
                .ipAddress(session.getIpAddress())
                .lastUsedAt(session.getLastUsedAt())
                .createdAt(session.getCreatedAt())
                .expiresAt(session.getExpiresAt())
                .build();
    }
}
