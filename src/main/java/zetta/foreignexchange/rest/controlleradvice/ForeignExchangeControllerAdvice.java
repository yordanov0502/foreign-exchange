package zetta.foreignexchange.rest.controlleradvice;

import static java.lang.String.format;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import zetta.foreignexchange.core.exception.BalanceNotFoundException;
import zetta.foreignexchange.core.exception.ClientNotFoundException;
import zetta.foreignexchange.core.exception.ExchangeRateUnavailableException;
import zetta.foreignexchange.core.exception.IdempotencyKeyConflictException;
import zetta.foreignexchange.core.exception.InsufficientFundsException;
import zetta.foreignexchange.core.exception.SameCurrencyException;
import zetta.foreignexchange.core.exception.UnsupportedCurrencyPairException;
import zetta.foreignexchange.core.model.ErrorCode;
import zetta.foreignexchange.rest.error.ErrorResponse;

import java.util.Map;

@RestControllerAdvice
@Slf4j
public class ForeignExchangeControllerAdvice {

    private static final String CLIENT_NOT_FOUND_MESSAGE = "Client with ID:%s was not found.";
    private static final String EXCHANGE_RATE_UNAVAILABLE_MESSAGE =
            "Exchange rate for currency pair %s/%s is currently unavailable.";
    private static final String UNSUPPORTED_CURRENCY_PAIR_MESSAGE = "Currency pair %s/%s is not supported.";
    private static final String SAME_CURRENCY_MESSAGE = "Currency pair %s/%s must contain two different currencies.";
    private static final String BALANCE_NOT_FOUND_MESSAGE = "Client %s has no balance in %s.";
    private static final String INSUFFICIENT_FUNDS_MESSAGE = "Client %s has insufficient %s balance for this conversion.";
    private static final String IDEMPOTENCY_KEY_CONFLICT_MESSAGE =
            "Client %s already used Idempotency-Key %s for a different conversion request.";
    private static final String VALIDATION_FAILED_MESSAGE = "Request validation failed: %s";
    private static final String MALFORMED_REQUEST_MESSAGE = "The request body could not be read.";
    private static final String INTERNAL_SERVER_ERROR_MESSAGE = "An unexpected internal error has occurred.";
    private static final String FIELD_ERROR_MESSAGE =
            "Either you submitted a request that is missing a mandatory field or the value of a field does not match " +
                    "the format expected.";

    private static final Map<ErrorCode, HttpStatus> STATUS_BY_ERROR_CODE = Map.ofEntries(
            Map.entry(ErrorCode.CLIENT_NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(ErrorCode.BALANCE_NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(ErrorCode.INSUFFICIENT_FUNDS, HttpStatus.UNPROCESSABLE_CONTENT),
            Map.entry(ErrorCode.SAME_CURRENCY, HttpStatus.UNPROCESSABLE_CONTENT),
            Map.entry(ErrorCode.UNSUPPORTED_CURRENCY_PAIR, HttpStatus.UNPROCESSABLE_CONTENT),
            Map.entry(ErrorCode.IDEMPOTENCY_KEY_CONFLICT, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.EXCHANGE_RATE_UNAVAILABLE, HttpStatus.BAD_GATEWAY),
            Map.entry(ErrorCode.FIELD_ERROR, HttpStatus.BAD_REQUEST),
            Map.entry(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST),
            Map.entry(ErrorCode.MALFORMED_REQUEST, HttpStatus.BAD_REQUEST),
            Map.entry(ErrorCode.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR));

    @ExceptionHandler(ClientNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleClientNotFoundException(
            ClientNotFoundException exception, HttpServletRequest request) {

        log.error("ClientNotFoundException thrown", exception);
        return buildErrorResponse(
                ErrorCode.CLIENT_NOT_FOUND,
                format(CLIENT_NOT_FOUND_MESSAGE, exception.getClientId()),
                request);
    }

    @ExceptionHandler(ExchangeRateUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleRateUnavailableException(
            ExchangeRateUnavailableException exception, HttpServletRequest request) {

        log.error("ExchangeRateUnavailableException thrown", exception);
        return buildErrorResponse(
                ErrorCode.EXCHANGE_RATE_UNAVAILABLE,
                format(EXCHANGE_RATE_UNAVAILABLE_MESSAGE, exception.getBaseCurrency(), exception.getQuoteCurrency()),
                request);
    }

    @ExceptionHandler(UnsupportedCurrencyPairException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedCurrencyPairException(
            UnsupportedCurrencyPairException exception, HttpServletRequest request) {

        log.error("UnsupportedCurrencyPairException thrown", exception);
        return buildErrorResponse(
                ErrorCode.UNSUPPORTED_CURRENCY_PAIR,
                format(UNSUPPORTED_CURRENCY_PAIR_MESSAGE, exception.getBaseCurrency(), exception.getQuoteCurrency()),
                request);
    }

    @ExceptionHandler(SameCurrencyException.class)
    public ResponseEntity<ErrorResponse> handleSameCurrencyException(
            SameCurrencyException exception, HttpServletRequest request) {

        log.error("SameCurrencyException thrown", exception);
        return buildErrorResponse(
                ErrorCode.SAME_CURRENCY,
                format(SAME_CURRENCY_MESSAGE, exception.getBaseCurrency(), exception.getQuoteCurrency()),
                request);
    }

    @ExceptionHandler(BalanceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBalanceNotFoundException(
            BalanceNotFoundException exception, HttpServletRequest request) {

        log.error("BalanceNotFoundException thrown", exception);
        return buildErrorResponse(
                ErrorCode.BALANCE_NOT_FOUND,
                format(BALANCE_NOT_FOUND_MESSAGE, exception.getClientId(), exception.getCurrency()), request);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFundsException(
            InsufficientFundsException exception, HttpServletRequest request) {

        log.error("InsufficientFundsException thrown", exception);
        return buildErrorResponse(
                ErrorCode.INSUFFICIENT_FUNDS,
                format(INSUFFICIENT_FUNDS_MESSAGE, exception.getClientId(), exception.getCurrency()),
                request);
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyKeyConflictException(
            IdempotencyKeyConflictException exception, HttpServletRequest request) {

        log.error("IdempotencyKeyConflictException thrown", exception);
        return buildErrorResponse(
                ErrorCode.IDEMPOTENCY_KEY_CONFLICT,
                format(IDEMPOTENCY_KEY_CONFLICT_MESSAGE, exception.getClientId(), exception.getIdempotencyKey()),
                request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception, HttpServletRequest request) {

        log.error("MethodArgumentNotValidException thrown", exception);
        return buildErrorResponse(
                ErrorCode.FIELD_ERROR,
                FIELD_ERROR_MESSAGE,
                request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception, HttpServletRequest request) {

        log.error("HandlerMethodValidationException thrown", exception);
        return buildErrorResponse(
                ErrorCode.VALIDATION_FAILED,
                format(VALIDATION_FAILED_MESSAGE, exception.getMessage()),
                request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException exception, HttpServletRequest request) {

        log.error("ConstraintViolationException thrown", exception);
        return buildErrorResponse(
                ErrorCode.VALIDATION_FAILED,
                format(VALIDATION_FAILED_MESSAGE, exception.getMessage()),
                request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestHeaderException(
            MissingRequestHeaderException exception, HttpServletRequest request) {

        log.error("MissingRequestHeaderException thrown", exception);
        return buildErrorResponse(
                ErrorCode.VALIDATION_FAILED,
                format(VALIDATION_FAILED_MESSAGE, exception.getMessage()),
                request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException exception, HttpServletRequest request) {

        log.error("MissingServletRequestParameterException thrown", exception);
        return buildErrorResponse(
                ErrorCode.VALIDATION_FAILED,
                format(VALIDATION_FAILED_MESSAGE, exception.getMessage()),
                request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception, HttpServletRequest request) {

        log.error("HttpMessageNotReadableException thrown", exception);
        return buildErrorResponse(ErrorCode.MALFORMED_REQUEST, MALFORMED_REQUEST_MESSAGE, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception thrown", exception);
        return buildErrorResponse(ErrorCode.INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR_MESSAGE, request);
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(
            ErrorCode errorCode, String message, HttpServletRequest request) {

        HttpStatus status = STATUS_BY_ERROR_CODE.get(errorCode);
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code(errorCode.name())
                .message(message)
                .status(status.value())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(errorResponse);
    }
}
