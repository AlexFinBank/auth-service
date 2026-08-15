package uz.finbank.finbankauthservice.exception;

public class IdempotencyKeyInUseException extends RuntimeException {

    public IdempotencyKeyInUseException(String message) {
        super(message);
    }
}
