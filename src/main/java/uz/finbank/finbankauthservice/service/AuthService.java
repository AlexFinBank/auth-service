package uz.finbank.finbankauthservice.service;

import uz.finbank.finbankauthservice.dto.request.CreateStaffRequest;
import uz.finbank.finbankauthservice.dto.request.LoginRequest;
import uz.finbank.finbankauthservice.dto.request.RefreshRequest;
import uz.finbank.finbankauthservice.dto.request.RegisterRequest;
import uz.finbank.finbankauthservice.dto.response.LoginResponse;
import uz.finbank.finbankauthservice.dto.response.UserResponse;

public interface AuthService {

    UserResponse register(RegisterRequest request);

    UserResponse createStaff(CreateStaffRequest request);

    LoginResponse login(LoginRequest request, String ipAddress);

    LoginResponse refresh(RefreshRequest request, String ipAddress);
}
