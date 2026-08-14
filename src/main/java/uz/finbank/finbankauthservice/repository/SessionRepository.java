package uz.finbank.finbankauthservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.finbank.finbankauthservice.entity.SessionEntity;
import uz.finbank.finbankauthservice.entity.enums.SessionStatusEnum;

import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<SessionEntity, String> {

    Optional<SessionEntity> findByRefreshTokenHash(String refreshTokenHash);

    Optional<SessionEntity> findByIdAndUserId(String id, String userId);

    List<SessionEntity> findByUserIdAndStatus(String userId, SessionStatusEnum status);

    long countByUserIdAndStatus(String userId, SessionStatusEnum status);

    Optional<SessionEntity> findFirstByUserIdAndStatusOrderByLastUsedAtAsc(String userId, SessionStatusEnum status);

    @Modifying
    @Query("UPDATE SessionEntity s SET s.status = uz.finbank.finbankauthservice.entity.enums.SessionStatusEnum.REVOKED " +
            "WHERE s.user.id = :userId AND s.status = uz.finbank.finbankauthservice.entity.enums.SessionStatusEnum.ACTIVE")
    int revokeAllActiveByUserId(@Param("userId") String userId);
}
