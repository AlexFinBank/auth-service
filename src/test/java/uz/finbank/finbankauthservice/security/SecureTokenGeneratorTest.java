package uz.finbank.finbankauthservice.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecureTokenGeneratorTest {

    private final SecureTokenGenerator secureTokenGenerator = new SecureTokenGenerator();

    @Test
    @DisplayName("should return a non-blank token when generating")
    void should_returnNonBlankToken_when_generating() {
        String token = secureTokenGenerator.generate();

        assertThat(token).isNotNull();
        assertThat(token).isNotBlank();
        assertThat(token.length()).isGreaterThan(40);
    }

    @Test
    @DisplayName("should return a different token on each call (uniqueness)")
    void should_returnDifferentTokens_when_generatingTwice() {
        String first = secureTokenGenerator.generate();
        String second = secureTokenGenerator.generate();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("should use URL-safe, unpadded Base64 characters only")
    void should_useUrlSafeUnpaddedAlphabet_when_generating() {
        String token = secureTokenGenerator.generate();

        assertThat(token).doesNotContain("+", "/", "=");
    }
}
