package uz.finbank.finbankauthservice.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Lets a revoked session's still-unexpired access token be rejected immediately, instead of
 * waiting out its natural (up to 15 min) JWT expiry -- JWTs are otherwise stateless and cannot
 * be invalidated server-side.
 *
 * Fails open on Redis errors: isBlacklisted() reports "not blacklisted" and blacklist() just
 * logs a warning rather than throwing. A Redis outage degrading to "revoked tokens stay valid a
 * little longer" is far preferable to it taking down every authenticated request in the app.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "auth:blacklisted-jti:";

    private final StringRedisTemplate redisTemplate;

    public void blacklist(String jti, Duration ttl) {
        if (!StringUtils.hasText(jti)) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + jti, "1", ttl);
        } catch (Exception ex) {
            log.warn("Could not blacklist access token jti (Redis unavailable): jti={}", jti, ex);
        }
    }

    public boolean isBlacklisted(String jti) {
        if (!StringUtils.hasText(jti)) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
        } catch (Exception ex) {
            log.warn("Could not check access token blacklist (Redis unavailable), failing open: jti={}", jti, ex);
            return false;
        }
    }
}
