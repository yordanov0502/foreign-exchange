package zetta.foreignexchange.rest.controlleradvice;

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import zetta.foreignexchange.core.exception.ClientNotFoundException;
import zetta.foreignexchange.core.exception.ExchangeRateUnavailableException;
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
    private static final String USD = "USD";
    private static final String EUR = "EUR";
    private static final String UNKNOWN_CURRENCY = "XXX";

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
}
