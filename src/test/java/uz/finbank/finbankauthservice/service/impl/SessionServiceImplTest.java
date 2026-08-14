package uz.finbank.finbankauthservice.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.finbank.finbankauthservice.dto.response.SessionResponse;
import uz.finbank.finbankauthservice.entity.SessionEntity;
import uz.finbank.finbankauthservice.entity.UserEntity;
import uz.finbank.finbankauthservice.entity.enums.SessionStatusEnum;
import uz.finbank.finbankauthservice.exception.ResourceNotFoundException;
import uz.finbank.finbankauthservice.mapper.SessionMapper;
import uz.finbank.finbankauthservice.repository.SessionRepository;
import uz.finbank.finbankauthservice.security.TokenHasher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceImplTest {

    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private SessionMapper sessionMapper;
    @Mock
    private TokenHasher tokenHasher;

    private SessionServiceImpl newService() {
        return new SessionServiceImpl(sessionRepository, sessionMapper, tokenHasher);
    }

    private UserEntity userWithId(String id) {
        UserEntity user = UserEntity.builder().username("u").email("e@test.local").password("p").build();
        user.setId(id);
        return user;
    }

    private SessionEntity sessionFor(String id, UserEntity user, String refreshTokenHash) {
        SessionEntity session = SessionEntity.builder()
                .user(user)
                .refreshTokenHash(refreshTokenHash)
                .status(SessionStatusEnum.ACTIVE)
                .build();
        session.setId(id);
        return session;
    }

    @Test
    void getActiveSessions_shouldMapEveryActiveSessionThroughMapper() {
        UserEntity user = userWithId("user-1");
        SessionEntity s1 = sessionFor("s1", user, "hash1");
        SessionEntity s2 = sessionFor("s2", user, "hash2");
        SessionResponse r1 = SessionResponse.builder().id("s1").build();
        SessionResponse r2 = SessionResponse.builder().id("s2").build();

        when(sessionRepository.findByUserIdAndStatus("user-1", SessionStatusEnum.ACTIVE))
                .thenReturn(List.of(s1, s2));
        when(sessionMapper.toResponse(s1)).thenReturn(r1);
        when(sessionMapper.toResponse(s2)).thenReturn(r2);

        List<SessionResponse> result = newService().getActiveSessions("user-1");

        assertThat(result).containsExactly(r1, r2);
    }

    @Test
    void revokeSession_shouldRevokeAndSave_whenSessionBelongsToUser() {
        UserEntity user = userWithId("user-1");
        SessionEntity session = sessionFor("s1", user, "hash1");
        when(sessionRepository.findByIdAndUserId("s1", "user-1")).thenReturn(Optional.of(session));

        newService().revokeSession("user-1", "s1");

        assertThat(session.getStatus()).isEqualTo(SessionStatusEnum.REVOKED);
        verify(sessionRepository).save(session);
    }

    @Test
    void revokeSession_shouldThrowNotFound_whenSessionMissingOrBelongsToAnotherUser() {
        when(sessionRepository.findByIdAndUserId("s1", "user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().revokeSession("user-1", "s1"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(sessionRepository, never()).save(any());
    }

    @Test
    void logout_shouldRevokeAndSave_whenTokenBelongsToRequestingUser() {
        UserEntity user = userWithId("user-1");
        SessionEntity session = sessionFor("s1", user, "hashed-token");
        when(tokenHasher.hash("raw-token")).thenReturn("hashed-token");
        when(sessionRepository.findByRefreshTokenHash("hashed-token")).thenReturn(Optional.of(session));

        newService().logout("user-1", "raw-token");

        assertThat(session.getStatus()).isEqualTo(SessionStatusEnum.REVOKED);
        verify(sessionRepository).save(session);
    }

    @Test
    void logout_shouldThrowNotFound_whenSessionBelongsToDifferentUser() {
        UserEntity otherUser = userWithId("someone-else");
        SessionEntity session = sessionFor("s1", otherUser, "hashed-token");
        when(tokenHasher.hash("raw-token")).thenReturn("hashed-token");
        when(sessionRepository.findByRefreshTokenHash("hashed-token")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> newService().logout("user-1", "raw-token"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(sessionRepository, never()).save(any());
    }

    @Test
    void logout_shouldThrowNotFound_whenNoSessionMatchesToken() {
        when(tokenHasher.hash("raw-token")).thenReturn("hashed-token");
        when(sessionRepository.findByRefreshTokenHash("hashed-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().logout("user-1", "raw-token"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(sessionRepository, never()).save(any());
    }

    @Test
    void logoutAll_shouldDelegateToRevokeAllActiveByUserId() {
        newService().logoutAll("user-1");

        verify(sessionRepository).revokeAllActiveByUserId(eq("user-1"));
    }
}
