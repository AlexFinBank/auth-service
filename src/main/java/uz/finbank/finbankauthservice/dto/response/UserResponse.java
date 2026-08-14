package uz.finbank.finbankauthservice.dto.response;

import lombok.Builder;
import uz.finbank.finbankauthservice.entity.enums.RoleEnum;
import uz.finbank.finbankauthservice.entity.enums.UserStatusEnum;

import java.time.LocalDateTime;

@Builder
public record UserResponse(
        String id,
        String username,
        String email,
        RoleEnum role,
        UserStatusEnum status,
        LocalDateTime createdAt
) {
}
