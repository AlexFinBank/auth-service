package uz.finbank.finbankauthservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.finbank.finbankauthservice.entity.PasswordResetTokenEntity;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, String> {

    Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);
}
