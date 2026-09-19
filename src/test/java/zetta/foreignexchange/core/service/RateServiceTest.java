package zetta.foreignexchange.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import zetta.foreignexchange.common.integrations.frankfurter.FrankfurterFeignClient;
import zetta.foreignexchange.common.integrations.frankfurter.exception.FrankfurterGeneralException;
import zetta.foreignexchange.common.integrations.frankfurter.exception.FrankfurterPairNotQuotableException;
import zetta.foreignexchange.common.integrations.frankfurter.response.FrankfurterRatePairResponse;
import zetta.foreignexchange.core.exception.ExchangeRateUnavailableException;
import zetta.foreignexchange.core.exception.UnsupportedCurrencyPairException;
import zetta.foreignexchange.core.mapper.RateMapper;
import zetta.foreignexchange.core.model.ExchangeRate;
import zetta.foreignexchange.core.service.implementation.RateServiceImpl;
import zetta.foreignexchange.core.validator.CurrencyValidator;

import java.math.BigDecimal;
import java.time.LocalDate;

@ExtendWith(MockitoExtension.class)
class RateServiceTest {

    private static final String USD = "USD";
    private static final String EUR = "EUR";
    private static final String UNKNOWN_CURRENCY = "XXX";
    private static final String LOWERCASE_CURRENCY = "us";
    private static final LocalDate QUOTE_DATE = LocalDate.of(2026, 9, 20);
    private static final BigDecimal PROVIDER_RATE = new BigDecimal("0.86984");
    private static final BigDecimal SCALED_RATE = new BigDecimal("0.86984000");
    private static final String PROVIDER_FAILED_MESSAGE = "provider failed";

    @Mock
    private CurrencyValidator currencyValidator;

    @Mock
    private FrankfurterFeignClient frankfurterFeignClient;

    @Spy
    private RateMapper rateMapper = Mappers.getMapper(RateMapper.class);

    @InjectMocks
    private RateServiceImpl rateService;

    @Test
    void getExchangeRate_withValidCurrencyPair_returnExchangeRate() {
        FrankfurterRatePairResponse frankfurterRatePairResponse = buildFrankfurterRatePairResponse(USD, EUR, PROVIDER_RATE);

        when(frankfurterFeignClient.fetchLatestExchangeRates(USD, EUR))
                .thenReturn(frankfurterRatePairResponse);

        ExchangeRate exchangeRate = rateService.getExchangeRate(USD, EUR);

        assertNotNull(exchangeRate);
        assertEquals(QUOTE_DATE, exchangeRate.date());
        assertEquals(USD, exchangeRate.baseCurrency());
        assertEquals(EUR, exchangeRate.quoteCurrency());
        assertEquals(SCALED_RATE, exchangeRate.rate());

        verify(frankfurterFeignClient).fetchLatestExchangeRates(USD, EUR);
        verify(rateMapper).mapToRate(frankfurterRatePairResponse);
    }

    @Test
    void getExchangeRate_withIdenticalCurrencyPair_returnExchangeRateOfOne() {
        when(frankfurterFeignClient.fetchLatestExchangeRates(USD, USD))
                .thenReturn(buildFrankfurterRatePairResponse(USD, USD, BigDecimal.ONE));

        ExchangeRate exchangeRate = rateService.getExchangeRate(USD, USD);

        assertNotNull(exchangeRate);
        assertEquals(QUOTE_DATE, exchangeRate.date());
        assertEquals(USD, exchangeRate.baseCurrency());
        assertEquals(USD, exchangeRate.quoteCurrency());
        assertEquals(0, exchangeRate.rate().compareTo(BigDecimal.ONE));

        verify(frankfurterFeignClient).fetchLatestExchangeRates(USD, USD);
    }

    @Test
    void getExchangeRate_whenProviderIsUnavailable_throwExchangeRateUnavailableException() {
        FrankfurterGeneralException providerException =
                new FrankfurterGeneralException(PROVIDER_FAILED_MESSAGE);

        when(frankfurterFeignClient.fetchLatestExchangeRates(USD, EUR))
                .thenThrow(providerException);

        ExchangeRateUnavailableException exception = assertThrows(
                ExchangeRateUnavailableException.class,
                () -> rateService.getExchangeRate(USD, EUR));

        assertEquals(USD, exception.getBaseCurrency());
        assertEquals(EUR, exception.getQuoteCurrency());
        assertSame(providerException, exception.getCause());

        verify(frankfurterFeignClient).fetchLatestExchangeRates(USD, EUR);
        verifyNoInteractions(rateMapper);
    }

    @Test
    void getExchangeRate_whenProviderRejectsCurrencyPair_throwUnsupportedCurrencyPairException() {
        FrankfurterPairNotQuotableException notQuotableException =
                new FrankfurterPairNotQuotableException(PROVIDER_FAILED_MESSAGE);

        when(frankfurterFeignClient.fetchLatestExchangeRates(USD, UNKNOWN_CURRENCY))
                .thenThrow(notQuotableException);

        UnsupportedCurrencyPairException exception = assertThrows(
                UnsupportedCurrencyPairException.class,
                () -> rateService.getExchangeRate(USD, UNKNOWN_CURRENCY));

        assertEquals(USD, exception.getBaseCurrency());
        assertEquals(UNKNOWN_CURRENCY, exception.getQuoteCurrency());
        assertSame(notQuotableException, exception.getCause());

        verifyNoInteractions(rateMapper);
    }

    @Test
    void getExchangeRate_whenProviderReturnsNullBody_throwExchangeRateUnavailableException() {
        when(frankfurterFeignClient.fetchLatestExchangeRates(USD, EUR))
                .thenReturn(null);

        assertThrows(ExchangeRateUnavailableException.class, () -> rateService.getExchangeRate(USD, EUR));

        verify(frankfurterFeignClient).fetchLatestExchangeRates(USD, EUR);
        verifyNoInteractions(rateMapper);
    }

    @Test
    void getExchangeRate_whenProviderReturnsNullDate_throwExchangeRateUnavailableException() {
        when(frankfurterFeignClient.fetchLatestExchangeRates(USD, EUR))
                .thenReturn(new FrankfurterRatePairResponse(null, USD, EUR, PROVIDER_RATE));

        assertThrows(ExchangeRateUnavailableException.class, () -> rateService.getExchangeRate(USD, EUR));

        verify(frankfurterFeignClient).fetchLatestExchangeRates(USD, EUR);
        verifyNoInteractions(rateMapper);
    }

    @Test
    void getExchangeRate_whenProviderReturnsNullBase_throwExchangeRateUnavailableException() {
        when(frankfurterFeignClient.fetchLatestExchangeRates(USD, EUR))
                .thenReturn(buildFrankfurterRatePairResponse(null, EUR, PROVIDER_RATE));

        assertThrows(ExchangeRateUnavailableException.class, () -> rateService.getExchangeRate(USD, EUR));

        verify(frankfurterFeignClient).fetchLatestExchangeRates(USD, EUR);
        verifyNoInteractions(rateMapper);
    }

    @Test
    void getExchangeRate_whenProviderReturnsNullQuote_throwExchangeRateUnavailableException() {
        when(frankfurterFeignClient.fetchLatestExchangeRates(USD, EUR))
                .thenReturn(buildFrankfurterRatePairResponse(USD, null, PROVIDER_RATE));

        assertThrows(ExchangeRateUnavailableException.class, () -> rateService.getExchangeRate(USD, EUR));

        verify(frankfurterFeignClient).fetchLatestExchangeRates(USD, EUR);
        verifyNoInteractions(rateMapper);
    }

    @Test
    void getExchangeRate_whenProviderReturnsNullRate_throwExchangeRateUnavailableException() {
        when(frankfurterFeignClient.fetchLatestExchangeRates(USD, EUR))
                .thenReturn(buildFrankfurterRatePairResponse(USD, EUR, null));

        assertThrows(ExchangeRateUnavailableException.class, () -> rateService.getExchangeRate(USD, EUR));

        verify(frankfurterFeignClient).fetchLatestExchangeRates(USD, EUR);
        verifyNoInteractions(rateMapper);
    }

    /**
     * Regex/JDK-currency validation itself is {@link CurrencyValidator}'s own responsibility and is covered by
     * {@code CurrencyValidatorTest}; this only proves {@code RateServiceImpl} propagates that failure without
     * ever calling the provider.
     */
    @Test
    void getExchangeRate_whenValidationFails_throwWithoutCallingProvider() {
        UnsupportedCurrencyPairException validationException =
                new UnsupportedCurrencyPairException(LOWERCASE_CURRENCY, EUR, null);
        doThrow(validationException).when(currencyValidator).validateCurrencyPair(LOWERCASE_CURRENCY, EUR);

        UnsupportedCurrencyPairException exception = assertThrows(
                UnsupportedCurrencyPairException.class,
                () -> rateService.getExchangeRate(LOWERCASE_CURRENCY, EUR));

        assertSame(validationException, exception);

        verify(currencyValidator).validateCurrencyPair(LOWERCASE_CURRENCY, EUR);
        verifyNoInteractions(frankfurterFeignClient, rateMapper);
    }

    private FrankfurterRatePairResponse buildFrankfurterRatePairResponse(String base, String quote, BigDecimal rate) {
        return new FrankfurterRatePairResponse(QUOTE_DATE, base, quote, rate);
    }
}
