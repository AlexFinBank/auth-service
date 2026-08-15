package uz.finbank.finbankauthservice.idempotency;

import uz.finbank.finbankauthservice.dto.response.UserResponse;

record CachedRegistrationResult(Outcome outcome, UserResponse body, String errorMessage) {

    enum Outcome {
        PROCESSING,
        SUCCESS,
        DUPLICATE_RESOURCE
    }

    static CachedRegistrationResult processing() {
        return new CachedRegistrationResult(Outcome.PROCESSING, null, null);
    }

    static CachedRegistrationResult success(UserResponse body) {
        return new CachedRegistrationResult(Outcome.SUCCESS, body, null);
    }

    static CachedRegistrationResult duplicate(String errorMessage) {
        return new CachedRegistrationResult(Outcome.DUPLICATE_RESOURCE, null, errorMessage);
    }
}
