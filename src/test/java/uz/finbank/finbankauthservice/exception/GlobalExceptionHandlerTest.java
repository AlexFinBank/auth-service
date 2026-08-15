package uz.finbank.finbankauthservice.exception;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import uz.finbank.finbankauthservice.dto.response.ErrorResponse;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleDuplicateResource_shouldReturn409() {
        ResponseEntity<ErrorResponse> response =
                handler.handleDuplicateResource(new DuplicateResourceException("email band"));

        assertStatusAndMessage(response, HttpStatus.CONFLICT, "email band");
    }

    @Test
    void handleInvalidCredentials_shouldReturn401() {
        ResponseEntity<ErrorResponse> response =
                handler.handleInvalidCredentials(new InvalidCredentialsException("email yoki parol noto'g'ri"));

        assertStatusAndMessage(response, HttpStatus.UNAUTHORIZED, "email yoki parol noto'g'ri");
    }

    @Test
    void handleAccountLocked_shouldReturn423() {
        ResponseEntity<ErrorResponse> response =
                handler.handleAccountLocked(new AccountLockedException("hisob bloklangan"));

        assertStatusAndMessage(response, HttpStatus.LOCKED, "hisob bloklangan");
    }

    @Test
    void handleAccountDisabled_shouldReturn403() {
        ResponseEntity<ErrorResponse> response =
                handler.handleAccountDisabled(new AccountDisabledException("hisob faolsizlantirilgan"));

        assertStatusAndMessage(response, HttpStatus.FORBIDDEN, "hisob faolsizlantirilgan");
    }

    @Test
    void handleInvalidRefreshToken_shouldReturn401() {
        ResponseEntity<ErrorResponse> response =
                handler.handleInvalidRefreshToken(new InvalidRefreshTokenException("token noto'g'ri"));

        assertStatusAndMessage(response, HttpStatus.UNAUTHORIZED, "token noto'g'ri");
    }

    @Test
    void handleResourceNotFound_shouldReturn404() {
        ResponseEntity<ErrorResponse> response =
                handler.handleResourceNotFound(new ResourceNotFoundException("session topilmadi"));

        assertStatusAndMessage(response, HttpStatus.NOT_FOUND, "session topilmadi");
    }

    @Test
    void handleInvalidResetToken_shouldReturn400() {
        ResponseEntity<ErrorResponse> response =
                handler.handleInvalidResetToken(new InvalidResetTokenException("token muddati tugagan"));

        assertStatusAndMessage(response, HttpStatus.BAD_REQUEST, "token muddati tugagan");
    }

    @Test
    void handleInvalidRequest_shouldReturn400() {
        ResponseEntity<ErrorResponse> response =
                handler.handleInvalidRequest(new InvalidRequestException("noto'g'ri so'rov"));

        assertStatusAndMessage(response, HttpStatus.BAD_REQUEST, "noto'g'ri so'rov");
    }

    @Test
    void handleTooManyRequests_shouldReturn429() {
        ResponseEntity<ErrorResponse> response =
                handler.handleTooManyRequests(new TooManyRequestsException("juda ko'p so'rov"));

        assertStatusAndMessage(response, HttpStatus.TOO_MANY_REQUESTS, "juda ko'p so'rov");
    }

    @Test
    void handleValidation_shouldReturn400WithFieldErrors() throws NoSuchMethodException {
        MethodParameter methodParameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyTarget", String.class), 0);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new DummyPayload(), "dummyPayload");
        bindingResult.rejectValue("email", "NotBlank", "email bo'sh bo'lmasligi kerak");
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(response.getBody().timestamp()).isNotNull();
        assertThat(response.getBody().fieldErrors())
                .containsEntry("email", "email bo'sh bo'lmasligi kerak");
    }

    @SuppressWarnings("unused")
    private void dummyTarget(String email) {
        // used only to obtain a MethodParameter for the MethodArgumentNotValidException test above
    }

    @SuppressWarnings("unused")
    private static class DummyPayload {
        private String email;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    private void assertStatusAndMessage(ResponseEntity<ErrorResponse> response, HttpStatus expectedStatus, String expectedMessage) {
        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(expectedStatus.value());
        assertThat(response.getBody().message()).isEqualTo(expectedMessage);
        assertThat(response.getBody().timestamp()).isNotNull();
    }
}
