package zetta.foreignexchange.integration;

import static java.lang.String.format;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import zetta.foreignexchange.common.cache.CacheConfiguration;
import zetta.foreignexchange.common.integrations.frankfurter.FrankfurterFeignClient;
import zetta.foreignexchange.common.integrations.frankfurter.exception.FrankfurterGeneralException;
import zetta.foreignexchange.common.integrations.frankfurter.response.FrankfurterRatePairResponse;
import zetta.foreignexchange.core.constant.IdempotencyConstant;
import zetta.foreignexchange.core.model.ErrorCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public class ConversionIntegrationTest extends BaseIntegrationTestSetUp {

    private static final String CONVERSIONS_URL = "/conversions";
    private static final String CLIENT_BALANCES_URL = "/clients/{clientId}/balances";
    private static final String CLIENT_ID_HEADER = "X-Client-Id";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String USD = "USD";
    private static final String EUR = "EUR";
    private static final String UNKNOWN_CURRENCY = "ZZZ";
    private static final String UNKNOWN_CLIENT_ID = "CLIENT-DOES-NOT-EXIST";
    private static final LocalDate QUOTE_DATE = LocalDate.of(2026, 9, 20);
    private static final BigDecimal PROVIDER_RATE = new BigDecimal("0.86984");
    private static final String PROVIDER_FAILED_MESSAGE = "provider failed";
    private static final String INSUFFICIENT_FUNDS_MESSAGE = "Client %s has insufficient %s balance for this conversion.";
    private static final String CLIENT_NOT_FOUND_MESSAGE = "Client with ID:%s was not found.";
    private static final String BALANCE_NOT_FOUND_MESSAGE = "Client %s has no balance in %s.";
    private static final String IDEMPOTENCY_KEY_CONFLICT_MESSAGE =
            "Client %s already used Idempotency-Key %s for a different conversion request.";
    private static final String SAME_CURRENCY_MESSAGE = "Currency pair %s/%s must contain two different currencies.";
    private static final String UNSUPPORTED_CURRENCY_PAIR_MESSAGE = "Currency pair %s/%s is not supported.";
    private static final String EXCHANGE_RATE_UNAVAILABLE_MESSAGE =
            "Exchange rate for currency pair %s/%s is currently unavailable.";
    private static final String FIELD_ERROR_MESSAGE =
            "Either you submitted a request that is missing a mandatory field or the value of a field does not match " +
                    "the format expected.";

    @MockitoBean
    private FrankfurterFeignClient frankfurterFeignClient;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearRatesCache() {
        Objects.requireNonNull(cacheManager.getCache(CacheConfiguration.EXCHANGE_RATE)).clear();
    }

    @Test
    void createConversion_withSufficientFunds_returnConversionAndUpdatedBalances() throws Exception {
        stubProviderRate(USD, EUR, PROVIDER_RATE);

        ResultActions result =
                createConversion(CLIENT_DEFAULT_ID, null, buildRequestBody(USD, EUR, "100.00"));

        result.andExpect(status().isCreated())
                .andExpectAll(
                        jsonPath("$.transactionId").exists(),
                        jsonPath("$.sourceCurrency").value(USD),
                        jsonPath("$.targetCurrency").value(EUR),
                        jsonPath("$.sourceAmount")
                                .value(comparesEqualTo(new BigDecimal("100.0000")), BigDecimal.class),
                        jsonPath("$.targetAmount")
                                .value(comparesEqualTo(new BigDecimal("86.9840")), BigDecimal.class),
                        jsonPath("$.rate").value(comparesEqualTo(PROVIDER_RATE), BigDecimal.class),
                        jsonPath("$.timestamp").exists(),
                        jsonPath("$.balances[0].currency").value(EUR),
                        jsonPath("$.balances[0].amount")
                                .value(comparesEqualTo(new BigDecimal("1286.9840")), BigDecimal.class),
                        jsonPath("$.balances[1].currency").value(USD),
                        jsonPath("$.balances[1].amount")
                                .value(comparesEqualTo(new BigDecimal("1400.0000")), BigDecimal.class));

        mockMvc.perform(get(CLIENT_BALANCES_URL, CLIENT_DEFAULT_ID).contentType(APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances[0].amount")
                        .value(comparesEqualTo(new BigDecimal("1286.9840")), BigDecimal.class))
                .andExpect(jsonPath("$.balances[1].amount")
                        .value(comparesEqualTo(new BigDecimal("1400.0000")), BigDecimal.class));
    }

    @Test
    void createConversion_withInsufficientFunds_returnUnprocessableContentAndPersistNoConversion() throws Exception {
        stubProviderRate(USD, EUR, PROVIDER_RATE);

        ResultActions result = createConversion(
                CLIENT_DEFAULT_ID, null, buildRequestBody(USD, EUR, "999999.0000"));

        result.andExpect(status().isUnprocessableContent())
                .andExpectAll(
                        jsonPath("$.code").value(ErrorCode.INSUFFICIENT_FUNDS.name()),
                        jsonPath("$.message").value(format(INSUFFICIENT_FUNDS_MESSAGE, CLIENT_DEFAULT_ID, USD)),
                        jsonPath("$.status").value(HttpStatus.UNPROCESSABLE_CONTENT.value()),
                        jsonPath("$.path").value(CONVERSIONS_URL));

        mockMvc.perform(get(CLIENT_BALANCES_URL, CLIENT_DEFAULT_ID).contentType(APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.balances[1].amount")
                        .value(comparesEqualTo(new BigDecimal("1500.0000")), BigDecimal.class));
    }

    @Test
    void createConversion_withUnknownClientId_returnNotFound() throws Exception {
        ResultActions result =
                createConversion(UNKNOWN_CLIENT_ID, null, buildRequestBody(USD, EUR, "10.00"));

        result.andExpect(status().isNotFound())
                .andExpectAll(
                        jsonPath("$.code").value(ErrorCode.CLIENT_NOT_FOUND.name()),
                        jsonPath("$.message").value(format(CLIENT_NOT_FOUND_MESSAGE, UNKNOWN_CLIENT_ID)),
                        jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()),
                        jsonPath("$.path").value(CONVERSIONS_URL));
    }

    @Test
    void createConversion_withSourceCurrencyNotHeldByClient_returnNotFound() throws Exception {
        stubProviderRate(EUR, USD, PROVIDER_RATE);

        ResultActions result = createConversion(CLIENT_WITH_SINGLE_CURRENCY_ID, null,
                buildRequestBody(EUR, USD, "10.00"));

        result.andExpect(status().isNotFound())
                .andExpectAll(
                        jsonPath("$.code").value(ErrorCode.BALANCE_NOT_FOUND.name()),
                        jsonPath("$.message").value(
                                format(BALANCE_NOT_FOUND_MESSAGE, CLIENT_WITH_SINGLE_CURRENCY_ID, EUR)),
                        jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()),
                        jsonPath("$.path").value(CONVERSIONS_URL));
    }

    @Test
    void createConversion_withTargetCurrencyNotHeldByClient_returnNotFound() throws Exception {
        stubProviderRate(USD, EUR, PROVIDER_RATE);

        ResultActions result =
                createConversion(CLIENT_WITH_SINGLE_CURRENCY_ID, null,
                        buildRequestBody(USD, EUR, "10.00"));

        result.andExpect(status().isNotFound())
                .andExpectAll(
                        jsonPath("$.code").value(ErrorCode.BALANCE_NOT_FOUND.name()),
                        jsonPath("$.message").value(
                                format(BALANCE_NOT_FOUND_MESSAGE, CLIENT_WITH_SINGLE_CURRENCY_ID, EUR)),
                        jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()),
                        jsonPath("$.path").value(CONVERSIONS_URL));
    }

    @Test
    void createConversion_withReplayedIdempotencyKey_returnOriginalConversion() throws Exception {
        stubProviderRate(USD, EUR, PROVIDER_RATE);
        String idempotencyKey = "IDEMPOTENCY-KEY-001";

        ResultActions firstResult =
                createConversion(CLIENT_DEFAULT_ID, idempotencyKey, buildRequestBody(USD, EUR, "100.00"));
        firstResult.andExpect(status().isCreated());

        String firstTransactionId = firstResult.andReturn().getResponse()
                .getContentAsString()
                .replaceAll(".*\"transactionId\":\"([^\"]+)\".*", "$1");

        ResultActions secondResult =
                createConversion(CLIENT_DEFAULT_ID, idempotencyKey, buildRequestBody(USD, EUR, "100.00"));

        secondResult.andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId").value(firstTransactionId));

        verify(frankfurterFeignClient).fetchLatestExchangeRates(USD, EUR);
    }

    @Test
    void createConversion_withReplayedIdempotencyKeyForDifferentAmount_returnConflict() throws Exception {
        stubProviderRate(USD, EUR, PROVIDER_RATE);
        String idempotencyKey = "IDEMPOTENCY-KEY-002";

        ResultActions firstResult =
                createConversion(CLIENT_DEFAULT_ID, idempotencyKey, buildRequestBody(USD, EUR, "100.00"));
        firstResult.andExpect(status().isCreated());

        ResultActions secondResult =
                createConversion(CLIENT_DEFAULT_ID, idempotencyKey, buildRequestBody(USD, EUR, "50.00"));

        secondResult.andExpect(status().isConflict())
                .andExpectAll(
                        jsonPath("$.code").value(ErrorCode.IDEMPOTENCY_KEY_CONFLICT.name()),
                        jsonPath("$.message").value(
                                format(IDEMPOTENCY_KEY_CONFLICT_MESSAGE, CLIENT_DEFAULT_ID, idempotencyKey)),
                        jsonPath("$.status").value(HttpStatus.CONFLICT.value()),
                        jsonPath("$.path").value(CONVERSIONS_URL));
    }

    @Test
    void createConversion_withIdenticalCurrencies_returnUnprocessableContent() throws Exception {
        ResultActions result =
                createConversion(CLIENT_DEFAULT_ID, null, buildRequestBody(USD, USD, "10.00"));

        result.andExpect(status().isUnprocessableContent())
                .andExpectAll(
                        jsonPath("$.code").value(ErrorCode.SAME_CURRENCY.name()),
                        jsonPath("$.message").value(format(SAME_CURRENCY_MESSAGE, USD, USD)),
                        jsonPath("$.status").value(HttpStatus.UNPROCESSABLE_CONTENT.value()),
                        jsonPath("$.path").value(CONVERSIONS_URL));
    }

    @Test
    void createConversion_withUnknownCurrencyCode_returnUnsupportedCurrencyPair() throws Exception {
        ResultActions result = createConversion(CLIENT_DEFAULT_ID, null,
                buildRequestBody(USD, UNKNOWN_CURRENCY, "10.00"));

        result.andExpect(status().isUnprocessableContent())
                .andExpectAll(
                        jsonPath("$.code").value(ErrorCode.UNSUPPORTED_CURRENCY_PAIR.name()),
                        jsonPath("$.message").value(
                                format(UNSUPPORTED_CURRENCY_PAIR_MESSAGE, USD, UNKNOWN_CURRENCY)),
                        jsonPath("$.status").value(HttpStatus.UNPROCESSABLE_CONTENT.value()),
                        jsonPath("$.path").value(CONVERSIONS_URL));
    }

    @Test
    void createConversion_whenProviderIsUnavailable_returnBadGateway() throws Exception {
        when(frankfurterFeignClient.fetchLatestExchangeRates(USD, EUR))
                .thenThrow(new FrankfurterGeneralException(PROVIDER_FAILED_MESSAGE));

        ResultActions result =
                createConversion(CLIENT_DEFAULT_ID, null, buildRequestBody(USD, EUR, "10.00"));

        result.andExpect(status().isBadGateway())
                .andExpectAll(
                        jsonPath("$.code").value(ErrorCode.EXCHANGE_RATE_UNAVAILABLE.name()),
                        jsonPath("$.message").value(format(EXCHANGE_RATE_UNAVAILABLE_MESSAGE, USD, EUR)),
                        jsonPath("$.status").value(HttpStatus.BAD_GATEWAY.value()),
                        jsonPath("$.path").value(CONVERSIONS_URL));
    }

    @Test
    void createConversion_withNegativeSourceAmount_returnBadRequest() throws Exception {
        ResultActions result =
                createConversion(CLIENT_DEFAULT_ID, null, buildRequestBody(USD, EUR, "-10.00"));

        result.andExpect(status().isBadRequest())
                .andExpectAll(
                        jsonPath("$.code").value(ErrorCode.FIELD_ERROR.name()),
                        jsonPath("$.message").value(FIELD_ERROR_MESSAGE),
                        jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()),
                        jsonPath("$.path").value(CONVERSIONS_URL));
    }

    @Test
    void createConversion_withZeroSourceAmount_returnBadRequest() throws Exception {
        ResultActions result =
                createConversion(CLIENT_DEFAULT_ID, null, buildRequestBody(USD, EUR, "0"));

        result.andExpect(status().isBadRequest())
                .andExpectAll(
                        jsonPath("$.code").value(ErrorCode.FIELD_ERROR.name()),
                        jsonPath("$.message").value(FIELD_ERROR_MESSAGE),
                        jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()),
                        jsonPath("$.path").value(CONVERSIONS_URL));
    }

    @Test
    void createConversion_withOverScaledSourceAmount_returnBadRequest() throws Exception {
        ResultActions result =
                createConversion(CLIENT_DEFAULT_ID, null, buildRequestBody(USD, EUR, "10.123456"));

        result.andExpect(status().isBadRequest())
                .andExpectAll(
                        jsonPath("$.code").value(ErrorCode.FIELD_ERROR.name()),
                        jsonPath("$.message").value(FIELD_ERROR_MESSAGE),
                        jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()),
                        jsonPath("$.path").value(CONVERSIONS_URL));
    }

    @Test
    void createConversion_withAbsentSourceAmount_returnBadRequest() throws Exception {
        String requestBody = """
                {"sourceCurrency":"%s","targetCurrency":"%s"}
                """.formatted(USD, EUR);

        ResultActions result = createConversion(CLIENT_DEFAULT_ID, null, requestBody);

        result.andExpect(status().isBadRequest())
                .andExpectAll(
                        jsonPath("$.code").value(ErrorCode.FIELD_ERROR.name()),
                        jsonPath("$.message").value(FIELD_ERROR_MESSAGE),
                        jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()),
                        jsonPath("$.path").value(CONVERSIONS_URL));
    }

    @Test
    void createConversion_withOverlongIdempotencyKeyHeader_returnBadRequest() throws Exception {
        String overlongIdempotencyKey = "K".repeat(IdempotencyConstant.MAX_KEY_LENGTH + 1);

        ResultActions result =
                createConversion(CLIENT_DEFAULT_ID, overlongIdempotencyKey, buildRequestBody(USD, EUR, "10.00"));

        result.andExpect(status().isBadRequest());
    }

    @Test
    void createConversion_withBlankClientIdHeader_returnBadRequest() throws Exception {
        ResultActions result =
                createConversion("", null, buildRequestBody(USD, EUR, "10.00"));

        result.andExpect(status().isBadRequest());
    }

    @Test
    void createConversion_withMissingClientIdHeader_returnBadRequest() throws Exception {
        MockHttpServletRequestBuilder requestBuilder = post(CONVERSIONS_URL)
                .contentType(APPLICATION_JSON_VALUE)
                .content(buildRequestBody(USD, EUR, "10.00"));

        mockMvc.perform(requestBuilder).andExpect(status().isBadRequest());
    }

    @Test
    void createConversion_withMalformedJsonBody_returnBadRequest() throws Exception {
        ResultActions result = createConversion(CLIENT_DEFAULT_ID, null, "{not-json");

        result.andExpect(status().isBadRequest());
    }

    private void stubProviderRate(String baseCurrency, String quoteCurrency, BigDecimal rate) {
        FrankfurterRatePairResponse frankfurterRatePairResponse =
                new FrankfurterRatePairResponse(QUOTE_DATE, baseCurrency, quoteCurrency, rate);

        when(frankfurterFeignClient.fetchLatestExchangeRates(baseCurrency, quoteCurrency))
                .thenReturn(frankfurterRatePairResponse);
    }

    private ResultActions createConversion(String clientId, String idempotencyKey, String requestBody) throws Exception {
        MockHttpServletRequestBuilder requestBuilder = post(CONVERSIONS_URL)
                .header(CLIENT_ID_HEADER, clientId)
                .contentType(APPLICATION_JSON_VALUE)
                .content(requestBody);

        if (idempotencyKey != null) {
            requestBuilder.header(IDEMPOTENCY_KEY_HEADER, idempotencyKey);
        }

        return mockMvc.perform(requestBuilder);
    }

    private String buildRequestBody(String sourceCurrency, String targetCurrency, String sourceAmount) {
        return """
                {"sourceCurrency":"%s","targetCurrency":"%s","sourceAmount":%s}
                """.formatted(sourceCurrency, targetCurrency, sourceAmount);
    }
}
