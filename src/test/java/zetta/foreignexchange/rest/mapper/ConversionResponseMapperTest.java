package zetta.foreignexchange.rest.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;
import zetta.foreignexchange.core.model.Balance;
import zetta.foreignexchange.core.model.Conversion;
import zetta.foreignexchange.core.model.ConversionResult;
import zetta.foreignexchange.rest.model.ConversionRequest;
import zetta.foreignexchange.rest.model.ConversionResponse;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

class ConversionResponseMapperTest {

    private static final String USD = "USD";
    private static final String EUR = "EUR";
    private static final BigDecimal BASE_AMOUNT = new BigDecimal("100.0000");
    private static final BigDecimal QUOTE_AMOUNT = new BigDecimal("86.9840");
    private static final BigDecimal RATE = new BigDecimal("0.86984");
    private static final UUID TRANSACTION_ID = UUID.randomUUID();
    private static final OffsetDateTime TIMESTAMP = OffsetDateTime.now();

    private final ConversionResponseMapper conversionResponseMapper = Mappers.getMapper(ConversionResponseMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    /**
     * {@code componentModel = "spring"} generates {@code @Autowired} field injection for the {@code uses}
     * delegate, so a plain {@link Mappers#getMapper} instance (bypassing Spring) needs the real delegate
     * wired in by hand, exactly as a Spring context would.
     */
    @BeforeEach
    void wireDelegateMapper() {
        ReflectionTestUtils.setField(
                conversionResponseMapper, "clientBalancesResponseMapper",
                Mappers.getMapper(ClientBalancesResponseMapper.class));
    }

    @Test
    void mapToConversionResponse_withConversionResultAndBalances_mapEveryField() {
        Conversion conversion = new Conversion(TRANSACTION_ID, USD, BASE_AMOUNT, EUR, QUOTE_AMOUNT, RATE, TIMESTAMP);
        List<Balance> updatedBalances = List.of(new Balance(EUR, QUOTE_AMOUNT), new Balance(USD, BASE_AMOUNT));
        ConversionResult conversionResult = new ConversionResult(conversion, updatedBalances);

        ConversionResponse conversionResponse = conversionResponseMapper.mapToConversionResponse(conversionResult);

        assertNotNull(conversionResponse);
        assertEquals(TRANSACTION_ID, conversionResponse.transactionId());
        assertEquals(USD, conversionResponse.baseCurrency());
        assertEquals(BASE_AMOUNT, conversionResponse.baseAmount());
        assertEquals(EUR, conversionResponse.quoteCurrency());
        assertEquals(QUOTE_AMOUNT, conversionResponse.quoteAmount());
        assertEquals(RATE, conversionResponse.rate());
        assertEquals(TIMESTAMP, conversionResponse.timestamp());
        assertEquals(updatedBalances.size(), conversionResponse.balances().size());
        assertEquals(EUR, conversionResponse.balances().getFirst().currency());
        assertEquals(USD, conversionResponse.balances().getLast().currency());
    }

    @Test
    void mapToConversionResponse_withEmptyBalanceList_returnResponseWithEmptyBalances() {
        Conversion conversion = new Conversion(TRANSACTION_ID, USD, BASE_AMOUNT, EUR, QUOTE_AMOUNT, RATE, TIMESTAMP);
        ConversionResult conversionResult = new ConversionResult(conversion, List.of());

        ConversionResponse conversionResponse = conversionResponseMapper.mapToConversionResponse(conversionResult);

        assertNotNull(conversionResponse);
        assertTrue(conversionResponse.balances().isEmpty());
    }

    @Test
    void mapToConversionResponse_withNullConversionResult_returnNull() {
        assertNull(conversionResponseMapper.mapToConversionResponse(null));
    }

    @Test
    void conversionRequest_withBriefFieldNames_deserializeIntoBaseAndQuoteComponents() throws Exception {
        String requestBody = """
                {
                  "sourceCurrency": "USD",
                  "targetCurrency": "EUR",
                  "sourceAmount": 100.00
                }
                """;

        ConversionRequest conversionRequest = objectMapper.readValue(requestBody, ConversionRequest.class);

        assertEquals(USD, conversionRequest.baseCurrency());
        assertEquals(EUR, conversionRequest.quoteCurrency());
        assertEquals(0, new BigDecimal("100.00").compareTo(conversionRequest.baseAmount()));
    }

    @Test
    void conversionResponse_withBaseAndQuoteComponents_serialiseUnderBriefFieldNames() throws Exception {
        ConversionResponse conversionResponse = new ConversionResponse(
                TRANSACTION_ID, USD, BASE_AMOUNT, EUR, QUOTE_AMOUNT, RATE, TIMESTAMP, List.of());

        String json = objectMapper.writeValueAsString(conversionResponse);

        assertTrue(json.contains("\"sourceCurrency\":\"USD\""));
        assertTrue(json.contains("\"targetCurrency\":\"EUR\""));
        assertTrue(json.contains("\"sourceAmount\":100.0000"));
        assertTrue(json.contains("\"targetAmount\":86.9840"));
        assertTrue(!json.contains("baseCurrency"));
        assertTrue(!json.contains("quoteCurrency"));
    }
}
