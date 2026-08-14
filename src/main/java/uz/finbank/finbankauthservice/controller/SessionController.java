package uz.finbank.finbankauthservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.finbank.finbankauthservice.dto.request.RefreshRequest;
import uz.finbank.finbankauthservice.dto.response.SessionResponse;
import uz.finbank.finbankauthservice.service.SessionService;

import java.util.List;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @GetMapping("/sessions")
    public ResponseEntity<List<SessionResponse>> getSessions(Authentication authentication) {
        return ResponseEntity.ok(sessionService.getActiveSessions(authentication.getName()));
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> revokeSession(@PathVariable String id, Authentication authentication) {
        sessionService.revokeSession(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request, Authentication authentication) {
        sessionService.logout(authentication.getName(), request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(Authentication authentication) {
        sessionService.logoutAll(authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
