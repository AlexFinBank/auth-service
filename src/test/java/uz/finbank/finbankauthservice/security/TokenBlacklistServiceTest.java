package uz.finbank.finbankauthservice.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private TokenBlacklistService service;

    @BeforeEach
    void setUp() {
        // Must be constructed here, not as a field initializer -- @Mock fields aren't injected
        // by MockitoExtension until after instance construction/field initializers have run.
        service = new TokenBlacklistService(redisTemplate);
    }

    @Test
    void blacklist_shouldStoreKeyWithTtl_whenJtiIsPresent() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        service.blacklist("jti-1", Duration.ofMinutes(15));

        verify(valueOperations).set("auth:blacklisted-jti:jti-1", "1", Duration.ofMinutes(15));
    }

    @Test
    void blacklist_shouldDoNothing_whenJtiIsNull() {
        service.blacklist(null, Duration.ofMinutes(15));

        verifyNoRedisWriteAttempted();
    }

    @Test
    void blacklist_shouldNotThrow_whenRedisIsUnavailable() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis connection refused"));

        assertThatCode(() -> service.blacklist("jti-1", Duration.ofMinutes(15))).doesNotThrowAnyException();
    }

    @Test
    void isBlacklisted_shouldReturnTrue_whenKeyExists() {
        when(redisTemplate.hasKey("auth:blacklisted-jti:jti-1")).thenReturn(true);

        assertThat(service.isBlacklisted("jti-1")).isTrue();
    }

    @Test
    void isBlacklisted_shouldReturnFalse_whenKeyDoesNotExist() {
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        assertThat(service.isBlacklisted("jti-1")).isFalse();
    }

    @Test
    void isBlacklisted_shouldFailOpen_whenRedisIsUnavailable() {
        when(redisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("Redis connection refused"));

        assertThat(service.isBlacklisted("jti-1")).isFalse();
    }

    private void verifyNoRedisWriteAttempted() {
        org.mockito.Mockito.verifyNoInteractions(redisTemplate);
    }
}
