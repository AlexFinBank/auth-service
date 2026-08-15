package uz.finbank.finbankauthservice.service;

import uz.finbank.finbankauthservice.dto.response.SessionResponse;

import java.util.List;

public interface SessionService {

    List<SessionResponse> getActiveSessions(String userId);

    void revokeSession(String userId, String sessionId);

    void logout(String userId, String rawRefreshToken);

    void logoutAll(String userId);

    /**
     * Revokes every ACTIVE session of the given user and blacklists each one's current access
     * token jti. Shared by logoutAll(), refresh-token reuse detection, and password reset --
     * anywhere the whole account's access needs to be cut off immediately, not just future
     * refreshes.
     */
    void revokeAllActiveSessions(String userId);
}
