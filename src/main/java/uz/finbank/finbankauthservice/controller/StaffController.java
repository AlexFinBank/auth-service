package uz.finbank.finbankauthservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.finbank.finbankauthservice.dto.request.CreateStaffRequest;
import uz.finbank.finbankauthservice.dto.response.UserResponse;
import uz.finbank.finbankauthservice.service.AuthService;

@RestController
@RequestMapping("/internal/staff")
@RequiredArgsConstructor
public class StaffController {

    private final AuthService authService;

    @PostMapping
    public ResponseEntity<UserResponse> createStaff(@Valid @RequestBody CreateStaffRequest request) {
        UserResponse response = authService.createStaff(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
