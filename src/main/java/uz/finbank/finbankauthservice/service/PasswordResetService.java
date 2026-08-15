package uz.finbank.finbankauthservice.service;

public interface PasswordResetService {

    void requestReset(String email, String ipAddress);

    void confirmReset(String rawToken, String newPassword);
}
