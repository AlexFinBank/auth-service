package uz.finbank.finbankauthservice.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import uz.finbank.finbankauthservice.entity.SessionEntity;
import uz.finbank.finbankauthservice.entity.UserEntity;
import uz.finbank.finbankauthservice.entity.enums.RoleEnum;
import uz.finbank.finbankauthservice.entity.enums.SessionStatusEnum;
import uz.finbank.finbankauthservice.entity.enums.UserStatusEnum;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SessionRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    private UserEntity persistUser(String username, String email) {
        UserEntity user = UserEntity.builder()
                .username(username)
                .email(email)
                .password("hashed-password")
                .role(RoleEnum.CUSTOMER)
                .status(UserStatusEnum.ACTIVE)
                .failedLoginAttempts(0)
                .build();
        return testEntityManager.persistAndFlush(user);
    }

    private SessionEntity newSession(UserEntity user, SessionStatusEnum status, LocalDateTime lastUsedAt) {
        return SessionEntity.builder()
                .user(user)
                .refreshTokenHash("hash-" + java.util.UUID.randomUUID())
                .status(status)
                .lastUsedAt(lastUsedAt)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
    }

    @Test
    void countByUserIdAndStatus_countsOnlyMatchingUserAndStatus() {
        UserEntity user = persistUser("user1", "user1@example.com");
        UserEntity otherUser = persistUser("user2", "user2@example.com");

        testEntityManager.persistAndFlush(newSession(user, SessionStatusEnum.ACTIVE, LocalDateTime.now()));
        testEntityManager.persistAndFlush(newSession(user, SessionStatusEnum.ACTIVE, LocalDateTime.now()));
        testEntityManager.persistAndFlush(newSession(user, SessionStatusEnum.REVOKED, LocalDateTime.now()));
        testEntityManager.persistAndFlush(newSession(otherUser, SessionStatusEnum.ACTIVE, LocalDateTime.now()));

        long count = sessionRepository.countByUserIdAndStatus(user.getId(), SessionStatusEnum.ACTIVE);

        assertThat(count).isEqualTo(2);
    }

    @Test
    void findFirstByUserIdAndStatusOrderByLastUsedAtAsc_returnsOldestActiveSession() {
        UserEntity user = persistUser("user3", "user3@example.com");
        LocalDateTime oldest = LocalDateTime.now().minusDays(5);
        LocalDateTime middle = LocalDateTime.now().minusDays(2);
        LocalDateTime newest = LocalDateTime.now();

        SessionEntity oldestSession = testEntityManager.persistAndFlush(newSession(user, SessionStatusEnum.ACTIVE, oldest));
        testEntityManager.persistAndFlush(newSession(user, SessionStatusEnum.ACTIVE, middle));
        testEntityManager.persistAndFlush(newSession(user, SessionStatusEnum.ACTIVE, newest));

        var found = sessionRepository.findFirstByUserIdAndStatusOrderByLastUsedAtAsc(user.getId(), SessionStatusEnum.ACTIVE);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(oldestSession.getId());
    }

    @Test
    void findByRefreshTokenHashOrPreviousRefreshTokenHash_findsSessionByCurrentHash() {
        UserEntity user = persistUser("user4", "user4@example.com");
        SessionEntity session = newSession(user, SessionStatusEnum.ACTIVE, LocalDateTime.now());
        session.setRefreshTokenHash("current-hash");
        session.setPreviousRefreshTokenHash("previous-hash");
        SessionEntity saved = testEntityManager.persistAndFlush(session);

        var found = sessionRepository.findByRefreshTokenHashOrPreviousRefreshTokenHash("current-hash", "current-hash");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void findByRefreshTokenHashOrPreviousRefreshTokenHash_findsSessionByPreviousHash() {
        UserEntity user = persistUser("user5", "user5@example.com");
        SessionEntity session = newSession(user, SessionStatusEnum.ACTIVE, LocalDateTime.now());
        session.setRefreshTokenHash("current-hash-2");
        session.setPreviousRefreshTokenHash("previous-hash-2");
        SessionEntity saved = testEntityManager.persistAndFlush(session);

        var found = sessionRepository.findByRefreshTokenHashOrPreviousRefreshTokenHash("previous-hash-2", "previous-hash-2");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void findByIdAndUserId_returnsEmpty_whenSessionBelongsToDifferentUser() {
        UserEntity userOne = persistUser("user6", "user6@example.com");
        UserEntity userTwo = persistUser("user7", "user7@example.com");

        SessionEntity sessionOne = testEntityManager.persistAndFlush(newSession(userOne, SessionStatusEnum.ACTIVE, LocalDateTime.now()));
        testEntityManager.persistAndFlush(newSession(userTwo, SessionStatusEnum.ACTIVE, LocalDateTime.now()));

        var found = sessionRepository.findByIdAndUserId(sessionOne.getId(), userTwo.getId());

        assertThat(found).isEmpty();
    }

    @Test
    void findByIdAndUserId_returnsSession_whenOwnedByGivenUser() {
        UserEntity user = persistUser("user8", "user8@example.com");
        SessionEntity session = testEntityManager.persistAndFlush(newSession(user, SessionStatusEnum.ACTIVE, LocalDateTime.now()));

        var found = sessionRepository.findByIdAndUserId(session.getId(), user.getId());

        assertThat(found).isPresent();
    }

    @Test
    void revokeAllActiveByUserId_revokesOnlyActiveSessionsOfGivenUser() {
        UserEntity user = persistUser("user9", "user9@example.com");
        UserEntity otherUser = persistUser("user10", "user10@example.com");

        testEntityManager.persistAndFlush(newSession(user, SessionStatusEnum.ACTIVE, LocalDateTime.now()));
        testEntityManager.persistAndFlush(newSession(user, SessionStatusEnum.ACTIVE, LocalDateTime.now()));
        SessionEntity alreadyRevoked = testEntityManager.persistAndFlush(newSession(user, SessionStatusEnum.REVOKED, LocalDateTime.now()));
        SessionEntity otherUsersActiveSession = testEntityManager.persistAndFlush(newSession(otherUser, SessionStatusEnum.ACTIVE, LocalDateTime.now()));

        int updated = sessionRepository.revokeAllActiveByUserId(user.getId());
        testEntityManager.getEntityManager().clear();

        assertThat(updated).isEqualTo(2);
        assertThat(sessionRepository.findByUserIdAndStatus(user.getId(), SessionStatusEnum.ACTIVE)).isEmpty();

        SessionEntity reloadedAlreadyRevoked = testEntityManager.find(SessionEntity.class, alreadyRevoked.getId());
        assertThat(reloadedAlreadyRevoked.getStatus()).isEqualTo(SessionStatusEnum.REVOKED);

        SessionEntity reloadedOtherUsersSession = testEntityManager.find(SessionEntity.class, otherUsersActiveSession.getId());
        assertThat(reloadedOtherUsersSession.getStatus()).isEqualTo(SessionStatusEnum.ACTIVE);
    }
}
