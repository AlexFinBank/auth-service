package uz.finbank.finbankauthservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.finbank.finbankauthservice.dto.response.SessionResponse;
import uz.finbank.finbankauthservice.entity.SessionEntity;
import uz.finbank.finbankauthservice.entity.enums.SessionStatusEnum;
import uz.finbank.finbankauthservice.exception.ResourceNotFoundException;
import uz.finbank.finbankauthservice.mapper.SessionMapper;
import uz.finbank.finbankauthservice.repository.SessionRepository;
import uz.finbank.finbankauthservice.security.TokenHasher;
import uz.finbank.finbankauthservice.service.SessionService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    private final SessionRepository sessionRepository;
    private final SessionMapper sessionMapper;
    private final TokenHasher tokenHasher;

    @Override
    public List<SessionResponse> getActiveSessions(String userId) {
        return sessionRepository.findByUserIdAndStatus(userId, SessionStatusEnum.ACTIVE)
                .stream()
                .map(sessionMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void revokeSession(String userId, String sessionId) {
        SessionEntity session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Session topilmadi"));
        session.setStatus(SessionStatusEnum.REVOKED);
        sessionRepository.save(session);
    }

    @Override
    @Transactional
    public void logout(String userId, String rawRefreshToken) {
        String hash = tokenHasher.hash(rawRefreshToken);
        SessionEntity session = sessionRepository.findByRefreshTokenHash(hash)
                .filter(s -> s.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Session topilmadi"));
        session.setStatus(SessionStatusEnum.REVOKED);
        sessionRepository.save(session);
    }

    @Override
    @Transactional
    public void logoutAll(String userId) {
        sessionRepository.revokeAllActiveByUserId(userId);
    }
}
