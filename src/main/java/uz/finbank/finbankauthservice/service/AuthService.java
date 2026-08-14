package uz.finbank.finbankauthservice.service;

import uz.finbank.finbankauthservice.dto.request.RegisterRequest;
import uz.finbank.finbankauthservice.dto.response.UserResponse;

public interface AuthService {

    UserResponse register(RegisterRequest request);
}
