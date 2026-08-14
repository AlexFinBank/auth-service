package uz.finbank.finbankauthservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.finbank.finbankauthservice.dto.request.RegisterRequest;
import uz.finbank.finbankauthservice.dto.response.UserResponse;
import uz.finbank.finbankauthservice.entity.UserEntity;
import uz.finbank.finbankauthservice.entity.enums.RoleEnum;
import uz.finbank.finbankauthservice.entity.enums.UserStatusEnum;
import uz.finbank.finbankauthservice.event.UserRegisteredEvent;
import uz.finbank.finbankauthservice.exception.DuplicateResourceException;
import uz.finbank.finbankauthservice.mapper.UserMapper;
import uz.finbank.finbankauthservice.repository.UserRepository;
import uz.finbank.finbankauthservice.service.AuthService;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final KafkaTemplate<String, UserRegisteredEvent> kafkaTemplate;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email allaqachon ro'yxatdan o'tgan: " + request.getEmail());
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username band: " + request.getUsername());
        }

        UserEntity user = UserEntity.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(RoleEnum.CUSTOMER)
                .status(UserStatusEnum.ACTIVE)
                .build();

        UserEntity savedUser = userRepository.save(user);

        publishUserRegisteredEvent(savedUser);

        return userMapper.toResponse(savedUser);
    }

    private void publishUserRegisteredEvent(UserEntity user) {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .registeredAt(user.getCreatedAt())
                .build();
        kafkaTemplate.send(UserRegisteredEvent.TOPIC, user.getId(), event);
    }
}
