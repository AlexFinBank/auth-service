package uz.finbank.finbankauthservice.repository;

import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import uz.finbank.finbankauthservice.entity.UserEntity;
import uz.finbank.finbankauthservice.entity.enums.RoleEnum;
import uz.finbank.finbankauthservice.entity.enums.UserStatusEnum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    private UserEntity newUser(String username, String email) {
        return UserEntity.builder()
                .username(username)
                .email(email)
                .password("hashed-password")
                .role(RoleEnum.CUSTOMER)
                .status(UserStatusEnum.ACTIVE)
                .failedLoginAttempts(0)
                .build();
    }

    @Test
    void findByEmail_returnsUser_whenEmailExists() {
        UserEntity saved = testEntityManager.persistAndFlush(newUser("alice", "alice@example.com"));

        var found = userRepository.findByEmail("alice@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void findByEmail_returnsEmpty_whenEmailDoesNotExist() {
        var found = userRepository.findByEmail("nobody@example.com");

        assertThat(found).isEmpty();
    }

    @Test
    void existsByEmail_isTrue_whenEmailMatches() {
        testEntityManager.persistAndFlush(newUser("bob", "bob@example.com"));

        assertThat(userRepository.existsByEmail("bob@example.com")).isTrue();
    }

    @Test
    void existsByEmail_isFalse_whenEmailDoesNotMatch() {
        testEntityManager.persistAndFlush(newUser("bob2", "bob2@example.com"));

        assertThat(userRepository.existsByEmail("someone-else@example.com")).isFalse();
    }

    @Test
    void existsByUsername_isTrue_whenUsernameMatches() {
        testEntityManager.persistAndFlush(newUser("carol", "carol@example.com"));

        assertThat(userRepository.existsByUsername("carol")).isTrue();
    }

    @Test
    void existsByUsername_isFalse_whenUsernameDoesNotMatch() {
        testEntityManager.persistAndFlush(newUser("carol2", "carol2@example.com"));

        assertThat(userRepository.existsByUsername("nonexistent-username")).isFalse();
    }

    @Test
    void persisting_secondUserWithDuplicateEmail_violatesUniqueConstraint() {
        testEntityManager.persistAndFlush(newUser("dave", "dup@example.com"));

        UserEntity duplicate = newUser("dave2", "dup@example.com");

        assertThrows(PersistenceException.class,
                () -> testEntityManager.persistAndFlush(duplicate));
    }
}
