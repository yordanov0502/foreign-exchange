-- Integration-test fixtures. Versioned above the production migrations so they always apply last,
-- and kept out of src/main so the shipped schema never carries test data.
--
-- CLIENT-TEST-001 holds two currencies, deliberately inserted USD-before-EUR so that a response
-- ordered by currency proves the ORDER BY rather than the insertion order. Also used as the
-- POST /conversions happy-path client (Seq 4).
-- CLIENT-TEST-002 exists but holds no currency — the "known client, empty balances" case.
-- CLIENT-TEST-003 holds only USD — drives the "known client, currency not held" / BALANCE_NOT_FOUND
-- case for POST /conversions (converting into or out of EUR, which it does not hold).
--
-- CLIENT-TEST-CONCURRENCY-001/002/003 are used ONLY by ConversionConcurrencyIntegrationTest, which
-- commits real transactions (it cannot run inside the shared rollback-only test transaction) and
-- resets their balances itself in @AfterEach, so no other test may read them.
INSERT INTO clients (client_id, created_at)
VALUES
    ('CLIENT-TEST-001', now()),
    ('CLIENT-TEST-002', now()),
    ('CLIENT-TEST-003', now()),
    ('CLIENT-TEST-CONCURRENCY-001', now()),
    ('CLIENT-TEST-CONCURRENCY-002', now()),
    ('CLIENT-TEST-CONCURRENCY-003', now());

INSERT INTO balances (client_id, currency, amount, created_at, updated_at)
SELECT c.id, v.currency, v.amount, now(), now()
FROM clients c
JOIN (VALUES
    ('CLIENT-TEST-001', 'USD', 1500.0000),
    ('CLIENT-TEST-001', 'EUR', 1200.0000),
    ('CLIENT-TEST-003', 'USD', 500.0000),
    ('CLIENT-TEST-CONCURRENCY-001', 'USD', 150.0000),
    ('CLIENT-TEST-CONCURRENCY-001', 'EUR', 1000.0000),
    ('CLIENT-TEST-CONCURRENCY-002', 'USD', 1000.0000),
    ('CLIENT-TEST-CONCURRENCY-002', 'EUR', 1000.0000),
    ('CLIENT-TEST-CONCURRENCY-003', 'USD', 500.0000),
    ('CLIENT-TEST-CONCURRENCY-003', 'EUR', 500.0000)
) AS v(client_id, currency, amount) ON v.client_id = c.client_id;
