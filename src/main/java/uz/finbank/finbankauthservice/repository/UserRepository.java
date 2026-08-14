package uz.finbank.finbankauthservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.finbank.finbankauthservice.entity.UserEntity;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, String> {

    Optional<UserEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);
}
