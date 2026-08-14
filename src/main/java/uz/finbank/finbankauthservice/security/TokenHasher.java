package uz.finbank.finbankauthservice.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

// SHA-256, Argon2id emas: hash bo'yicha to'g'ridan-to'g'ri WHERE qidiruv kerak, Argon2 esa har safar boshqa salt bilan boshqa natija beradi.
@Component
public class TokenHasher {

    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algoritmi mavjud emas", e);
        }
    }
}
