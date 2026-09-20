package zetta.foreignexchange.rest.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import zetta.foreignexchange.core.model.Conversion;
import zetta.foreignexchange.core.model.ConversionHistoryQuery;
import zetta.foreignexchange.core.model.ConversionInput;
import zetta.foreignexchange.core.model.ConversionResult;
import zetta.foreignexchange.core.service.ConversionService;
import zetta.foreignexchange.rest.mapper.ClientBalancesResponseMapper;
import zetta.foreignexchange.rest.mapper.ConversionHistoryQueryMapper;
import zetta.foreignexchange.rest.mapper.ConversionHistoryResponseMapper;
import zetta.foreignexchange.rest.mapper.ConversionRequestMapper;
import zetta.foreignexchange.rest.mapper.ConversionResponseMapper;
import zetta.foreignexchange.rest.model.ConversionHistoryFilterRequest;
import zetta.foreignexchange.rest.model.ConversionHistoryResponse;
import zetta.foreignexchange.rest.model.ConversionRequest;
import zetta.foreignexchange.rest.model.ConversionResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class ConversionControllerTest {

    private static final String CLIENT_ID = "CLIENT-001";
    private static final String IDEMPOTENCY_KEY = "IDEMPOTENCY-KEY-001";
    private static final String USD = "USD";
    private static final String EUR = "EUR";
    private static final BigDecimal BASE_AMOUNT = new BigDecimal("100.00");
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    @Mock
    private ConversionService conversionService;

    @Spy
    private ConversionRequestMapper conversionRequestMapper = Mappers.getMapper(ConversionRequestMapper.class);

    @Spy
    private ConversionResponseMapper conversionResponseMapper = Mappers.getMapper(ConversionResponseMapper.class);

    @Spy
    private ConversionHistoryQueryMapper conversionHistoryQueryMapper =
            Mappers.getMapper(ConversionHistoryQueryMapper.class);

    @Spy
    private ConversionHistoryResponseMapper conversionHistoryResponseMapper =
            Mappers.getMapper(ConversionHistoryResponseMapper.class);

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

    @Test
    void getConversionHistory_withFilters_returnConversionHistoryResponse() {
        ConversionHistoryFilterRequest conversionHistoryFilterRequest =
                new ConversionHistoryFilterRequest(null, null, CLIENT_ID);
        Conversion conversion = Instancio.create(Conversion.class);
        Page<Conversion> conversionPage =
                new PageImpl<>(List.of(conversion), PageRequest.of(DEFAULT_PAGE, DEFAULT_SIZE), 1);
        ArgumentCaptor<ConversionHistoryQuery> conversionHistoryQueryCaptor =
                ArgumentCaptor.forClass(ConversionHistoryQuery.class);

        when(conversionService.getConversionHistory(conversionHistoryQueryCaptor.capture()))
                .thenReturn(conversionPage);

        ResponseEntity<ConversionHistoryResponse> response = conversionController.getConversionHistory(
                conversionHistoryFilterRequest, DEFAULT_PAGE, DEFAULT_SIZE);

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        ConversionHistoryResponse conversionHistoryResponse = response.getBody();
        assertEquals(1, conversionHistoryResponse.content().size());
        assertEquals(conversion.transactionId(), conversionHistoryResponse.content().getFirst().transactionId());
        assertEquals(DEFAULT_PAGE, conversionHistoryResponse.page());
        assertEquals(DEFAULT_SIZE, conversionHistoryResponse.size());

        ConversionHistoryQuery capturedConversionHistoryQuery = conversionHistoryQueryCaptor.getValue();
        assertEquals(CLIENT_ID, capturedConversionHistoryQuery.clientId());
        assertEquals(DEFAULT_PAGE, capturedConversionHistoryQuery.page());
        assertEquals(DEFAULT_SIZE, capturedConversionHistoryQuery.size());

        verify(conversionService).getConversionHistory(capturedConversionHistoryQuery);
    }

    @Test
    void getConversionHistory_withoutPageAndSize_useDefaults() {
        UUID transactionId = UUID.randomUUID();
        ConversionHistoryFilterRequest conversionHistoryFilterRequest =
                new ConversionHistoryFilterRequest(transactionId, null, null);
        Page<Conversion> emptyConversionPage =
                new PageImpl<>(List.of(), PageRequest.of(DEFAULT_PAGE, DEFAULT_SIZE), 0);

        when(conversionService.getConversionHistory(any(ConversionHistoryQuery.class)))
                .thenReturn(emptyConversionPage);

        ResponseEntity<ConversionHistoryResponse> response = conversionController.getConversionHistory(
                conversionHistoryFilterRequest, DEFAULT_PAGE, DEFAULT_SIZE);

        assertNotNull(response.getBody());
        assertEquals(DEFAULT_PAGE, response.getBody().page());
        assertEquals(DEFAULT_SIZE, response.getBody().size());
    }
}
