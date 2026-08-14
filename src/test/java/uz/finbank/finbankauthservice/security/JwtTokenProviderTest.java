package uz.finbank.finbankauthservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.finbank.finbankauthservice.config.AppSecurityProperties;
import uz.finbank.finbankauthservice.entity.UserEntity;
import uz.finbank.finbankauthservice.entity.enums.RoleEnum;
import uz.finbank.finbankauthservice.entity.enums.UserStatusEnum;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static final String SECRET = "test-signing-key-must-be-at-least-32-bytes-long!!";

    private JwtTokenProvider jwtTokenProvider;
    private AppSecurityProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AppSecurityProperties();
        properties.getJwt().setSecret(SECRET);
        properties.getJwt().setAccessTokenTtlMinutes(15);

        jwtTokenProvider = new JwtTokenProvider(properties);
        jwtTokenProvider.init();
    }

    private UserEntity buildUser() {
        UserEntity user = UserEntity.builder()
                .username("alice")
                .email("alice@finbank.uz")
                .password("hashed-password")
                .role(RoleEnum.CUSTOMER)
                .status(UserStatusEnum.ACTIVE)
                .build();
        user.setId("user-123");
        return user;
    }

    @Test
    @DisplayName("should embed subject/email/role claims when generating an access token")
    void should_embedUserClaims_when_generatingAccessToken() {
        UserEntity user = buildUser();

        String token = jwtTokenProvider.generateAccessToken(user);

        Claims claims = jwtTokenProvider.parseClaims(token).orElseThrow();
        assertThat(claims.getSubject()).isEqualTo("user-123");
        assertThat(claims.get("email", String.class)).isEqualTo("alice@finbank.uz");
        assertThat(claims.get("role", String.class)).isEqualTo("CUSTOMER");
    }

    @Test
    @DisplayName("should set expiration roughly 15 minutes after issuedAt when generating an access token")
    void should_setFifteenMinuteExpiry_when_generatingAccessToken() {
        UserEntity user = buildUser();

        String token = jwtTokenProvider.generateAccessToken(user);

        Claims claims = jwtTokenProvider.parseClaims(token).orElseThrow();
        Duration ttl = Duration.between(claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant());
        assertThat(ttl).isCloseTo(Duration.ofMinutes(15), Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("should return empty when parsing garbage input")
    void should_returnEmpty_when_parsingGarbageToken() {
        Optional<Claims> claims = jwtTokenProvider.parseClaims("this-is-not-a-jwt");

        assertThat(claims).isEmpty();
    }

    @Test
    @DisplayName("should return empty when the token signature does not match the signing key")
    void should_returnEmpty_when_tokenSignatureIsTampered() {
        SecretKey differentKey = Keys.hmacShaKeyFor("a-completely-different-signing-key-32bytes!".getBytes(StandardCharsets.UTF_8));
        String tokenSignedWithDifferentKey = Jwts.builder()
                .subject("user-123")
                .claim("email", "alice@finbank.uz")
                .claim("role", "CUSTOMER")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(900)))
                .signWith(differentKey)
                .compact();

        Optional<Claims> claims = jwtTokenProvider.parseClaims(tokenSignedWithDifferentKey);

        assertThat(claims).isEmpty();
    }

    @Test
    @DisplayName("should return empty when the token is already expired")
    void should_returnEmpty_when_tokenIsExpired() {
        SecretKey sameKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String expiredToken = Jwts.builder()
                .subject("user-123")
                .claim("email", "alice@finbank.uz")
                .claim("role", "CUSTOMER")
                .issuedAt(Date.from(Instant.now().minusSeconds(3600)))
                .expiration(Date.from(Instant.now().minusSeconds(1800)))
                .signWith(sameKey)
                .compact();

        Optional<Claims> claims = jwtTokenProvider.parseClaims(expiredToken);

        assertThat(claims).isEmpty();
    }
}
