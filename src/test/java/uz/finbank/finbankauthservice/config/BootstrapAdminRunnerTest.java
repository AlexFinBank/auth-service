package uz.finbank.finbankauthservice.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import uz.finbank.finbankauthservice.entity.UserEntity;
import uz.finbank.finbankauthservice.entity.enums.RoleEnum;
import uz.finbank.finbankauthservice.entity.enums.UserStatusEnum;
import uz.finbank.finbankauthservice.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BootstrapAdminRunnerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ApplicationArguments applicationArguments;

    private AppSecurityProperties properties;

    private BootstrapAdminRunner newRunner() {
        return new BootstrapAdminRunner(userRepository, passwordEncoder, properties);
    }

    @Test
    void run_shouldDoNothing_whenBootstrapAdminNotConfigured() {
        properties = new AppSecurityProperties();

        newRunner().run(applicationArguments);

        verify(userRepository, never()).save(any());
    }

    @Test
    void run_shouldDoNothing_whenPartiallyConfigured() {
        properties = new AppSecurityProperties();
        properties.getBootstrapAdmin().setUsername("admin");
        properties.getBootstrapAdmin().setEmail("admin@finbank.uz");
        // password intentionally left unset

        newRunner().run(applicationArguments);

        verify(userRepository, never()).save(any());
    }

    @Test
    void run_shouldDoNothing_whenAnAdminAlreadyExists() {
        properties = fullyConfiguredBootstrapProperties();
        when(userRepository.existsByRole(RoleEnum.ADMIN)).thenReturn(true);

        newRunner().run(applicationArguments);

        verify(userRepository, never()).save(any());
    }

    @Test
    void run_shouldSkip_whenEmailOrUsernameAlreadyTakenByNonAdmin() {
        properties = fullyConfiguredBootstrapProperties();
        when(userRepository.existsByRole(RoleEnum.ADMIN)).thenReturn(false);
        when(userRepository.existsByEmail("admin@finbank.uz")).thenReturn(true);

        newRunner().run(applicationArguments);

        verify(userRepository, never()).save(any());
    }

    @Test
    void run_shouldCreateAdmin_whenFullyConfiguredAndNoAdminExists() {
        properties = fullyConfiguredBootstrapProperties();
        when(userRepository.existsByRole(RoleEnum.ADMIN)).thenReturn(false);
        when(userRepository.existsByEmail("admin@finbank.uz")).thenReturn(false);
        when(userRepository.existsByUsername("admin")).thenReturn(false);
        when(passwordEncoder.encode("BootstrapPass123")).thenReturn("encoded-password");

        newRunner().run(applicationArguments);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        UserEntity saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("admin");
        assertThat(saved.getEmail()).isEqualTo("admin@finbank.uz");
        assertThat(saved.getPassword()).isEqualTo("encoded-password");
        assertThat(saved.getRole()).isEqualTo(RoleEnum.ADMIN);
        assertThat(saved.getStatus()).isEqualTo(UserStatusEnum.ACTIVE);
    }

    private AppSecurityProperties fullyConfiguredBootstrapProperties() {
        AppSecurityProperties props = new AppSecurityProperties();
        props.getBootstrapAdmin().setUsername("admin");
        props.getBootstrapAdmin().setEmail("admin@finbank.uz");
        props.getBootstrapAdmin().setPassword("BootstrapPass123");
        return props;
    }
}
