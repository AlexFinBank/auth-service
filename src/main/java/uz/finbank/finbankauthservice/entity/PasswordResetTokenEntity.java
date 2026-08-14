package uz.finbank.finbankauthservice.entity;

import jakarta.persistence.*;
import lombok.*;
import uz.finbank.finbankauthservice.entity.base.BaseEntity;
import uz.finbank.finbankauthservice.entity.enums.PasswordResetTokenStatusEnum;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@Table(name = "password_reset_tokens")
public class PasswordResetTokenEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private PasswordResetTokenStatusEnum status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
