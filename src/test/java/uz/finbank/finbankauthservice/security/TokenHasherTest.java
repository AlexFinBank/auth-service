package uz.finbank.finbankauthservice.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenHasherTest {

    private final TokenHasher tokenHasher = new TokenHasher();

    @Test
    @DisplayName("should produce the same hash for the same raw input across repeated calls")
    void should_produceSameHash_when_hashingSameInputTwice() {
        String raw = "some-raw-refresh-token-value";

        String first = tokenHasher.hash(raw);
        String second = tokenHasher.hash(raw);

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("should produce different hashes for different raw inputs")
    void should_produceDifferentHashes_when_inputsDiffer() {
        String hashA = tokenHasher.hash("token-a");
        String hashB = tokenHasher.hash("token-b");

        assertThat(hashA).isNotEqualTo(hashB);
    }

    @Test
    @DisplayName("should return a 64-character lowercase hex string (SHA-256 digest)")
    void should_returnSixtyFourCharLowercaseHex_when_hashing() {
        String hash = tokenHasher.hash("any-input");

        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]{64}");
    }
}
