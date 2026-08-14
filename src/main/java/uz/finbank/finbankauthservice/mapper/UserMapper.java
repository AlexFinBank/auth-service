package uz.finbank.finbankauthservice.mapper;

import org.springframework.stereotype.Component;
import uz.finbank.finbankauthservice.dto.response.UserResponse;
import uz.finbank.finbankauthservice.entity.UserEntity;

@Component
public class UserMapper {

    public UserResponse toResponse(UserEntity user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
