package uz.finbank.finbankauthservice.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.finbank.finbankauthservice.dto.response.UserResponse;
import uz.finbank.finbankauthservice.entity.UserEntity;
import uz.finbank.finbankauthservice.entity.enums.RoleEnum;
import uz.finbank.finbankauthservice.entity.enums.UserStatusEnum;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

    private final UserMapper userMapper = new UserMapper();

    @Test
    @DisplayName("should map every UserEntity field onto UserResponse, excluding the password")
    void should_mapAllFieldsExceptPassword_when_mappingUserEntity() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 15, 10, 30);
        UserEntity user = UserEntity.builder()
                .username("bob")
                .email("bob@finbank.uz")
                .password("argon2-hashed-secret")
                .role(RoleEnum.ADMIN)
                .status(UserStatusEnum.ACTIVE)
                .build();
        user.setId("user-456");
        user.setCreatedAt(createdAt);

        UserResponse response = userMapper.toResponse(user);

        assertThat(response.id()).isEqualTo("user-456");
        assertThat(response.username()).isEqualTo("bob");
        assertThat(response.email()).isEqualTo("bob@finbank.uz");
        assertThat(response.role()).isEqualTo(RoleEnum.ADMIN);
        assertThat(response.status()).isEqualTo(UserStatusEnum.ACTIVE);
        assertThat(response.createdAt()).isEqualTo(createdAt);
        // UserResponse has no password component at all - the record's declared
        // fields above are its entire contract, so there is nothing further to assert.
    }
}
