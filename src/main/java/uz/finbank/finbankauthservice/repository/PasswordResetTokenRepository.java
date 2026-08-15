package uz.finbank.finbankauthservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.finbank.finbankauthservice.entity.PasswordResetTokenEntity;
import uz.finbank.finbankauthservice.entity.enums.PasswordResetTokenStatusEnum;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, String> {

    Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE PasswordResetTokenEntity t SET t.status = uz.finbank.finbankauthservice.entity.enums.PasswordResetTokenStatusEnum.EXPIRED " +
            "WHERE t.status = uz.finbank.finbankauthservice.entity.enums.PasswordResetTokenStatusEnum.ACTIVE AND t.expiresAt < :now")
    int expireStaleActiveTokens(@Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM PasswordResetTokenEntity t WHERE t.status IN :statuses AND t.updatedAt < :cutoff")
    int deleteByStatusInAndUpdatedAtBefore(@Param("statuses") Collection<PasswordResetTokenStatusEnum> statuses,
                                           @Param("cutoff") LocalDateTime cutoff);
}
