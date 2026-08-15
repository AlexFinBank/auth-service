package uz.finbank.finbankauthservice.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;
import uz.finbank.finbankauthservice.dto.response.UserResponse;
import uz.finbank.finbankauthservice.entity.enums.RoleEnum;
import uz.finbank.finbankauthservice.entity.enums.UserStatusEnum;
import uz.finbank.finbankauthservice.exception.DuplicateResourceException;
import uz.finbank.finbankauthservice.exception.IdempotencyKeyInUseException;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        // Constructed here, not as a field initializer -- @Mock fields aren't injected by
        // MockitoExtension until after instance construction/field initializers have run.
        service = new IdempotencyService(redisTemplate, new ObjectMapper());
    }

    private UserResponse sampleResponse() {
        return UserResponse.builder()
                .id("user-1").username("john").email("john@test.local")
                .role(RoleEnum.CUSTOMER).status(UserStatusEnum.ACTIVE).build();
    }

    @Test
    void should_runActionAndCacheSuccess_when_keyIsSeenForTheFirstTime() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        UserResponse expected = sampleResponse();

        UserResponse result = service.executeRegisterIdempotently("key-1", () -> expected);

        assertThat(result).isEqualTo(expected);
        verify(valueOperations).set(eq("idempotency:register:key-1"), anyString(), eq(Duration.ofHours(24)));
    }

    @Test
    void should_notRunActionAgain_when_keyAlreadyHasACachedSuccessResult() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        when(valueOperations.get("idempotency:register:key-1"))
                .thenReturn("{\"outcome\":\"SUCCESS\",\"body\":{\"id\":\"user-1\",\"username\":\"john\","
                        + "\"email\":\"john@test.local\",\"role\":\"CUSTOMER\",\"status\":\"ACTIVE\"},\"errorMessage\":null}");
        AtomicInteger invocationCount = new AtomicInteger();

        UserResponse result = service.executeRegisterIdempotently("key-1", () -> {
            invocationCount.incrementAndGet();
            return sampleResponse();
        });

        assertThat(result).isEqualTo(sampleResponse());
        assertThat(invocationCount.get()).isZero();
    }

    @Test
    void should_rethrowCachedDuplicateResourceException_when_keyAlreadyFailedWithThatOutcome() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        when(valueOperations.get("idempotency:register:key-1"))
                .thenReturn("{\"outcome\":\"DUPLICATE_RESOURCE\",\"body\":null,"
                        + "\"errorMessage\":\"Email allaqachon ro'yxatdan o'tgan\"}");

        assertThatThrownBy(() -> service.executeRegisterIdempotently("key-1", this::failIfCalled))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Email allaqachon ro'yxatdan o'tgan");
    }

    @Test
    void should_throwIdempotencyKeyInUse_when_anotherRequestWithSameKeyIsStillProcessing() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        when(valueOperations.get("idempotency:register:key-1"))
                .thenReturn("{\"outcome\":\"PROCESSING\",\"body\":null,\"errorMessage\":null}");

        assertThatThrownBy(() -> service.executeRegisterIdempotently("key-1", this::failIfCalled))
                .isInstanceOf(IdempotencyKeyInUseException.class);
    }

    @Test
    void should_cacheDuplicateResourceOutcome_when_actionThrowsIt() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        assertThatThrownBy(() -> service.executeRegisterIdempotently("key-1", () -> {
            throw new DuplicateResourceException("Email allaqachon ro'yxatdan o'tgan");
        })).isInstanceOf(DuplicateResourceException.class);

        verify(valueOperations).set(eq("idempotency:register:key-1"), anyString(), eq(Duration.ofHours(24)));
    }

    @Test
    void should_clearReservation_when_actionThrowsAnUnexpectedRuntimeException() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        assertThatThrownBy(() -> service.executeRegisterIdempotently("key-1", () -> {
            throw new IllegalStateException("kutilmagan xato");
        })).isInstanceOf(IllegalStateException.class);

        verify(redisTemplate).delete("idempotency:register:key-1");
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void should_runActionDirectly_when_redisIsUnavailableDuringReservation() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis connection refused"));
        UserResponse expected = sampleResponse();

        UserResponse result = service.executeRegisterIdempotently("key-1", () -> expected);

        assertThat(result).isEqualTo(expected);
    }

    private UserResponse failIfCalled() {
        throw new AssertionError("action should not have been invoked");
    }
}
