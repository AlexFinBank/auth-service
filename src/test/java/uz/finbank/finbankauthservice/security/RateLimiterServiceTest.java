package uz.finbank.finbankauthservice.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import uz.finbank.finbankauthservice.exception.TooManyRequestsException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private RateLimiterService rateLimiterService;

    @Test
    void enforce_shouldAllow_whenUnderTheLimit() {
        rateLimiterService = new RateLimiterService(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(3L);

        assertThatCode(() -> rateLimiterService.enforce("key", 10, Duration.ofMinutes(5)))
                .doesNotThrowAnyException();
    }

    @Test
    void enforce_shouldSetExpiry_onlyOnTheFirstRequestInTheWindow() {
        rateLimiterService = new RateLimiterService(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("auth:rate-limit:key")).thenReturn(1L);

        rateLimiterService.enforce("key", 10, Duration.ofMinutes(5));

        verify(redisTemplate).expire("auth:rate-limit:key", Duration.ofMinutes(5));
    }

    @Test
    void enforce_shouldNotResetExpiry_onSubsequentRequestsInTheSameWindow() {
        rateLimiterService = new RateLimiterService(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(2L);

        rateLimiterService.enforce("key", 10, Duration.ofMinutes(5));

        verify(redisTemplate, never()).expire(anyString(), org.mockito.ArgumentMatchers.any(Duration.class));
    }

    @Test
    void enforce_shouldThrowTooManyRequests_whenCountExceedsTheLimit() {
        rateLimiterService = new RateLimiterService(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(11L);

        assertThatThrownBy(() -> rateLimiterService.enforce("key", 10, Duration.ofMinutes(5)))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void enforce_shouldFailOpen_whenRedisIsUnavailable() {
        rateLimiterService = new RateLimiterService(redisTemplate);
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis connection refused"));

        assertThatCode(() -> rateLimiterService.enforce("key", 10, Duration.ofMinutes(5)))
                .doesNotThrowAnyException();
    }
}
