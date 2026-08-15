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
import uz.finbank.finbankauthservice.dto.request.PasswordResetConfirmRequest;
import uz.finbank.finbankauthservice.dto.request.PasswordResetRequest;
import uz.finbank.finbankauthservice.security.JwtTokenProvider;
import uz.finbank.finbankauthservice.security.TokenBlacklistService;
import uz.finbank.finbankauthservice.service.PasswordResetService;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PasswordResetController.class, excludeAutoConfiguration = DataWebAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
class PasswordResetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @Test
    void should_return202AndDelegateWithEmail_when_requestBodyIsValid() throws Exception {
        PasswordResetRequest request = PasswordResetRequest.builder().email("john@example.com").build();

        mockMvc.perform(post("/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        verify(passwordResetService).requestReset(eq("john@example.com"), anyString());
    }

    @Test
    void should_return400AndSkipService_when_requestBodyHasMalformedEmail() throws Exception {
        PasswordResetRequest invalid = PasswordResetRequest.builder().email("not-an-email").build();

        mockMvc.perform(post("/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(passwordResetService);
    }

    @Test
    void should_return204AndDelegateWithTokenAndPassword_when_confirmBodyIsValid() throws Exception {
        PasswordResetConfirmRequest request = PasswordResetConfirmRequest.builder()
                .token("raw-reset-token")
                .newPassword("newPassword123")
                .build();

        mockMvc.perform(post("/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(passwordResetService).confirmReset("raw-reset-token", "newPassword123");
    }

    @Test
    void should_return400AndSkipService_when_confirmBodyHasTooShortPassword() throws Exception {
        PasswordResetConfirmRequest invalid = PasswordResetConfirmRequest.builder()
                .token("raw-reset-token")
                .newPassword("abc")
                .build();

        mockMvc.perform(post("/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(passwordResetService);
    }
}
