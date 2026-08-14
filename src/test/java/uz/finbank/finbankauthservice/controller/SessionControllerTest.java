package uz.finbank.finbankauthservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.autoconfigure.web.DataWebAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import uz.finbank.finbankauthservice.dto.request.RefreshRequest;
import uz.finbank.finbankauthservice.dto.response.SessionResponse;
import uz.finbank.finbankauthservice.security.JwtTokenProvider;
import uz.finbank.finbankauthservice.service.SessionService;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SessionController.class, excludeAutoConfiguration = DataWebAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
class SessionControllerTest {

    private static final String USER_ID = "user-123";
    private static final TestingAuthenticationToken PRINCIPAL =
            new TestingAuthenticationToken(USER_ID, null, "ROLE_CUSTOMER");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SessionService sessionService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void should_return200WithSessionList_when_getSessionsCalledByAuthenticatedUser() throws Exception {
        SessionResponse session = SessionResponse.builder()
                .id("session-1")
                .deviceLabel("iPhone")
                .ipAddress("1.2.3.4")
                .lastUsedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        when(sessionService.getActiveSessions(USER_ID)).thenReturn(List.of(session));

        mockMvc.perform(get("/sessions").principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("session-1"))
                .andExpect(jsonPath("$[0].deviceLabel").value("iPhone"));

        verify(sessionService).getActiveSessions(USER_ID);
    }

    @Test
    void should_return204AndDelegateWithUserAndId_when_revokeSessionCalled() throws Exception {
        mockMvc.perform(delete("/sessions/{id}", "session-1").principal(PRINCIPAL))
                .andExpect(status().isNoContent());

        verify(sessionService).revokeSession(USER_ID, "session-1");
    }

    @Test
    void should_return204AndDelegateWithUserAndToken_when_logoutRequestIsValid() throws Exception {
        RefreshRequest request = RefreshRequest.builder().refreshToken("raw-refresh-token").build();

        mockMvc.perform(post("/logout")
                        .principal(PRINCIPAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(sessionService).logout(eq(USER_ID), eq("raw-refresh-token"));
    }

    @Test
    void should_return400_when_logoutRequestHasBlankRefreshToken() throws Exception {
        RefreshRequest invalid = RefreshRequest.builder().refreshToken("").build();

        mockMvc.perform(post("/logout")
                        .principal(PRINCIPAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_return204AndDelegateWithUser_when_logoutAllCalled() throws Exception {
        mockMvc.perform(post("/logout-all").principal(PRINCIPAL))
                .andExpect(status().isNoContent());

        verify(sessionService).logoutAll(USER_ID);
    }
}
