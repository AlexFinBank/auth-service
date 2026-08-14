package uz.finbank.finbankauthservice.service;

import uz.finbank.finbankauthservice.dto.response.SessionResponse;

import java.util.List;

public interface SessionService {

    List<SessionResponse> getActiveSessions(String userId);

    void revokeSession(String userId, String sessionId);

    void logout(String userId, String rawRefreshToken);

    void logoutAll(String userId);
}
