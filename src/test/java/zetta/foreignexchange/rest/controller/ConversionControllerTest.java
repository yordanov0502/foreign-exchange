package zetta.foreignexchange.rest.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import zetta.foreignexchange.core.model.ConversionInput;
import zetta.foreignexchange.core.model.ConversionResult;
import zetta.foreignexchange.core.service.ConversionService;
import zetta.foreignexchange.rest.mapper.ClientBalancesResponseMapper;
import zetta.foreignexchange.rest.mapper.ConversionRequestMapper;
import zetta.foreignexchange.rest.mapper.ConversionResponseMapper;
import zetta.foreignexchange.rest.model.ConversionRequest;
import zetta.foreignexchange.rest.model.ConversionResponse;

import java.math.BigDecimal;

@ExtendWith(MockitoExtension.class)
class ConversionControllerTest {

    private static final String CLIENT_ID = "CLIENT-001";
    private static final String IDEMPOTENCY_KEY = "IDEMPOTENCY-KEY-001";
    private static final String USD = "USD";
    private static final String EUR = "EUR";
    private static final BigDecimal BASE_AMOUNT = new BigDecimal("100.00");

    @Mock
    private ConversionService conversionService;

    @Spy
    private ConversionRequestMapper conversionRequestMapper = Mappers.getMapper(ConversionRequestMapper.class);

    @Spy
    private ConversionResponseMapper conversionResponseMapper = Mappers.getMapper(ConversionResponseMapper.class);

    @InjectMocks
    private ConversionController conversionController;

    @BeforeEach
    void wireDelegateMapper() {
        ReflectionTestUtils.setField(
                conversionResponseMapper, "clientBalancesResponseMapper",
                Mappers.getMapper(ClientBalancesResponseMapper.class));
    }

    @Test
    void createConversion_withValidRequest_returnConversionResponse() {
        ConversionRequest conversionRequest = new ConversionRequest(USD, EUR, BASE_AMOUNT);
        ConversionInput expectedInput =
                conversionRequestMapper.mapToConversionInput(CLIENT_ID, IDEMPOTENCY_KEY, conversionRequest);
        ConversionResult conversionResult = Instancio.create(ConversionResult.class);

        when(conversionService.convert(expectedInput))
                .thenReturn(conversionResult);

        ResponseEntity<ConversionResponse> response =
                conversionController.createConversion(CLIENT_ID, IDEMPOTENCY_KEY, conversionRequest);

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        ConversionResponse conversionResponse = response.getBody();
        assertEquals(conversionResult.conversion().transactionId(), conversionResponse.transactionId());
        assertEquals(conversionResult.conversion().baseCurrency(), conversionResponse.baseCurrency());
        assertEquals(conversionResult.conversion().baseAmount(), conversionResponse.baseAmount());
        assertEquals(conversionResult.conversion().quoteCurrency(), conversionResponse.quoteCurrency());
        assertEquals(conversionResult.conversion().quoteAmount(), conversionResponse.quoteAmount());
        assertEquals(conversionResult.conversion().rate(), conversionResponse.rate());
        assertEquals(conversionResult.conversion().timestamp(), conversionResponse.timestamp());
        assertEquals(conversionResult.updatedBalances().size(), conversionResponse.balances().size());

        verify(conversionService).convert(expectedInput);
        verify(conversionResponseMapper).mapToConversionResponse(conversionResult);
    }

    @Test
    void createConversion_withoutIdempotencyKey_returnConversionResponse() {
        ConversionRequest conversionRequest = new ConversionRequest(USD, EUR, BASE_AMOUNT);
        ConversionInput expectedInput =
                conversionRequestMapper.mapToConversionInput(CLIENT_ID, null, conversionRequest);
        ConversionResult conversionResult = Instancio.create(ConversionResult.class);

        when(conversionService.convert(expectedInput))
                .thenReturn(conversionResult);

        ResponseEntity<ConversionResponse> response =
                conversionController.createConversion(CLIENT_ID, null, conversionRequest);

        assertNotNull(response.getBody());
        assertEquals(HttpStatus.CREATED, response.getStatusCode());

        verify(conversionService).convert(expectedInput);
        verify(conversionResponseMapper).mapToConversionResponse(conversionResult);
    }
}
