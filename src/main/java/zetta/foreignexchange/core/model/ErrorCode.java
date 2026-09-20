package zetta.foreignexchange.core.model;

/**
 * One constant per error code emitted by the API's {@code @RestControllerAdvice} handlers. The HTTP status
 * for each code lives in the advice, not here, so this enum never depends on a {@code rest}-layer type.
 */
public enum ErrorCode {
    CLIENT_NOT_FOUND,
    BALANCE_NOT_FOUND,
    INSUFFICIENT_FUNDS,
    SAME_CURRENCY,
    UNSUPPORTED_CURRENCY_PAIR,
    IDEMPOTENCY_KEY_CONFLICT,
    EXCHANGE_RATE_UNAVAILABLE,
    VALIDATION_FAILED,
    FIELD_ERROR,
    MALFORMED_REQUEST,
    INTERNAL_SERVER_ERROR
}
