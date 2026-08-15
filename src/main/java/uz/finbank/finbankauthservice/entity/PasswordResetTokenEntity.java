package uz.finbank.finbankauthservice.entity;

import jakarta.persistence.*;
import lombok.*;
import uz.finbank.finbankauthservice.entity.base.BaseEntity;
import uz.finbank.finbankauthservice.entity.enums.PasswordResetTokenStatusEnum;

import java.time.LocalDateTime;

@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString(exclude = {"user", "tokenHash"})
@Table(name = "password_reset_tokens")
public class PasswordResetTokenEntity extends BaseEntity {

    // Excluded from toString: lazy-loaded, so toString() outside a Hibernate session would
    // throw LazyInitializationException; @Data's generated toString had this exact bug.
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
