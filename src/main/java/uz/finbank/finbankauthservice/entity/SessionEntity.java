package uz.finbank.finbankauthservice.entity;

import jakarta.persistence.*;
import lombok.*;
import uz.finbank.finbankauthservice.entity.base.BaseEntity;
import uz.finbank.finbankauthservice.entity.enums.SessionStatusEnum;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@Table(name = "sessions")
public class SessionEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "refresh_token_hash", nullable = false, unique = true)
    private String refreshTokenHash;

    @Column(name = "device_label")
    private String deviceLabel;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private SessionStatusEnum status;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
