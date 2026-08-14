package uz.finbank.finbankauthservice.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.finbank.finbankauthservice.dto.response.SessionResponse;
import uz.finbank.finbankauthservice.entity.SessionEntity;
import uz.finbank.finbankauthservice.entity.UserEntity;
import uz.finbank.finbankauthservice.entity.enums.SessionStatusEnum;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SessionMapperTest {

    private final SessionMapper sessionMapper = new SessionMapper();

    @Test
    @DisplayName("should map every SessionEntity field onto SessionResponse, excluding the refresh token hashes")
    void should_mapAllFieldsExceptHashes_when_mappingSessionEntity() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 2, 1, 9, 0);
        LocalDateTime lastUsedAt = LocalDateTime.of(2026, 2, 2, 12, 0);
        LocalDateTime expiresAt = LocalDateTime.of(2026, 2, 9, 12, 0);

        UserEntity owner = UserEntity.builder().username("carol").email("carol@finbank.uz").build();
        owner.setId("user-789");

        SessionEntity session = SessionEntity.builder()
                .user(owner)
                .refreshTokenHash("current-hash")
                .previousRefreshTokenHash("previous-hash")
                .deviceLabel("iPhone 15 - Safari")
                .ipAddress("10.0.0.5")
                .status(SessionStatusEnum.ACTIVE)
                .lastUsedAt(lastUsedAt)
                .expiresAt(expiresAt)
                .build();
        session.setId("session-001");
        session.setCreatedAt(createdAt);

        SessionResponse response = sessionMapper.toResponse(session);

        assertThat(response.id()).isEqualTo("session-001");
        assertThat(response.deviceLabel()).isEqualTo("iPhone 15 - Safari");
        assertThat(response.ipAddress()).isEqualTo("10.0.0.5");
        assertThat(response.lastUsedAt()).isEqualTo(lastUsedAt);
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
        // SessionResponse has no refreshTokenHash/previousRefreshTokenHash component at all -
        // the assertions above already cover its entire contract, confirming the hashes never leak out.
    }
}
