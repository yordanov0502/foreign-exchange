package zetta.foreignexchange.rest.controlleradvice;

import static java.lang.String.format;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import zetta.foreignexchange.core.exception.ClientNotFoundException;
import zetta.foreignexchange.core.exception.ExchangeRateUnavailableException;
import zetta.foreignexchange.core.exception.UnsupportedCurrencyPairException;
import zetta.foreignexchange.rest.error.ErrorResponse;

@RestControllerAdvice
@Slf4j
public class ForeignExchangeControllerAdvice {

    private static final String CLIENT_NOT_FOUND_CODE = "CLIENT_NOT_FOUND";
    private static final String CLIENT_NOT_FOUND_MESSAGE = "Client with ID:%s was not found.";
    private static final String EXCHANGE_RATE_UNAVAILABLE_CODE = "EXCHANGE_RATE_UNAVAILABLE";
    private static final String EXCHANGE_RATE_UNAVAILABLE_MESSAGE =
            "Exchange rate for currency pair %s/%s is currently unavailable.";
    private static final String UNSUPPORTED_CURRENCY_PAIR_CODE = "UNSUPPORTED_CURRENCY_PAIR";
    private static final String UNSUPPORTED_CURRENCY_PAIR_MESSAGE = "Currency pair %s/%s is not supported.";

    @ExceptionHandler(ClientNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleClientNotFoundException(
            ClientNotFoundException exception, HttpServletRequest request) {

        log.error("ClientNotFoundException thrown", exception);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .code(CLIENT_NOT_FOUND_CODE)
                .message(format(CLIENT_NOT_FOUND_MESSAGE, exception.getClientId()))
                .status(HttpStatus.NOT_FOUND.value())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(errorResponse);
    }

    @ExceptionHandler(ExchangeRateUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleRateUnavailableException(
            ExchangeRateUnavailableException exception, HttpServletRequest request) {

        log.error("ExchangeRateUnavailableException thrown", exception);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .code(EXCHANGE_RATE_UNAVAILABLE_CODE)
                .message(format(
                        EXCHANGE_RATE_UNAVAILABLE_MESSAGE,
                        exception.getBaseCurrency(),
                        exception.getQuoteCurrency()))
                .status(HttpStatus.BAD_GATEWAY.value())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(errorResponse);
    }

    @ExceptionHandler(UnsupportedCurrencyPairException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedCurrencyPairException(
            UnsupportedCurrencyPairException exception, HttpServletRequest request) {

        log.error("UnsupportedCurrencyPairException thrown", exception);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .code(UNSUPPORTED_CURRENCY_PAIR_CODE)
                .message(format(
                        UNSUPPORTED_CURRENCY_PAIR_MESSAGE,
                        exception.getBaseCurrency(),
                        exception.getQuoteCurrency()))
                .status(HttpStatus.UNPROCESSABLE_CONTENT.value())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(errorResponse);
    }

}
