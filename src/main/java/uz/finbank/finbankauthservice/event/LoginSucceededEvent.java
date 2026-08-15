package uz.finbank.finbankauthservice.event;

import lombok.Builder;
import uz.finbank.finbankauthservice.entity.enums.RoleEnum;

import java.time.LocalDateTime;

@Builder
public record LoginSucceededEvent(
        String userId,
        String sessionId,
        RoleEnum role,
        String ipAddress,
        String deviceLabel,
        LocalDateTime loggedInAt
) {
    public static final String TOPIC = "login-succeeded";
}
