package uz.finbank.finbankauthservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.autoconfigure.web.DataWebAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import uz.finbank.finbankauthservice.dto.request.CreateStaffRequest;
import uz.finbank.finbankauthservice.dto.response.UserResponse;
import uz.finbank.finbankauthservice.entity.enums.RoleEnum;
import uz.finbank.finbankauthservice.entity.enums.UserStatusEnum;
import uz.finbank.finbankauthservice.exception.DuplicateResourceException;
import uz.finbank.finbankauthservice.security.JwtTokenProvider;
import uz.finbank.finbankauthservice.service.AuthService;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// NOTE: role-based authorization (only real ADMINs may call this endpoint) is intentionally
// NOT tested here since @AutoConfigureMockMvc(addFilters = false) bypasses the whole security
// filter chain (hasRole("ADMIN") in SecurityConfig). That boundary is covered separately by a
// dedicated full-stack security integration test elsewhere in this test suite.
@WebMvcTest(controllers = StaffController.class, excludeAutoConfiguration = DataWebAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
class StaffControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void should_return201WithUserBody_when_createStaffRequestIsValid() throws Exception {
        CreateStaffRequest request = CreateStaffRequest.builder()
                .username("opuser")
                .email("op@example.com")
                .password("password123")
                .role(RoleEnum.ADMIN)
                .build();
        UserResponse response = UserResponse.builder()
                .id("staff-1")
                .username("opuser")
                .email("op@example.com")
                .role(RoleEnum.ADMIN)
                .status(UserStatusEnum.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
        when(authService.createStaff(any(CreateStaffRequest.class))).thenReturn(response);

        mockMvc.perform(post("/internal/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("staff-1"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void should_return400_when_createStaffRequestHasNullRole() throws Exception {
        Map<String, Object> invalidBody = Map.of(
                "username", "opuser",
                "email", "op@example.com",
                "password", "password123"
        );

        mockMvc.perform(post("/internal/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidBody)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_return400_when_createStaffRequestHasBlankUsername() throws Exception {
        CreateStaffRequest request = CreateStaffRequest.builder()
                .username("")
                .email("op@example.com")
                .password("password123")
                .role(RoleEnum.OPERATOR)
                .build();

        mockMvc.perform(post("/internal/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_return400_when_createStaffRequestHasMalformedEmail() throws Exception {
        CreateStaffRequest request = CreateStaffRequest.builder()
                .username("opuser")
                .email("not-an-email")
                .password("password123")
                .role(RoleEnum.AUDITOR)
                .build();

        mockMvc.perform(post("/internal/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_return400_when_createStaffRequestHasTooShortPassword() throws Exception {
        CreateStaffRequest request = CreateStaffRequest.builder()
                .username("opuser")
                .email("op@example.com")
                .password("short")
                .role(RoleEnum.OPERATOR)
                .build();

        mockMvc.perform(post("/internal/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_return409_when_authServiceReportsDuplicateResource() throws Exception {
        CreateStaffRequest request = CreateStaffRequest.builder()
                .username("opuser")
                .email("op@example.com")
                .password("password123")
                .role(RoleEnum.OPERATOR)
                .build();
        when(authService.createStaff(any(CreateStaffRequest.class)))
                .thenThrow(new DuplicateResourceException("Email allaqachon ro'yxatdan o'tgan: op@example.com"));

        mockMvc.perform(post("/internal/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }
}
