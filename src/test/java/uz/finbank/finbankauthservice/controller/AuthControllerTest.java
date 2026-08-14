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
import uz.finbank.finbankauthservice.dto.request.LoginRequest;
import uz.finbank.finbankauthservice.dto.request.RefreshRequest;
import uz.finbank.finbankauthservice.dto.request.RegisterRequest;
import uz.finbank.finbankauthservice.dto.response.LoginResponse;
import uz.finbank.finbankauthservice.dto.response.UserResponse;
import uz.finbank.finbankauthservice.entity.enums.RoleEnum;
import uz.finbank.finbankauthservice.entity.enums.UserStatusEnum;
import uz.finbank.finbankauthservice.security.JwtTokenProvider;
import uz.finbank.finbankauthservice.service.AuthService;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class, excludeAutoConfiguration = DataWebAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    // JwtAuthenticationFilter is a Filter bean, so @WebMvcTest auto-detects and constructs it
    // even with addFilters=false; it needs a JwtTokenProvider collaborator to satisfy that.
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void should_return201WithUserBody_when_registerRequestIsValid() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("johndoe")
                .email("john@example.com")
                .password("password123")
                .build();
        UserResponse response = UserResponse.builder()
                .id("user-1")
                .username("johndoe")
                .email("john@example.com")
                .role(RoleEnum.CUSTOMER)
                .status(UserStatusEnum.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("user-1"))
                .andExpect(jsonPath("$.username").value("johndoe"))
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void should_return400AndSkipService_when_registerRequestHasBlankFields() throws Exception {
        RegisterRequest invalid = RegisterRequest.builder()
                .username("")
                .email("not-an-email")
                .password("short")
                .build();

        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void should_return200AndPassRemoteAddress_when_loginRequestIsValid() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("john@example.com")
                .password("password123")
                .deviceLabel("iPhone")
                .build();
        LoginResponse response = LoginResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .tokenType("Bearer")
                .expiresInSeconds(900)
                .build();
        when(authService.login(any(LoginRequest.class), eq("127.0.0.1"))).thenReturn(response);

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(900));

        verify(authService).login(any(LoginRequest.class), eq("127.0.0.1"));
    }

    @Test
    void should_return400AndSkipService_when_loginRequestHasBlankPassword() throws Exception {
        LoginRequest invalid = LoginRequest.builder()
                .email("john@example.com")
                .password("")
                .build();

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void should_return200_when_refreshRequestIsValid() throws Exception {
        RefreshRequest request = RefreshRequest.builder().refreshToken("some-refresh-token").build();
        LoginResponse response = LoginResponse.builder()
                .accessToken("new-access-token")
                .refreshToken("new-refresh-token")
                .tokenType("Bearer")
                .expiresInSeconds(900)
                .build();
        when(authService.refresh(any(RefreshRequest.class), eq("127.0.0.1"))).thenReturn(response);

        mockMvc.perform(post("/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"));
    }

    @Test
    void should_return400AndSkipService_when_refreshRequestHasBlankToken() throws Exception {
        RefreshRequest invalid = RefreshRequest.builder().refreshToken("").build();

        mockMvc.perform(post("/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }
}
