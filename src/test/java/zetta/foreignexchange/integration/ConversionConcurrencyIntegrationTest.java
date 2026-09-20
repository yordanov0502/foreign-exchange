package zetta.foreignexchange.integration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import zetta.foreignexchange.common.cache.CacheConfiguration;
import zetta.foreignexchange.common.integrations.frankfurter.FrankfurterFeignClient;
import zetta.foreignexchange.common.integrations.frankfurter.response.FrankfurterRatePairResponse;
import zetta.foreignexchange.persistence.entity.BalanceEntity;
import zetta.foreignexchange.persistence.repository.BalanceRepository;
import zetta.foreignexchange.persistence.repository.ConversionRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Proves the pessimistic-lock concurrency strategy (R1): two genuinely parallel {@code POST /conversions}
 * requests for the same client never double-spend, never lose an update, and never leave a balance
 * negative. Cannot run inside the shared rollback-only test transaction — parallel threads would not see
 * an uncommitted row from the main test thread — so this class commits for real and resets its own
 * dedicated fixture clients in {@link #resetConcurrencyFixtureBalances()}.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class ConversionConcurrencyIntegrationTest extends BaseIntegrationTestSetUp {

    private static final String CONVERSIONS_URL = "/conversions";
    private static final String CLIENT_ID_HEADER = "X-Client-Id";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String USD = "USD";
    private static final String EUR = "EUR";
    private static final LocalDate QUOTE_DATE = LocalDate.of(2026, 9, 20);
    private static final BigDecimal PROVIDER_RATE = new BigDecimal("0.86984");
    private static final int PARALLEL_REQUEST_COUNT = 2;
    private static final int CREATED_STATUS = 201;
    private static final int UNPROCESSABLE_CONTENT_STATUS = 422;

    @MockitoBean
    private FrankfurterFeignClient frankfurterFeignClient;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private BalanceRepository balanceRepository;

    @Autowired
    private ConversionRepository conversionRepository;

    @BeforeEach
    void clearRatesCache() {
        Objects.requireNonNull(cacheManager.getCache(CacheConfiguration.EXCHANGE_RATE)).clear();
    }

    @AfterEach
    void resetConcurrencyFixtureBalances() {
        resetBalance(CLIENT_CONCURRENT_INSUFFICIENT_FUNDS_ID, USD, new BigDecimal("150.0000"));
        resetBalance(CLIENT_CONCURRENT_INSUFFICIENT_FUNDS_ID, EUR, new BigDecimal("1000.0000"));
        resetBalance(CLIENT_CONCURRENT_SUFFICIENT_FUNDS_ID, USD, new BigDecimal("1000.0000"));
        resetBalance(CLIENT_CONCURRENT_SUFFICIENT_FUNDS_ID, EUR, new BigDecimal("1000.0000"));
        resetBalance(CLIENT_CONCURRENT_SHARED_IDEMPOTENCY_KEY_ID, USD, new BigDecimal("500.0000"));
        resetBalance(CLIENT_CONCURRENT_SHARED_IDEMPOTENCY_KEY_ID, EUR, new BigDecimal("500.0000"));
        deleteConversions(CLIENT_CONCURRENT_INSUFFICIENT_FUNDS_ID);
        deleteConversions(CLIENT_CONCURRENT_SUFFICIENT_FUNDS_ID);
        deleteConversions(CLIENT_CONCURRENT_SHARED_IDEMPOTENCY_KEY_ID);
    }

    @Test
    void createConversion_withTwoParallelRequestsExceedingBalance_persistOnlyOneConversion() throws Exception {
        stubProviderRate();
        ConcurrentConversionRequest concurrentRequest = new ConcurrentConversionRequest(
                CLIENT_CONCURRENT_INSUFFICIENT_FUNDS_ID, null, buildRequestBody("100.00"));

        List<MvcResult> results = runConversionsInParallel(concurrentRequest);

        List<Integer> statusCodes = results.stream()
                .map(result -> result.getResponse().getStatus())
                .sorted()
                .toList();
        assertEquals(List.of(CREATED_STATUS, UNPROCESSABLE_CONTENT_STATUS), statusCodes);
        assertThat(findBalance(CLIENT_CONCURRENT_INSUFFICIENT_FUNDS_ID, USD),
                comparesEqualTo(new BigDecimal("50.0000")));
        assertEquals(1, countConversions(CLIENT_CONCURRENT_INSUFFICIENT_FUNDS_ID));
    }

    @Test
    void createConversion_withTwoParallelAffordableRequests_debitBothWithoutLostUpdate() throws Exception {
        stubProviderRate();
        ConcurrentConversionRequest concurrentRequest = new ConcurrentConversionRequest(
                CLIENT_CONCURRENT_SUFFICIENT_FUNDS_ID, null, buildRequestBody("100.00"));

        List<MvcResult> results = runConversionsInParallel(concurrentRequest);

        results.forEach(result -> assertEquals(CREATED_STATUS, result.getResponse().getStatus()));
        assertThat(
                findBalance(CLIENT_CONCURRENT_SUFFICIENT_FUNDS_ID, USD), comparesEqualTo(new BigDecimal("800.0000")));
        assertEquals(2, countConversions(CLIENT_CONCURRENT_SUFFICIENT_FUNDS_ID));
    }

    @Test
    void createConversion_withTwoParallelRequestsSharingIdempotencyKey_persistOnlyOneConversion() throws Exception {
        stubProviderRate();
        String idempotencyKey = "IDEMPOTENCY-KEY-CONCURRENT-001";
        ConcurrentConversionRequest concurrentRequest = new ConcurrentConversionRequest(
                CLIENT_CONCURRENT_SHARED_IDEMPOTENCY_KEY_ID, idempotencyKey, buildRequestBody("100.00"));

        List<MvcResult> results = runConversionsInParallel(concurrentRequest);

        results.forEach(result -> assertEquals(CREATED_STATUS, result.getResponse().getStatus()));
        List<String> transactionIds = results.stream()
                .map(this::extractTransactionId)
                .distinct()
                .toList();
        assertEquals(1, transactionIds.size());
        assertThat(findBalance(CLIENT_CONCURRENT_SHARED_IDEMPOTENCY_KEY_ID, USD),
                comparesEqualTo(new BigDecimal("400.0000")));
        assertEquals(1, countConversions(CLIENT_CONCURRENT_SHARED_IDEMPOTENCY_KEY_ID));
    }

    private void stubProviderRate() {
        when(frankfurterFeignClient.fetchLatestExchangeRates(USD, EUR))
                .thenReturn(new FrankfurterRatePairResponse(QUOTE_DATE, USD, EUR, PROVIDER_RATE));
    }

    /**
     * Fires {@code PARALLEL_REQUEST_COUNT} copies of the same request from separate threads, releasing them
     * all at the same instant via {@code startLatch} so they genuinely race each other, then collects every
     * response before returning.
     */
    private List<MvcResult> runConversionsInParallel(ConcurrentConversionRequest concurrentRequest) throws Exception {

        ExecutorService executorService = Executors.newFixedThreadPool(PARALLEL_REQUEST_COUNT);
        CountDownLatch readyLatch = new CountDownLatch(PARALLEL_REQUEST_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();

        for (int requestIndex = 0; requestIndex < PARALLEL_REQUEST_COUNT; requestIndex++) {
            futures.add(executorService
                    .submit(() -> performSynchronizedConversion(concurrentRequest, readyLatch, startLatch)));
        }
        readyLatch.await();
        startLatch.countDown();

        List<MvcResult> results = new ArrayList<>();
        for (Future<MvcResult> future : futures) {
            results.add(future.get());
        }
        executorService.shutdown();
        return results;
    }

    /**
     * Signals readiness on {@code readyLatch}, then blocks on {@code startLatch} until every other thread is
     * also ready, so the actual {@code POST /conversions} call happens at the same moment across all threads
     * rather than whichever one happened to be scheduled first.
     */
    private MvcResult performSynchronizedConversion(ConcurrentConversionRequest concurrentRequest,
                                                    CountDownLatch readyLatch,
                                                    CountDownLatch startLatch) throws Exception {
        readyLatch.countDown();
        startLatch.await();

        MockHttpServletRequestBuilder requestBuilder = post(CONVERSIONS_URL)
                .header(CLIENT_ID_HEADER, concurrentRequest.clientId())
                .contentType(APPLICATION_JSON_VALUE)
                .content(concurrentRequest.requestBody());

        if (concurrentRequest.idempotencyKey() != null) {
            requestBuilder.header(IDEMPOTENCY_KEY_HEADER, concurrentRequest.idempotencyKey());
        }

        return mockMvc.perform(requestBuilder).andReturn();
    }

    private String extractTransactionId(MvcResult result) {
        try {
            return result.getResponse().getContentAsString()
                    .replaceAll(".*\"transactionId\":\"([^\"]+)\".*", "$1");
        } catch (Exception readResponseBodyException) {
            throw new IllegalStateException(readResponseBodyException);
        }
    }

    private BigDecimal findBalance(String clientId, String currency) {
        return balanceRepository.findByClientClientIdOrderByCurrencyAsc(clientId).stream()
                .filter(balanceEntity -> balanceEntity.getCurrency().equals(currency))
                .findFirst()
                .map(BalanceEntity::getAmount)
                .orElseThrow();
    }

    private int countConversions(String clientId) {
        return (int) conversionRepository.countByClientClientId(clientId);
    }

    private void resetBalance(String clientId, String currency, BigDecimal amount) {
        BalanceEntity balanceEntity = balanceRepository.findByClientClientIdAndCurrency(clientId, currency)
                .orElseThrow();
        BalanceEntity balanceEntityWithNewAmount = balanceEntity
                .toBuilder()
                .amount(amount)
                .build();
        balanceRepository.save(balanceEntityWithNewAmount);
    }

    private void deleteConversions(String clientId) {
        conversionRepository.deleteByClientClientId(clientId);
    }

    private String buildRequestBody(String sourceAmount) {
        return """
                {"sourceCurrency":"%s","targetCurrency":"%s","sourceAmount":%s}
                """.formatted(USD, EUR, sourceAmount);
    }

    private record ConcurrentConversionRequest(String clientId, String idempotencyKey, String requestBody) {
    }
}
