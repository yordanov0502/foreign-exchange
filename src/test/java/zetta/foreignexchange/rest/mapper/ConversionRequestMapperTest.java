package zetta.foreignexchange.rest.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import zetta.foreignexchange.core.model.ConversionInput;
import zetta.foreignexchange.rest.model.ConversionRequest;

import java.math.BigDecimal;

class ConversionRequestMapperTest {

    private static final String CLIENT_ID = "CLIENT-001";
    private static final String IDEMPOTENCY_KEY = "KEY-001";
    private static final String USD = "USD";
    private static final String EUR = "EUR";
    private static final BigDecimal BASE_AMOUNT = new BigDecimal("100.0000");

    private final ConversionRequestMapper conversionRequestMapper = Mappers.getMapper(ConversionRequestMapper.class);

    @Test
    void mapToConversionInput_withHeadersAndRequest_mapEveryField() {
        ConversionRequest conversionRequest = new ConversionRequest(USD, EUR, BASE_AMOUNT);

        ConversionInput conversionInput =
                conversionRequestMapper.mapToConversionInput(CLIENT_ID, IDEMPOTENCY_KEY, conversionRequest);

        assertNotNull(conversionInput);
        assertEquals(CLIENT_ID, conversionInput.clientId());
        assertEquals(IDEMPOTENCY_KEY, conversionInput.idempotencyKey());
        assertEquals(USD, conversionInput.baseCurrency());
        assertEquals(EUR, conversionInput.quoteCurrency());
        assertEquals(BASE_AMOUNT, conversionInput.baseAmount());
    }

    @Test
    void mapToConversionInput_withNullIdempotencyKey_mapRemainingFields() {
        ConversionRequest conversionRequest = new ConversionRequest(USD, EUR, BASE_AMOUNT);

        ConversionInput conversionInput =
                conversionRequestMapper.mapToConversionInput(CLIENT_ID, null, conversionRequest);

        assertNotNull(conversionInput);
        assertEquals(CLIENT_ID, conversionInput.clientId());
        assertNull(conversionInput.idempotencyKey());
        assertEquals(USD, conversionInput.baseCurrency());
        assertEquals(EUR, conversionInput.quoteCurrency());
        assertEquals(BASE_AMOUNT, conversionInput.baseAmount());
    }

    @Test
    void mapToConversionInput_withNullRequest_mapHeadersOnly() {
        ConversionInput conversionInput =
                conversionRequestMapper.mapToConversionInput(CLIENT_ID, IDEMPOTENCY_KEY, null);

        assertNotNull(conversionInput);
        assertEquals(CLIENT_ID, conversionInput.clientId());
        assertEquals(IDEMPOTENCY_KEY, conversionInput.idempotencyKey());
        assertNull(conversionInput.baseCurrency());
    }
}
