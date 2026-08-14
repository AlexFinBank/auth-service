package uz.finbank.finbankauthservice.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import uz.finbank.finbankauthservice.entity.PasswordResetTokenEntity;
import uz.finbank.finbankauthservice.entity.UserEntity;
import uz.finbank.finbankauthservice.entity.enums.PasswordResetTokenStatusEnum;
import uz.finbank.finbankauthservice.entity.enums.RoleEnum;
import uz.finbank.finbankauthservice.entity.enums.UserStatusEnum;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetTokenRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    @Test
    void findByTokenHash_returnsToken_whenHashMatches() {
        UserEntity user = testEntityManager.persistAndFlush(UserEntity.builder()
                .username("reset-user")
                .email("reset-user@example.com")
                .password("hashed-password")
                .role(RoleEnum.CUSTOMER)
                .status(UserStatusEnum.ACTIVE)
                .failedLoginAttempts(0)
                .build());

        PasswordResetTokenEntity token = testEntityManager.persistAndFlush(PasswordResetTokenEntity.builder()
                .user(user)
                .tokenHash("known-hash")
                .status(PasswordResetTokenStatusEnum.ACTIVE)
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .build());

        var found = passwordResetTokenRepository.findByTokenHash("known-hash");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(token.getId());
    }

    @Test
    void findByTokenHash_returnsEmpty_whenHashUnknown() {
        var found = passwordResetTokenRepository.findByTokenHash("unknown-hash");

        assertThat(found).isEmpty();
    }
}
