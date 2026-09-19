package zetta.foreignexchange.common.cache;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import zetta.foreignexchange.common.integrations.frankfurter.FrankfurterFeignClient;
import zetta.foreignexchange.common.integrations.frankfurter.response.FrankfurterRatePairResponse;
import zetta.foreignexchange.integration.BaseIntegrationTestSetUp;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;

/**
 * Proves the TTL actually <em>expires</em>, which {@code RateIntegrationTest} cannot: a cache configured with
 * {@code expireAfterAccess}, or one that never received the configured TTL at all, would still satisfy every
 * "second call is served from cache" assertion.
 *
 * <p>Runs in its own context with a deliberately tiny TTL, so this class starts a second application context.
 */
@Transactional
@TestPropertySource(properties = "cache.currency-rate-pair.ttl=100ms")
public class RateCacheExpiryTest extends BaseIntegrationTestSetUp {

    private static final String GET_RATES = "/rates";
    private static final String FROM_PARAMETER = "from";
    private static final String TO_PARAMETER = "to";
    private static final String USD = "USD";
    private static final String EUR = "EUR";
    private static final LocalDate QUOTE_DATE = LocalDate.of(2026, 9, 20);
    private static final BigDecimal PROVIDER_RATE = new BigDecimal("0.86984");
    private static final Duration CACHE_TTL = Duration.ofMillis(100);
    private static final Duration WAIT_PAST_TTL = CACHE_TTL.multipliedBy(4);

    @MockitoBean
    private FrankfurterFeignClient frankfurterFeignClient;

    @Test
    void getRate_afterTtlExpires_callProviderAgain() throws Exception {
        when(frankfurterFeignClient.fetchLatestExchangeRates(USD, EUR))
                .thenReturn(new FrankfurterRatePairResponse(QUOTE_DATE, USD, EUR, PROVIDER_RATE));

        getRate().andExpect(status().isOk());
        getRate().andExpect(status().isOk());

        verify(frankfurterFeignClient).fetchLatestExchangeRates(USD, EUR);

        Thread.sleep(WAIT_PAST_TTL.toMillis());

        getRate().andExpect(status().isOk());

        verify(frankfurterFeignClient, times(2)).fetchLatestExchangeRates(USD, EUR);
    }

    private ResultActions getRate() throws Exception {
        return mockMvc.perform(get(GET_RATES)
                .param(FROM_PARAMETER, USD)
                .param(TO_PARAMETER, EUR)
                .contentType(APPLICATION_JSON_VALUE));
    }
}
