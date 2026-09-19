package zetta.foreignexchange.core.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import zetta.foreignexchange.common.integrations.frankfurter.response.FrankfurterRatePairResponse;
import zetta.foreignexchange.core.model.ExchangeRate;

import java.math.BigDecimal;
import java.time.LocalDate;

class RateMapperTest {

    private static final String USD = "USD";
    private static final String EUR = "EUR";
    private static final LocalDate QUOTE_DATE = LocalDate.of(2026, 9, 20);
    private static final BigDecimal PROVIDER_RATE = new BigDecimal("0.86984");
    private static final BigDecimal SCALED_RATE = new BigDecimal("0.86984");

    private final RateMapper rateMapper = Mappers.getMapper(RateMapper.class);

    @Test
    void mapToRate_withFrankfurterRatePairResponse_mapCurrenciesAndDate() {
        FrankfurterRatePairResponse frankfurterRatePairResponse = buildRatePairResponse(PROVIDER_RATE);

        ExchangeRate exchangeRate = rateMapper.mapToRate(frankfurterRatePairResponse);

        assertNotNull(exchangeRate);
        assertEquals(QUOTE_DATE, exchangeRate.date());
        assertEquals(USD, exchangeRate.baseCurrency());
        assertEquals(EUR, exchangeRate.quoteCurrency());
        assertEquals(SCALED_RATE, exchangeRate.rate());
        assertEquals(SCALED_RATE.scale(), exchangeRate.rate().scale());
    }

    @Test
    void mapToRate_withRateNeedingRounding_roundHalfUp() {
        FrankfurterRatePairResponse frankfurterRatePairResponse =
                buildRatePairResponse(new BigDecimal("0.123456785"));

        ExchangeRate exchangeRate = rateMapper.mapToRate(frankfurterRatePairResponse);

        assertEquals(new BigDecimal("0.12346"), exchangeRate.rate());
    }

    @Test
    void mapToRate_withNullFrankfurterRatePairResponse_returnNull() {
        assertNull(rateMapper.mapToRate(null));
    }

    @Test
    void mapToRate_withNullRate_returnRateWithNullRate() {
        FrankfurterRatePairResponse frankfurterRatePairResponse = buildRatePairResponse(null);

        ExchangeRate exchangeRate = rateMapper.mapToRate(frankfurterRatePairResponse);

        assertNotNull(exchangeRate);
        assertNull(exchangeRate.rate());
    }

    private FrankfurterRatePairResponse buildRatePairResponse(BigDecimal rate) {
        return new FrankfurterRatePairResponse(QUOTE_DATE, USD, EUR, rate);
    }
}
