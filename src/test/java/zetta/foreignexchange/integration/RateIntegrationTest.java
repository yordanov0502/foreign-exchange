package zetta.foreignexchange.integration;

import static java.lang.String.format;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import zetta.foreignexchange.common.cache.CacheConfiguration;
import zetta.foreignexchange.common.integrations.frankfurter.FrankfurterFeignClient;
import zetta.foreignexchange.common.integrations.frankfurter.exception.FrankfurterGeneralException;
import zetta.foreignexchange.common.integrations.frankfurter.exception.FrankfurterPairNotQuotableException;
import zetta.foreignexchange.common.integrations.frankfurter.response.FrankfurterRatePairResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Full-stack tests for {@code GET /rates}. Everything below the provider is real — controller, service,
 * cache, advice and JSON serialisation — while {@link FrankfurterFeignClient} is replaced by a mock so the
 * suite never reaches the network.
 */
@Transactional
public class RateIntegrationTest extends BaseIntegrationTestSetUp {

    private static final String GET_RATES_URL = "/rates";
    private static final String FROM_PARAMETER = "from";
    private static final String TO_PARAMETER = "to";
    private static final String USD = "USD";
    private static final String EUR = "EUR";
    private static final String GBP = "GBP";
    private static final String UNKNOWN_CURRENCY = "XXX";
    private static final LocalDate QUOTE_DATE = LocalDate.of(2026, 9, 20);
    private static final BigDecimal PROVIDER_RATE = new BigDecimal("0.86984");
    private static final BigDecimal SCALED_RATE = new BigDecimal("0.86984000");
    private static final String EXCHANGE_RATE_UNAVAILABLE_CODE = "EXCHANGE_RATE_UNAVAILABLE";
    private static final String EXCHANGE_RATE_UNAVAILABLE_MESSAGE =
            "Exchange rate for currency pair %s/%s is currently unavailable.";
    private static final String UNSUPPORTED_CURRENCY_PAIR_CODE = "UNSUPPORTED_CURRENCY_PAIR";
    private static final String UNSUPPORTED_CURRENCY_PAIR_MESSAGE = "Currency pair %s/%s is not supported.";
    private static final String PROVIDER_FAILED_MESSAGE = "provider failed";

    @MockitoBean
    private FrankfurterFeignClient frankfurterFeignClient;

    @Autowired
    private CacheManager cacheManager;

    /**
     * Clears shared cache before each test so cached values from previous tests
     * do not bypass mocked provider calls.
     */
    @BeforeEach
    void clearRatesCache() {
        Objects.requireNonNull(cacheManager.getCache(CacheConfiguration.EXCHANGE_RATE)).clear();
    }

    @Test
    void getExchangeRate_withKnownCurrencyPair_returnRate() throws Exception {
        when(frankfurterFeignClient.fetchLatestExchangeRates(USD, EUR))
                .thenReturn(buildRatePairResponse(USD, EUR, PROVIDER_RATE));

        ResultActions result = getRate(USD, EUR);

        result.andExpect(status().isOk())
                .andExpectAll(
                        jsonPath("$.date").value(QUOTE_DATE.toString()),
                        jsonPath("$.baseCurrency").value(USD),
                        jsonPath("$.quoteCurrency").value(EUR),
                        jsonPath("$.rate").value(comparesEqualTo(SCALED_RATE), BigDecimal.class));

        verify(frankfurterFeignClient).fetchLatestExchangeRates(USD, EUR);
    }

    @Test
    void getExchangeRate_withSecondCallInsideTtl_serveFromCacheWithoutCallingProvider() throws Exception {
        when(frankfurterFeignClient.fetchLatestExchangeRates(USD, EUR))
                .thenReturn(buildRatePairResponse(USD, EUR, PROVIDER_RATE));

        getRate(USD, EUR).andExpect(status().isOk());
        getRate(USD, EUR).andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value(comparesEqualTo(SCALED_RATE), BigDecimal.class));

        verify(frankfurterFeignClient).fetchLatestExchangeRates(USD, EUR);
    }

    @Test
    void getExchangeRate_withDifferentCurrencyPair_callProviderAgain() throws Exception {
        when(frankfurterFeignClient.fetchLatestExchangeRates(USD, EUR))
                .thenReturn(buildRatePairResponse(USD, EUR, PROVIDER_RATE));
        when(frankfurterFeignClient.fetchLatestExchangeRates(USD, GBP))
                .thenReturn(buildRatePairResponse(USD, GBP, new BigDecimal("0.74123")));

        getRate(USD, EUR).andExpect(status().isOk());
        getRate(USD, GBP).andExpect(status().isOk())
                .andExpect(jsonPath("$.quoteCurrency").value(GBP));

        verify(frankfurterFeignClient).fetchLatestExchangeRates(USD, EUR);
        verify(frankfurterFeignClient).fetchLatestExchangeRates(USD, GBP);
    }

    @Test
    void getExchangeRate_withIdenticalCurrencyPair_returnRateOfOne() throws Exception {
        when(frankfurterFeignClient.fetchLatestExchangeRates(USD, USD))
                .thenReturn(buildRatePairResponse(USD, USD, BigDecimal.ONE));

        ResultActions result = getRate(USD, USD);

        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value(comparesEqualTo(BigDecimal.ONE), BigDecimal.class));
    }

    @Test
    void getExchangeRate_whenProviderIsUnavailable_returnRateUnavailable() throws Exception {
        when(frankfurterFeignClient.fetchLatestExchangeRates(USD, EUR))
                .thenThrow(new FrankfurterGeneralException(PROVIDER_FAILED_MESSAGE));

        ResultActions result = getRate(USD, EUR);

        result.andExpect(status().isBadGateway())
                .andExpectAll(
                        jsonPath("$.code").value(EXCHANGE_RATE_UNAVAILABLE_CODE),
                        jsonPath("$.message").value(format(EXCHANGE_RATE_UNAVAILABLE_MESSAGE, USD, EUR)),
                        jsonPath("$.status").value(HttpStatus.BAD_GATEWAY.value()),
                        jsonPath("$.path").value(GET_RATES_URL));
    }

    @Test
    void getExchangeRate_whenProviderRejectsCurrencyPair_returnUnsupportedCurrencyPair() throws Exception {
        when(frankfurterFeignClient.fetchLatestExchangeRates(USD, UNKNOWN_CURRENCY))
                .thenThrow(new FrankfurterPairNotQuotableException(PROVIDER_FAILED_MESSAGE));

        ResultActions result = getRate(USD, UNKNOWN_CURRENCY);

        result.andExpect(status().isUnprocessableContent())
                .andExpectAll(
                        jsonPath("$.code").value(UNSUPPORTED_CURRENCY_PAIR_CODE),
                        jsonPath("$.message").value(
                                format(UNSUPPORTED_CURRENCY_PAIR_MESSAGE, USD, UNKNOWN_CURRENCY)),
                        jsonPath("$.status").value(HttpStatus.UNPROCESSABLE_CONTENT.value()),
                        jsonPath("$.path").value(GET_RATES_URL));
    }

    @Test
    void getExchangeRate_withMalformedCurrencyCode_returnUnsupportedCurrencyPairWithoutCallingProvider() throws Exception {
        ResultActions result = getRate("us", EUR);

        result.andExpect(status().isUnprocessableContent())
                .andExpectAll(
                        jsonPath("$.code").value(UNSUPPORTED_CURRENCY_PAIR_CODE),
                        jsonPath("$.message").value(format(UNSUPPORTED_CURRENCY_PAIR_MESSAGE, "us", EUR)),
                        jsonPath("$.status").value(HttpStatus.UNPROCESSABLE_CONTENT.value()),
                        jsonPath("$.path").value(GET_RATES_URL));

        verifyNoInteractions(frankfurterFeignClient);
    }

    @Test
    void getExchangeRate_whenProviderFailsRepeatedly_doNotCacheTheFailure() throws Exception {
        when(frankfurterFeignClient.fetchLatestExchangeRates(USD, EUR))
                .thenThrow(new FrankfurterGeneralException(PROVIDER_FAILED_MESSAGE));

        getRate(USD, EUR).andExpect(status().isBadGateway());
        getRate(USD, EUR).andExpect(status().isBadGateway());

        verify(frankfurterFeignClient, times(2)).fetchLatestExchangeRates(USD, EUR);
    }

    @Test
    void getExchangeRate_withoutRequiredParameters_returnBadRequest() throws Exception {
        mockMvc.perform(get(GET_RATES_URL).contentType(APPLICATION_JSON_VALUE))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(frankfurterFeignClient);
    }

    private FrankfurterRatePairResponse buildRatePairResponse(String base, String quote, BigDecimal rate) {
        return new FrankfurterRatePairResponse(QUOTE_DATE, base, quote, rate);
    }

    private ResultActions getRate(String sourceCurrency, String targetCurrency) throws Exception {
        return mockMvc.perform(get(GET_RATES_URL)
                .param(FROM_PARAMETER, sourceCurrency)
                .param(TO_PARAMETER, targetCurrency)
                .contentType(APPLICATION_JSON_VALUE));
    }
}
