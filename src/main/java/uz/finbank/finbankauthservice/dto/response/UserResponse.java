package uz.finbank.finbankauthservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uz.finbank.finbankauthservice.entity.enums.RoleEnum;
import uz.finbank.finbankauthservice.entity.enums.UserStatusEnum;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserResponse {

    private String id;
    private String username;
    private String email;
    private RoleEnum role;
    private UserStatusEnum status;
    private LocalDateTime createdAt;
}
