-- Integration-test fixtures. Versioned above the production migrations so they always apply last,
-- and kept out of src/main so the shipped schema never carries test data.
--
-- CLIENT-TEST-001 holds two currencies, deliberately inserted USD-before-EUR so that a response
-- ordered by currency proves the ORDER BY rather than the insertion order.
-- CLIENT-TEST-002 exists but holds no currency — the "known client, empty balances" case.
INSERT INTO clients (client_id, created_at)
VALUES
    ('CLIENT-TEST-001', now()),
    ('CLIENT-TEST-002', now());

INSERT INTO balances (client_id, currency, amount, created_at, updated_at)
SELECT c.id, v.currency, v.amount, now(), now()
FROM clients c
JOIN (VALUES
    ('CLIENT-TEST-001', 'USD', 1500.0000),
    ('CLIENT-TEST-001', 'EUR', 1200.0000)
) AS v(client_id, currency, amount) ON v.client_id = c.client_id;
