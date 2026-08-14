package uz.finbank.finbankauthservice.event;

import lombok.Builder;
import uz.finbank.finbankauthservice.entity.enums.RoleEnum;

import java.time.LocalDateTime;

@Builder
public record UserRegisteredEvent(
        String userId,
        String username,
        String email,
        RoleEnum role,
        LocalDateTime registeredAt
) {
    public static final String TOPIC = "user-registered";
}
