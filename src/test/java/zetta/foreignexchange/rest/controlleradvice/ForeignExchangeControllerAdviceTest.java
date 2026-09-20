package zetta.foreignexchange.rest.controlleradvice;

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import zetta.foreignexchange.core.exception.BalanceNotFoundException;
import zetta.foreignexchange.core.exception.ClientNotFoundException;
import zetta.foreignexchange.core.exception.ExchangeRateUnavailableException;
import zetta.foreignexchange.core.exception.IdempotencyKeyConflictException;
import zetta.foreignexchange.core.exception.InsufficientFundsException;
import zetta.foreignexchange.core.exception.SameCurrencyException;
import zetta.foreignexchange.core.exception.UnsupportedCurrencyPairException;
import zetta.foreignexchange.rest.error.ErrorResponse;

public class ForeignExchangeControllerAdviceTest {

    private static final String REQUEST_URI = "/test/uri";
    private static final String CLIENT_NOT_FOUND_CODE = "CLIENT_NOT_FOUND";
    private static final String CLIENT_NOT_FOUND_MESSAGE = "Client with ID:%s was not found.";
    private static final String EXCHANGE_RATE_UNAVAILABLE_CODE = "EXCHANGE_RATE_UNAVAILABLE";
    private static final String EXCHANGE_RATE_UNAVAILABLE_MESSAGE =
            "Exchange rate for currency pair %s/%s is currently unavailable.";
    private static final String UNSUPPORTED_CURRENCY_PAIR_CODE = "UNSUPPORTED_CURRENCY_PAIR";
    private static final String UNSUPPORTED_CURRENCY_PAIR_MESSAGE = "Currency pair %s/%s is not supported.";
    private static final String SAME_CURRENCY_CODE = "SAME_CURRENCY";
    private static final String SAME_CURRENCY_MESSAGE = "Currency pair %s/%s must contain two different currencies.";
    private static final String BALANCE_NOT_FOUND_CODE = "BALANCE_NOT_FOUND";
    private static final String BALANCE_NOT_FOUND_MESSAGE = "Client %s has no balance in %s.";
    private static final String INSUFFICIENT_FUNDS_CODE = "INSUFFICIENT_FUNDS";
    private static final String INSUFFICIENT_FUNDS_MESSAGE = "Client %s has insufficient %s balance for this conversion.";
    private static final String IDEMPOTENCY_KEY_CONFLICT_CODE = "IDEMPOTENCY_KEY_CONFLICT";
    private static final String IDEMPOTENCY_KEY_CONFLICT_MESSAGE =
            "Client %s already used Idempotency-Key %s for a different conversion request.";
    private static final String CLIENT_ID = "CLIENT-001";
    private static final String USD = "USD";
    private static final String EUR = "EUR";
    private static final String UNKNOWN_CURRENCY = "XXX";
    private static final String VALIDATION_FAILED_CODE = "VALIDATION_FAILED";
    private static final String VALIDATION_FAILED_MESSAGE = "Request validation failed: %s";
    private static final String FIELD_ERROR_CODE = "FIELD_ERROR";
    private static final String FIELD_ERROR_MESSAGE =
            "Either you submitted a request that is missing a mandatory field or the value of a field does not match "
                    + "the format expected.";
    private static final String MALFORMED_REQUEST_CODE = "MALFORMED_REQUEST";
    private static final String MALFORMED_REQUEST_MESSAGE = "The request body could not be read.";
    private static final String INTERNAL_SERVER_ERROR_CODE = "INTERNAL_SERVER_ERROR";
    private static final String INTERNAL_SERVER_ERROR_MESSAGE = "An unexpected internal error has occurred.";
    private static final String EXCEPTION_MESSAGE = "exception message";

    private final ForeignExchangeControllerAdvice controllerAdvice = new ForeignExchangeControllerAdvice();

    @Test
    void handleClientNotFoundException_withClientNotFoundException_returnNotFoundResponseEntity() {
        String clientId = "CLIENT-999";
        ClientNotFoundException exception = new ClientNotFoundException(clientId);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getRequestURI()).thenReturn(REQUEST_URI);

        ResponseEntity<ErrorResponse> responseEntity =
                controllerAdvice.handleClientNotFoundException(exception, request);

        assertNotNull(responseEntity);
        assertNotNull(responseEntity.getBody());
        assertEquals(CLIENT_NOT_FOUND_CODE, responseEntity.getBody().code());
        assertEquals(format(CLIENT_NOT_FOUND_MESSAGE, clientId), responseEntity.getBody().message());
        assertEquals(HttpStatus.NOT_FOUND, responseEntity.getStatusCode());
        assertEquals(REQUEST_URI, responseEntity.getBody().path());
    }

    @Test
    void handleRateUnavailableException_withRateUnavailableException_returnBadGatewayResponseEntity() {
        ExchangeRateUnavailableException exception = new ExchangeRateUnavailableException(USD, EUR, new RuntimeException());
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getRequestURI()).thenReturn(REQUEST_URI);

        ResponseEntity<ErrorResponse> responseEntity =
                controllerAdvice.handleRateUnavailableException(exception, request);

        assertNotNull(responseEntity);
        assertNotNull(responseEntity.getBody());
        assertEquals(EXCHANGE_RATE_UNAVAILABLE_CODE, responseEntity.getBody().code());
        assertEquals(format(EXCHANGE_RATE_UNAVAILABLE_MESSAGE, USD, EUR), responseEntity.getBody().message());
        assertEquals(HttpStatus.BAD_GATEWAY, responseEntity.getStatusCode());
        assertEquals(HttpStatus.BAD_GATEWAY.value(), responseEntity.getBody().status());
        assertEquals(REQUEST_URI, responseEntity.getBody().path());
    }

    @Test
    void handleUnsupportedCurrencyPairException_withUnsupportedPair_returnUnprocessableEntityResponseEntity() {
        UnsupportedCurrencyPairException exception = new UnsupportedCurrencyPairException(USD, UNKNOWN_CURRENCY, null);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getRequestURI()).thenReturn(REQUEST_URI);

        ResponseEntity<ErrorResponse> responseEntity =
                controllerAdvice.handleUnsupportedCurrencyPairException(exception, request);

        assertNotNull(responseEntity);
        assertNotNull(responseEntity.getBody());
        assertEquals(UNSUPPORTED_CURRENCY_PAIR_CODE, responseEntity.getBody().code());
        assertEquals(format(UNSUPPORTED_CURRENCY_PAIR_MESSAGE, USD, UNKNOWN_CURRENCY), responseEntity.getBody().message());
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, responseEntity.getStatusCode());
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT.value(), responseEntity.getBody().status());
        assertEquals(REQUEST_URI, responseEntity.getBody().path());
    }

    @Test
    void handleSameCurrencyException_withSameCurrencyException_returnUnprocessableEntityResponseEntity() {
        SameCurrencyException exception = new SameCurrencyException(USD, USD);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getRequestURI()).thenReturn(REQUEST_URI);

        ResponseEntity<ErrorResponse> responseEntity =
                controllerAdvice.handleSameCurrencyException(exception, request);

        assertNotNull(responseEntity);
        assertNotNull(responseEntity.getBody());
        assertEquals(SAME_CURRENCY_CODE, responseEntity.getBody().code());
        assertEquals(format(SAME_CURRENCY_MESSAGE, USD, USD), responseEntity.getBody().message());
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, responseEntity.getStatusCode());
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT.value(), responseEntity.getBody().status());
        assertEquals(REQUEST_URI, responseEntity.getBody().path());
    }

    @Test
    void handleBalanceNotFoundException_withBalanceNotFoundException_returnNotFoundResponseEntity() {
        BalanceNotFoundException exception = new BalanceNotFoundException(CLIENT_ID, EUR);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getRequestURI()).thenReturn(REQUEST_URI);

        ResponseEntity<ErrorResponse> responseEntity =
                controllerAdvice.handleBalanceNotFoundException(exception, request);

        assertNotNull(responseEntity);
        assertNotNull(responseEntity.getBody());
        assertEquals(BALANCE_NOT_FOUND_CODE, responseEntity.getBody().code());
        assertEquals(format(BALANCE_NOT_FOUND_MESSAGE, CLIENT_ID, EUR), responseEntity.getBody().message());
        assertEquals(HttpStatus.NOT_FOUND, responseEntity.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND.value(), responseEntity.getBody().status());
        assertEquals(REQUEST_URI, responseEntity.getBody().path());
    }

    @Test
    void handleInsufficientFundsException_withInsufficientFundsException_returnUnprocessableEntityResponseEntity() {
        InsufficientFundsException exception = new InsufficientFundsException(CLIENT_ID, USD);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getRequestURI()).thenReturn(REQUEST_URI);

        ResponseEntity<ErrorResponse> responseEntity =
                controllerAdvice.handleInsufficientFundsException(exception, request);

        assertNotNull(responseEntity);
        assertNotNull(responseEntity.getBody());
        assertEquals(INSUFFICIENT_FUNDS_CODE, responseEntity.getBody().code());
        assertEquals(format(INSUFFICIENT_FUNDS_MESSAGE, CLIENT_ID, USD), responseEntity.getBody().message());
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, responseEntity.getStatusCode());
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT.value(), responseEntity.getBody().status());
        assertEquals(REQUEST_URI, responseEntity.getBody().path());
    }

    @Test
    void handleIdempotencyKeyConflictException_withIdempotencyKeyConflictException_returnConflictResponseEntity() {
        String idempotencyKey = "IDEMPOTENCY-KEY-001";
        IdempotencyKeyConflictException exception = new IdempotencyKeyConflictException(CLIENT_ID, idempotencyKey);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getRequestURI()).thenReturn(REQUEST_URI);

        ResponseEntity<ErrorResponse> responseEntity =
                controllerAdvice.handleIdempotencyKeyConflictException(exception, request);

        assertNotNull(responseEntity);
        assertNotNull(responseEntity.getBody());
        assertEquals(IDEMPOTENCY_KEY_CONFLICT_CODE, responseEntity.getBody().code());
        assertEquals(
                format(IDEMPOTENCY_KEY_CONFLICT_MESSAGE, CLIENT_ID, idempotencyKey),
                responseEntity.getBody().message());
        assertEquals(HttpStatus.CONFLICT, responseEntity.getStatusCode());
        assertEquals(HttpStatus.CONFLICT.value(), responseEntity.getBody().status());
        assertEquals(REQUEST_URI, responseEntity.getBody().path());
    }

    @Test
    void handleMethodArgumentNotValidException_withValidationException_returnBadRequestResponseEntity() {
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getRequestURI()).thenReturn(REQUEST_URI);

        ResponseEntity<ErrorResponse> responseEntity =
                controllerAdvice.handleMethodArgumentNotValidException(exception, request);

        assertNotNull(responseEntity);
        assertNotNull(responseEntity.getBody());
        assertEquals(FIELD_ERROR_CODE, responseEntity.getBody().code());
        assertEquals(FIELD_ERROR_MESSAGE, responseEntity.getBody().message());
        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        assertEquals(REQUEST_URI, responseEntity.getBody().path());
    }

    @Test
    void handleHandlerMethodValidationException_withValidationException_returnBadRequestResponseEntity() {
        HandlerMethodValidationException exception = mock(HandlerMethodValidationException.class);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(exception.getMessage()).thenReturn(EXCEPTION_MESSAGE);
        when(request.getRequestURI()).thenReturn(REQUEST_URI);

        ResponseEntity<ErrorResponse> responseEntity =
                controllerAdvice.handleHandlerMethodValidationException(exception, request);

        assertNotNull(responseEntity);
        assertNotNull(responseEntity.getBody());
        assertEquals(VALIDATION_FAILED_CODE, responseEntity.getBody().code());
        assertEquals(format(VALIDATION_FAILED_MESSAGE, EXCEPTION_MESSAGE), responseEntity.getBody().message());
        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        assertEquals(REQUEST_URI, responseEntity.getBody().path());
    }

    @Test
    void handleConstraintViolationException_withBlankHeaderViolation_returnBadRequestResponseEntity() {
        ConstraintViolationException exception = mock(ConstraintViolationException.class);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(exception.getMessage()).thenReturn(EXCEPTION_MESSAGE);
        when(request.getRequestURI()).thenReturn(REQUEST_URI);

        ResponseEntity<ErrorResponse> responseEntity =
                controllerAdvice.handleConstraintViolationException(exception, request);

        assertNotNull(responseEntity);
        assertNotNull(responseEntity.getBody());
        assertEquals(VALIDATION_FAILED_CODE, responseEntity.getBody().code());
        assertEquals(format(VALIDATION_FAILED_MESSAGE, EXCEPTION_MESSAGE), responseEntity.getBody().message());
        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        assertEquals(REQUEST_URI, responseEntity.getBody().path());
    }

    @Test
    void handleMissingRequestHeaderException_withAbsentHeader_returnBadRequestResponseEntity() {
        MissingRequestHeaderException exception = mock(MissingRequestHeaderException.class);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(exception.getMessage()).thenReturn(EXCEPTION_MESSAGE);
        when(request.getRequestURI()).thenReturn(REQUEST_URI);

        ResponseEntity<ErrorResponse> responseEntity =
                controllerAdvice.handleMissingRequestHeaderException(exception, request);

        assertNotNull(responseEntity);
        assertNotNull(responseEntity.getBody());
        assertEquals(VALIDATION_FAILED_CODE, responseEntity.getBody().code());
        assertEquals(format(VALIDATION_FAILED_MESSAGE, EXCEPTION_MESSAGE), responseEntity.getBody().message());
        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        assertEquals(REQUEST_URI, responseEntity.getBody().path());
    }

    @Test
    void handleMissingServletRequestParameterException_withAbsentParameter_returnBadRequestResponseEntity() {
        MissingServletRequestParameterException exception = mock(MissingServletRequestParameterException.class);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(exception.getMessage()).thenReturn(EXCEPTION_MESSAGE);
        when(request.getRequestURI()).thenReturn(REQUEST_URI);

        ResponseEntity<ErrorResponse> responseEntity =
                controllerAdvice.handleMissingServletRequestParameterException(exception, request);

        assertNotNull(responseEntity);
        assertNotNull(responseEntity.getBody());
        assertEquals(VALIDATION_FAILED_CODE, responseEntity.getBody().code());
        assertEquals(format(VALIDATION_FAILED_MESSAGE, EXCEPTION_MESSAGE), responseEntity.getBody().message());
        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        assertEquals(REQUEST_URI, responseEntity.getBody().path());
    }

    @Test
    void handleHttpMessageNotReadableException_withMalformedBody_returnBadRequestResponseEntity() {
        HttpMessageNotReadableException exception = mock(HttpMessageNotReadableException.class);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getRequestURI()).thenReturn(REQUEST_URI);

        ResponseEntity<ErrorResponse> responseEntity =
                controllerAdvice.handleHttpMessageNotReadableException(exception, request);

        assertNotNull(responseEntity);
        assertNotNull(responseEntity.getBody());
        assertEquals(MALFORMED_REQUEST_CODE, responseEntity.getBody().code());
        assertEquals(MALFORMED_REQUEST_MESSAGE, responseEntity.getBody().message());
        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        assertEquals(REQUEST_URI, responseEntity.getBody().path());
    }

    @Test
    void handleUnexpectedException_withUnhandledException_returnInternalServerErrorResponseEntity() {
        Exception exception = new RuntimeException(EXCEPTION_MESSAGE);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getRequestURI()).thenReturn(REQUEST_URI);

        ResponseEntity<ErrorResponse> responseEntity = controllerAdvice.handleUnexpectedException(exception, request);

        assertNotNull(responseEntity);
        assertNotNull(responseEntity.getBody());
        assertEquals(INTERNAL_SERVER_ERROR_CODE, responseEntity.getBody().code());
        assertEquals(INTERNAL_SERVER_ERROR_MESSAGE, responseEntity.getBody().message());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseEntity.getStatusCode());
        assertEquals(REQUEST_URI, responseEntity.getBody().path());
    }
}
