package uz.finbank.finbankauthservice.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import uz.finbank.finbankauthservice.exception.TooManyRequestsException;

import java.time.Duration;

/**
 * Fixed-window counter in Redis. Fails open on Redis errors -- a rate limiter that itself takes
 * down /login or /password-reset/request during a Redis outage would defeat its own purpose.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimiterService {

    private static final String KEY_PREFIX = "auth:rate-limit:";

    private final StringRedisTemplate redisTemplate;

    /**
     * Throws TooManyRequestsException once more than maxRequests have been consumed for this key
     * within the current window.
     */
    public void enforce(String key, int maxRequests, Duration window) {
        if (!tryConsume(key, maxRequests, window)) {
            throw new TooManyRequestsException("Juda ko'p so'rov yuborildi, keyinroq urinib ko'ring");
        }
    }

    private boolean tryConsume(String key, int maxRequests, Duration window) {
        String redisKey = KEY_PREFIX + key;
        try {
            Long count = redisTemplate.opsForValue().increment(redisKey);
            if (count != null && count == 1L) {
                redisTemplate.expire(redisKey, window);
            }
            return count == null || count <= maxRequests;
        } catch (Exception ex) {
            log.warn("Rate limiter unavailable (Redis), failing open: key={}", key, ex);
            return true;
        }
    }
}
