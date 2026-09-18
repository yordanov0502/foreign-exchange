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
import zetta.foreignexchange.rest.error.ErrorResponse;

public class ForeignExchangeControllerAdviceTest {

    private static final String REQUEST_URI = "/test/uri";
    private static final String CLIENT_NOT_FOUND_CODE = "CLIENT_NOT_FOUND";
    private static final String CLIENT_NOT_FOUND_DETAIL = "Client with ID:%s was not found.";

    private final ForeignExchangeControllerAdvice controllerAdvice = new ForeignExchangeControllerAdvice();

    @Test
    void handleClientNotFoundException_withClientNotFoundException_returnProperResponseEntity() {
        String clientId = "CLIENT-999";
        ClientNotFoundException exception = new ClientNotFoundException(clientId);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getRequestURI()).thenReturn(REQUEST_URI);

        ResponseEntity<ErrorResponse> responseEntity =
                controllerAdvice.handleClientNotFoundException(exception, request);

        assertNotNull(responseEntity);
        assertNotNull(responseEntity.getBody());
        assertEquals(CLIENT_NOT_FOUND_CODE, responseEntity.getBody().code());
        assertEquals(format(CLIENT_NOT_FOUND_DETAIL, clientId), responseEntity.getBody().message());
        assertEquals(HttpStatus.NOT_FOUND, responseEntity.getStatusCode());
        assertEquals(REQUEST_URI, responseEntity.getBody().path());
    }
}
