package uz.finbank.finbankauthservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import uz.finbank.finbankauthservice.config.AppSecurityProperties;
import uz.finbank.finbankauthservice.dto.request.CreateStaffRequest;
import uz.finbank.finbankauthservice.dto.request.LoginRequest;
import uz.finbank.finbankauthservice.dto.request.RefreshRequest;
import uz.finbank.finbankauthservice.dto.request.RegisterRequest;
import uz.finbank.finbankauthservice.dto.response.LoginResponse;
import uz.finbank.finbankauthservice.dto.response.UserResponse;
import uz.finbank.finbankauthservice.entity.SessionEntity;
import uz.finbank.finbankauthservice.entity.UserEntity;
import uz.finbank.finbankauthservice.entity.enums.RoleEnum;
import uz.finbank.finbankauthservice.entity.enums.SessionStatusEnum;
import uz.finbank.finbankauthservice.entity.enums.UserStatusEnum;
import uz.finbank.finbankauthservice.event.EventPublisher;
import uz.finbank.finbankauthservice.event.LoginFailedEvent;
import uz.finbank.finbankauthservice.event.LoginSucceededEvent;
import uz.finbank.finbankauthservice.event.SuspiciousTokenReuseEvent;
import uz.finbank.finbankauthservice.event.UserRegisteredEvent;
import uz.finbank.finbankauthservice.idempotency.IdempotencyService;
import uz.finbank.finbankauthservice.exception.AccountDisabledException;
import uz.finbank.finbankauthservice.exception.AccountLockedException;
import uz.finbank.finbankauthservice.exception.DuplicateResourceException;
import uz.finbank.finbankauthservice.exception.InvalidCredentialsException;
import uz.finbank.finbankauthservice.exception.InvalidRefreshTokenException;
import uz.finbank.finbankauthservice.exception.InvalidRequestException;
import uz.finbank.finbankauthservice.mapper.UserMapper;
import uz.finbank.finbankauthservice.repository.SessionRepository;
import uz.finbank.finbankauthservice.repository.UserRepository;
import uz.finbank.finbankauthservice.security.JwtTokenProvider;
import uz.finbank.finbankauthservice.security.RateLimiterService;
import uz.finbank.finbankauthservice.security.SecureTokenGenerator;
import uz.finbank.finbankauthservice.security.TokenHasher;
import uz.finbank.finbankauthservice.service.AuthService;
import uz.finbank.finbankauthservice.service.SessionService;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String UNKNOWN_DEVICE = "Unknown device";

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final EventPublisher eventPublisher;
    private final AppSecurityProperties securityProperties;
    private final JwtTokenProvider jwtTokenProvider;
    private final SecureTokenGenerator secureTokenGenerator;
    private final TokenHasher tokenHasher;
    private final SessionService sessionService;
    private final RateLimiterService rateLimiterService;
    private final IdempotencyService idempotencyService;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request, String idempotencyKey) {
        if (StringUtils.hasText(idempotencyKey)) {
            return idempotencyService.executeRegisterIdempotently(idempotencyKey, () -> doRegister(request));
        }
        return doRegister(request);
    }

    private UserResponse doRegister(RegisterRequest request) {
        UserEntity savedUser = createUser(request.username(), request.email(), request.password(), RoleEnum.CUSTOMER);
        publishUserRegisteredEvent(savedUser);
        return userMapper.toResponse(savedUser);
    }

    @Override
    @Transactional
    public UserResponse createStaff(CreateStaffRequest request) {
        if (request.role() == RoleEnum.CUSTOMER) {
            throw new InvalidRequestException("CUSTOMER roli /register orqali ro'yxatdan o'tadi, /internal/staff orqali emas");
        }

        UserEntity savedUser = createUser(request.username(), request.email(), request.password(), request.role());
        publishUserRegisteredEvent(savedUser);
        return userMapper.toResponse(savedUser);
    }

    private UserEntity createUser(String username, String email, String rawPassword, RoleEnum role) {
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email allaqachon ro'yxatdan o'tgan: " + email);
        }
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateResourceException("Username band: " + username);
        }

        UserEntity user = UserEntity.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                .role(role)
                .status(UserStatusEnum.ACTIVE)
                .build();

        return userRepository.save(user);
    }

    @Override
    // A failed attempt below the lockout threshold, or the lockout itself, is persisted via
    // registerFailedAttempt() and THEN this method throws InvalidCredentialsException -- without
    // this exemption Spring's default rollback-on-RuntimeException would silently undo that
    // write, and the 5-failed-attempts lockout would never actually persist.
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public LoginResponse login(LoginRequest request, String ipAddress) {
        enforceLoginRateLimit(request.email(), ipAddress);

        UserEntity user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new InvalidCredentialsException("Email yoki parol noto'g'ri"));

        ensureAccountUsable(user);

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            registerFailedAttempt(user, ipAddress);
            throw new InvalidCredentialsException("Email yoki parol noto'g'ri");
        }

        if (user.getFailedLoginAttempts() != 0) {
            user.setFailedLoginAttempts(0);
            userRepository.save(user);
        }

        // Serializes concurrent logins for this exact user for the rest of the transaction, so
        // the count-then-evict-then-insert sequence below can't race with another simultaneous
        // login and let more than maxDevices sessions end up ACTIVE at once.
        userRepository.lockById(user.getId());

        evictOldestSessionIfLimitReached(user);

        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String jti = jwtTokenProvider.extractJti(accessToken);
        String rawRefreshToken = secureTokenGenerator.generate();
        SessionEntity session = createSession(user, rawRefreshToken, jti, request.deviceLabel(), ipAddress);

        publishLoginSucceededEvent(user, session, ipAddress);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .tokenType("Bearer")
                .expiresInSeconds(securityProperties.getJwt().getAccessTokenTtlMinutes() * 60)
                .build();
    }

    @Override
    // Reuse detection revokes all sessions via handleSuspiciousReuse() and THEN throws
    // InvalidRefreshTokenException -- same rollback trap as login() above: without this
    // exemption, the security-critical revoke-all would be undone by the throw.
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public LoginResponse refresh(RefreshRequest request, String ipAddress) {
        String presentedHash = tokenHasher.hash(request.refreshToken());

        SessionEntity session = sessionRepository
                .findByRefreshTokenHashOrPreviousRefreshTokenHash(presentedHash, presentedHash)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token noto'g'ri"));

        if (presentedHash.equals(session.getPreviousRefreshTokenHash())) {
            handleSuspiciousReuse(session, ipAddress);
            throw new InvalidRefreshTokenException("Refresh token noto'g'ri");
        }

        if (session.getStatus() != SessionStatusEnum.ACTIVE
                || session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidRefreshTokenException("Refresh token noto'g'ri");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(session.getUser());
        String jti = jwtTokenProvider.extractJti(accessToken);
        String newRawRefreshToken = secureTokenGenerator.generate();
        rotateSession(session, newRawRefreshToken, jti);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRawRefreshToken)
                .tokenType("Bearer")
                .expiresInSeconds(securityProperties.getJwt().getAccessTokenTtlMinutes() * 60)
                .build();
    }

    private void rotateSession(SessionEntity session, String newRawRefreshToken, String accessTokenJti) {
        LocalDateTime now = LocalDateTime.now();
        session.setPreviousRefreshTokenHash(session.getRefreshTokenHash());
        session.setRefreshTokenHash(tokenHasher.hash(newRawRefreshToken));
        session.setAccessTokenJti(accessTokenJti);
        session.setLastUsedAt(now);
        session.setExpiresAt(now.plusDays(securityProperties.getRefreshToken().getTtlDays()));
        sessionRepository.save(session);
    }

    private void handleSuspiciousReuse(SessionEntity session, String ipAddress) {
        sessionService.revokeAllActiveSessions(session.getUser().getId());
        publishSuspiciousTokenReuseEvent(session, ipAddress);
    }

    private void enforceLoginRateLimit(String email, String ipAddress) {
        AppSecurityProperties.RateLimit rateLimit = securityProperties.getRateLimit();
        Duration window = Duration.ofMinutes(rateLimit.getLoginWindowMinutes());
        rateLimiterService.enforce("login:email:" + email.toLowerCase(), rateLimit.getLoginMaxPerEmail(), window);
        rateLimiterService.enforce("login:ip:" + ipAddress, rateLimit.getLoginMaxPerIp(), window);
    }

    private void ensureAccountUsable(UserEntity user) {
        if (user.getStatus() == UserStatusEnum.DISABLED) {
            throw new AccountDisabledException("Hisob faolsizlantirilgan");
        }
        if (user.getStatus() == UserStatusEnum.LOCKED) {
            LocalDateTime lockedUntil = user.getLockedUntil();
            if (lockedUntil != null && lockedUntil.isBefore(LocalDateTime.now())) {
                user.setStatus(UserStatusEnum.ACTIVE);
                user.setLockedUntil(null);
                userRepository.save(user);
                return;
            }
            long remainingSeconds = Duration.between(LocalDateTime.now(), lockedUntil).getSeconds();
            long remainingMinutes = Math.max(1, (remainingSeconds + 59) / 60);
            throw new AccountLockedException(
                    "Hisob vaqtincha bloklangan, " + remainingMinutes + " daqiqadan so'ng qayta urinib ko'ring");
        }
    }

    private void registerFailedAttempt(UserEntity user, String ipAddress) {
        int attempts = user.getFailedLoginAttempts() + 1;
        String reason = "Noto'g'ri parol";

        if (attempts >= securityProperties.getLogin().getMaxFailedAttempts()) {
            user.setStatus(UserStatusEnum.LOCKED);
            user.setLockedUntil(LocalDateTime.now().plusMinutes(securityProperties.getLogin().getLockoutMinutes()));
            user.setFailedLoginAttempts(0);
            reason = "Hisob bloklandi: %d marta ketma-ket noto'g'ri parol".formatted(attempts);
        } else {
            user.setFailedLoginAttempts(attempts);
        }

        userRepository.save(user);
        publishLoginFailedEvent(user.getEmail(), ipAddress, reason);
    }

    private void evictOldestSessionIfLimitReached(UserEntity user) {
        long activeSessionCount = sessionRepository.countByUserIdAndStatus(user.getId(), SessionStatusEnum.ACTIVE);
        if (activeSessionCount < securityProperties.getSession().getMaxDevices()) {
            return;
        }

        sessionRepository.findFirstByUserIdAndStatusOrderByLastUsedAtAsc(user.getId(), SessionStatusEnum.ACTIVE)
                .ifPresent(oldest -> {
                    oldest.setStatus(SessionStatusEnum.REVOKED);
                    sessionRepository.save(oldest);
                });
    }

    private SessionEntity createSession(UserEntity user, String rawRefreshToken, String accessTokenJti,
                                         String deviceLabel, String ipAddress) {
        LocalDateTime now = LocalDateTime.now();

        SessionEntity session = SessionEntity.builder()
                .user(user)
                .refreshTokenHash(tokenHasher.hash(rawRefreshToken))
                .accessTokenJti(accessTokenJti)
                .deviceLabel(StringUtils.hasText(deviceLabel) ? deviceLabel : UNKNOWN_DEVICE)
                .ipAddress(ipAddress)
                .status(SessionStatusEnum.ACTIVE)
                .lastUsedAt(now)
                .expiresAt(now.plusDays(securityProperties.getRefreshToken().getTtlDays()))
                .build();

        return sessionRepository.save(session);
    }

    private void publishUserRegisteredEvent(UserEntity user) {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .registeredAt(user.getCreatedAt())
                .build();
        eventPublisher.publish(UserRegisteredEvent.TOPIC, user.getId(), event);
    }

    private void publishLoginSucceededEvent(UserEntity user, SessionEntity session, String ipAddress) {
        LoginSucceededEvent event = LoginSucceededEvent.builder()
                .userId(user.getId())
                .sessionId(session.getId())
                .role(user.getRole())
                .ipAddress(ipAddress)
                .deviceLabel(session.getDeviceLabel())
                .loggedInAt(session.getLastUsedAt())
                .build();
        eventPublisher.publish(LoginSucceededEvent.TOPIC, user.getId(), event);
    }

    private void publishSuspiciousTokenReuseEvent(SessionEntity session, String ipAddress) {
        SuspiciousTokenReuseEvent event = SuspiciousTokenReuseEvent.builder()
                .userId(session.getUser().getId())
                .sessionId(session.getId())
                .ipAddress(ipAddress)
                .detectedAt(LocalDateTime.now())
                .build();
        eventPublisher.publish(SuspiciousTokenReuseEvent.TOPIC, session.getUser().getId(), event);
    }

    private void publishLoginFailedEvent(String email, String ipAddress, String reason) {
        LoginFailedEvent event = LoginFailedEvent.builder()
                .email(email)
                .ipAddress(ipAddress)
                .reason(reason)
                .attemptedAt(LocalDateTime.now())
                .build();
        eventPublisher.publish(LoginFailedEvent.TOPIC, email, event);
    }
}
