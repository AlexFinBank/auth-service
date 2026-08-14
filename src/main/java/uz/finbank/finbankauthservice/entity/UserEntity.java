package uz.finbank.finbankauthservice.entity;

import jakarta.persistence.*;
import lombok.*;
import uz.finbank.finbankauthservice.entity.base.BaseEntity;
import uz.finbank.finbankauthservice.entity.enums.RoleEnum;
import uz.finbank.finbankauthservice.entity.enums.UserStatusEnum;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@Table(name = "users")
public class UserEntity extends BaseEntity {
    @Column(name = "username", nullable = false, unique = true)
    private String username;
    @Column(name = "email", nullable = false, unique = true)
    private String email;
    @Column(name = "password", nullable = false)
    private String password;
    @Column(name = "role", nullable = false)
    @Enumerated(EnumType.STRING)
    private RoleEnum role;
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private UserStatusEnum status;
    @Builder.Default
    @Column(name = "failed_login_attempts", nullable = false)
    private Integer failedLoginAttempts = 0;
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;
}
