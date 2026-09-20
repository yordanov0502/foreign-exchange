package zetta.foreignexchange.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared set-up for full-stack tests: boots the application against a Postgres container, runs the Flyway
 * migrations on it, and exposes {@link MockMvc} so subclasses can call the real endpoints.
 *
 * <p>The container is defined as a {@code @ServiceConnection} bean, so Spring reuses one context — and one
 * container — across every test class extending this one.
 *
 * <p>The {@code test} profile adds {@code classpath:db/testdata} to the Flyway locations, so the
 * integration-test fixtures in {@code V900} and {@code V901} are applied on top of the production migrations. Each test runs
 * in a transaction that is rolled back afterwards, so those fixtures — and the seeded demo clients — stay
 * intact for every other test in the run.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
public abstract class BaseIntegrationTestSetUp {

    protected static final String CLIENT_DEFAULT_ID = "CLIENT-TEST-001";
    protected static final String CLIENT_WITHOUT_BALANCES_ID = "CLIENT-TEST-002";
    protected static final String CLIENT_WITH_SINGLE_CURRENCY_ID = "CLIENT-TEST-003";
    protected static final String CLIENT_CONCURRENT_INSUFFICIENT_FUNDS_ID = "CLIENT-TEST-CONCURRENCY-001";
    protected static final String CLIENT_CONCURRENT_SUFFICIENT_FUNDS_ID = "CLIENT-TEST-CONCURRENCY-002";
    protected static final String CLIENT_CONCURRENT_SHARED_IDEMPOTENCY_KEY_ID = "CLIENT-TEST-CONCURRENCY-003";
    protected static final String CLIENT_TEST_HISTORY_FIRST_ID = "CLIENT-TEST-HISTORY-001";
    protected static final String CLIENT_TEST_HISTORY_SECOND_ID = "CLIENT-TEST-HISTORY-002";

    @Autowired
    protected MockMvc mockMvc;
}
