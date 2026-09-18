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
import zetta.foreignexchange.rest.error.ErrorResponse;

@RestControllerAdvice
@Slf4j
public class ForeignExchangeControllerAdvice {

    private static final String CLIENT_NOT_FOUND_CODE = "CLIENT_NOT_FOUND";
    private static final String CLIENT_NOT_FOUND_MESSAGE = "Client with ID:%s was not found.";

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

    //TODO: Handle:
    // InternalServerError,
    // MethodArgumentNotValidException,
    // HttpMessageNotReadableException,
    // ConstraintViolationException, <-----> HandlerMethodValidationException
    // and other bad request exceptions as needed.
}
