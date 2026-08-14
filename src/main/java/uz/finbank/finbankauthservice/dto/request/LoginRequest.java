package uz.finbank.finbankauthservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        String deviceLabel
) {
}
