package uz.finbank.finbankauthservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.security")
public class AppSecurityProperties {

    private Jwt jwt = new Jwt();
    private RefreshToken refreshToken = new RefreshToken();
    private Session session = new Session();
    private Login login = new Login();
    private PasswordReset passwordReset = new PasswordReset();

    @Data
    public static class Jwt {
        private String secret;
        private long accessTokenTtlMinutes = 15;
    }

    @Data
    public static class RefreshToken {
        private long ttlDays = 7;
    }

    @Data
    public static class Session {
        private int maxDevices = 5;
    }

    @Data
    public static class Login {
        private int maxFailedAttempts = 5;
        private long lockoutMinutes = 30;
    }

    @Data
    public static class PasswordReset {
        private long ttlMinutes = 15;
    }
}
