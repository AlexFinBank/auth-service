package uz.finbank.finbankauthservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record RefreshRequest(
        @NotBlank String refreshToken
) {
}
