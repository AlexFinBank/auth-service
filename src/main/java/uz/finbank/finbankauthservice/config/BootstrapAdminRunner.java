package uz.finbank.finbankauthservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import uz.finbank.finbankauthservice.entity.UserEntity;
import uz.finbank.finbankauthservice.entity.enums.RoleEnum;
import uz.finbank.finbankauthservice.entity.enums.UserStatusEnum;
import uz.finbank.finbankauthservice.repository.UserRepository;

/**
 * Seeds the very first ADMIN account so there is a way into /internal/staff without ever
 * running a manual SQL UPDATE. Only acts when app.security.bootstrap-admin.* (username/email/
 * password, meant to be supplied via env vars) is fully configured AND no ADMIN exists yet --
 * otherwise it's a silent no-op on every startup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BootstrapAdminRunner implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppSecurityProperties securityProperties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppSecurityProperties.BootstrapAdmin config = securityProperties.getBootstrapAdmin();

        if (!StringUtils.hasText(config.getUsername())
                || !StringUtils.hasText(config.getEmail())
                || !StringUtils.hasText(config.getPassword())) {
            return;
        }

        if (userRepository.existsByRole(RoleEnum.ADMIN)) {
            return;
        }

        if (userRepository.existsByEmail(config.getEmail()) || userRepository.existsByUsername(config.getUsername())) {
            log.warn("Bootstrap ADMIN skipped: email/username '{}' already taken by a non-ADMIN account",
                    config.getEmail());
            return;
        }

        UserEntity admin = UserEntity.builder()
                .username(config.getUsername())
                .email(config.getEmail())
                .password(passwordEncoder.encode(config.getPassword()))
                .role(RoleEnum.ADMIN)
                .status(UserStatusEnum.ACTIVE)
                .build();
        userRepository.save(admin);

        log.info("Bootstrap ADMIN account created: {}", config.getEmail());
    }
}
