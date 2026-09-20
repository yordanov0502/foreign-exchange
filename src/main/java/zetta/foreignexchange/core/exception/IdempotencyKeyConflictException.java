package zetta.foreignexchange.core.exception;

import lombok.Getter;

/**
 * The same {@code (clientId, idempotencyKey)} pair was reused for a conversion request whose currencies or
 * amount do not match the conversion originally persisted under that key.
 */
@Getter
public class IdempotencyKeyConflictException extends RuntimeException {

    private final String clientId;
    private final String idempotencyKey;

    public IdempotencyKeyConflictException(String clientId, String idempotencyKey) {
        this.clientId = clientId;
        this.idempotencyKey = idempotencyKey;
    }
}
