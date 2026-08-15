package uz.finbank.finbankauthservice.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.finbank.finbankauthservice.entity.UserEntity;
import uz.finbank.finbankauthservice.entity.enums.RoleEnum;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, String> {

    Optional<UserEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByRole(RoleEnum role);

    /**
     * Takes a DB-level row lock on this user for the rest of the current transaction, so the
     * count-then-evict-then-insert session-limit sequence in AuthServiceImpl.login() can't race
     * with another concurrent login for the SAME user (two transactions both reading "4 active
     * sessions" and both proceeding to insert a 5th and 6th).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserEntity u WHERE u.id = :id")
    Optional<UserEntity> lockById(@Param("id") String id);
}
