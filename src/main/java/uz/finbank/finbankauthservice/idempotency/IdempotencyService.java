package uz.finbank.finbankauthservice.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import uz.finbank.finbankauthservice.dto.response.UserResponse;
import uz.finbank.finbankauthservice.exception.DuplicateResourceException;
import uz.finbank.finbankauthservice.exception.IdempotencyKeyInUseException;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Makes {@code POST /register} safe to retry with the same {@code Idempotency-Key}: a repeated
 * request with a key that already produced an outcome replays that exact outcome (success or
 * duplicate-email conflict) instead of re-running the business logic, so a client retrying after
 * a dropped response can never end up with two different answers for the same attempt.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String KEY_PREFIX = "idempotency:register:";
    // Short: only needs to cover one in-flight request's actual processing time.
    private static final Duration PROCESSING_TTL = Duration.ofSeconds(30);
    // Long: covers realistic client retry windows (retries after a dropped connection, etc).
    private static final Duration COMPLETED_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public UserResponse executeRegisterIdempotently(String idempotencyKey, Supplier<UserResponse> action) {
        String redisKey = KEY_PREFIX + idempotencyKey;

        Boolean reserved;
        try {
            reserved = redisTemplate.opsForValue()
                    .setIfAbsent(redisKey, write(CachedRegistrationResult.processing()), PROCESSING_TTL);
        } catch (Exception ex) {
            log.warn("Idempotency reservation unavailable (Redis), proceeding without a guarantee: key={}",
                    idempotencyKey, ex);
            return action.get();
        }

        if (Boolean.TRUE.equals(reserved)) {
            return runAndCache(redisKey, action);
        }
        return replayOrRunFresh(redisKey, idempotencyKey, action);
    }

    private UserResponse runAndCache(String redisKey, Supplier<UserResponse> action) {
        try {
            UserResponse response = action.get();
            store(redisKey, CachedRegistrationResult.success(response));
            return response;
        } catch (DuplicateResourceException ex) {
            store(redisKey, CachedRegistrationResult.duplicate(ex.getMessage()));
            throw ex;
        } catch (RuntimeException ex) {
            // This outcome can't be faithfully replayed later, so don't leave the key
            // reserved -- a retry with the same Idempotency-Key should be free to try again.
            deleteQuietly(redisKey);
            throw ex;
        }
    }

    private UserResponse replayOrRunFresh(String redisKey, String idempotencyKey, Supplier<UserResponse> action) {
        String raw;
        try {
            raw = redisTemplate.opsForValue().get(redisKey);
        } catch (Exception ex) {
            log.warn("Idempotency replay lookup unavailable (Redis), proceeding without a guarantee: key={}",
                    idempotencyKey, ex);
            return action.get();
        }

        if (raw == null) {
            // The reservation expired between our failed setIfAbsent and this read.
            return runAndCache(redisKey, action);
        }

        CachedRegistrationResult cached = objectMapper.readValue(raw, CachedRegistrationResult.class);
        return switch (cached.outcome()) {
            case PROCESSING -> throw new IdempotencyKeyInUseException(
                    "Bu Idempotency-Key bilan so'rov hozir qayta ishlanmoqda, biroz kutib qayta urinib ko'ring");
            case SUCCESS -> cached.body();
            case DUPLICATE_RESOURCE -> throw new DuplicateResourceException(cached.errorMessage());
        };
    }

    private String write(CachedRegistrationResult result) {
        return objectMapper.writeValueAsString(result);
    }

    private void store(String redisKey, CachedRegistrationResult result) {
        try {
            redisTemplate.opsForValue().set(redisKey, write(result), COMPLETED_TTL);
        } catch (Exception ex) {
            log.warn("Could not persist idempotency result (Redis unavailable): key={}", redisKey, ex);
        }
    }

    private void deleteQuietly(String redisKey) {
        try {
            redisTemplate.delete(redisKey);
        } catch (Exception ex) {
            log.warn("Could not clear idempotency reservation (Redis unavailable): key={}", redisKey, ex);
        }
    }
}
