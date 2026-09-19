package zetta.foreignexchange.rest.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import zetta.foreignexchange.core.model.ExchangeRate;
import zetta.foreignexchange.rest.model.ExchangeRateResponse;

import java.math.BigDecimal;
import java.time.LocalDate;

class ExchangeRateResponseMapperTest {

    private static final String USD = "USD";
    private static final String EUR = "EUR";
    private static final LocalDate QUOTE_DATE = LocalDate.of(2026, 9, 20);
    private static final BigDecimal SCALED_RATE = new BigDecimal("0.86984000");

    private final ExchangeRateResponseMapper exchangeRateResponseMapper =
            Mappers.getMapper(ExchangeRateResponseMapper.class);

    @Test
    void mapToExchangeRateResponse_withExchangeRate_mapEveryFieldExchange() {
        ExchangeRate exchangeRate = new ExchangeRate(QUOTE_DATE, USD, EUR, SCALED_RATE);

        ExchangeRateResponse exchangeRateResponse = exchangeRateResponseMapper.mapToExchangeRateResponse(exchangeRate);

        assertNotNull(exchangeRateResponse);
        assertEquals(QUOTE_DATE, exchangeRateResponse.date());
        assertEquals(USD, exchangeRateResponse.baseCurrency());
        assertEquals(EUR, exchangeRateResponse.quoteCurrency());
        assertEquals(SCALED_RATE, exchangeRateResponse.rate());
    }

    @Test
    void mapToExchangeRateResponse_withNullExchangeRate_returnNull() {
        assertNull(exchangeRateResponseMapper.mapToExchangeRateResponse(null));
    }
}
