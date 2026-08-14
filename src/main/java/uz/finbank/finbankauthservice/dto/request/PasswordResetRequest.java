package uz.finbank.finbankauthservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record PasswordResetRequest(
        @NotBlank @Email String email
) {
}
