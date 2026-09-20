-- Fixture for GET /conversions history tests. Uses dedicated clients and deliberately old, fixed
-- timestamps so it is invisible to, and unaffected by, every other test class (ConversionIntegrationTest
-- and ConversionConcurrencyIntegrationTest both write with created_at = now()).
--
-- CLIENT-TEST-HISTORY-001 has three conversions: two on 2020-01-15 (08:00Z and 09:30Z) and one at exactly
-- 2020-01-16T00:00:00Z, which proves the [date, date+1) range's exclusive upper bound.
-- CLIENT-TEST-HISTORY-002 has one conversion on 2020-01-15, so a clientId+date filter can prove the AND.
INSERT INTO clients (client_id, created_at)
VALUES
    ('CLIENT-TEST-HISTORY-001', now()),
    ('CLIENT-TEST-HISTORY-002', now());

INSERT INTO conversions (
    transaction_id, client_id, base_currency, base_amount, quote_currency, quote_amount, rate,
    idempotency_key, created_at)
SELECT v.transaction_id, c.id, v.base_currency, v.base_amount, v.quote_currency, v.quote_amount, v.rate,
       v.idempotency_key, v.created_at
FROM clients c
JOIN (VALUES
    ('CLIENT-TEST-HISTORY-001', '11111111-1111-1111-1111-111111111111'::uuid, 'USD', 100.0000::numeric,
        'EUR', 86.9840::numeric, 0.86984::numeric, 'HISTORY-001-A', '2020-01-15T08:00:00Z'::timestamptz),
    ('CLIENT-TEST-HISTORY-001', '22222222-2222-2222-2222-222222222222'::uuid, 'USD', 200.0000::numeric,
        'EUR', 173.9680::numeric, 0.86984::numeric, 'HISTORY-001-B', '2020-01-15T09:30:00Z'::timestamptz),
    ('CLIENT-TEST-HISTORY-001', '33333333-3333-3333-3333-333333333333'::uuid, 'EUR', 50.0000::numeric,
        'USD', 57.4800::numeric, 1.14960::numeric, 'HISTORY-001-C', '2020-01-16T00:00:00Z'::timestamptz),
    ('CLIENT-TEST-HISTORY-002', '44444444-4444-4444-4444-444444444444'::uuid, 'USD', 300.0000::numeric,
        'EUR', 260.9520::numeric, 0.86984::numeric, 'HISTORY-002-A', '2020-01-15T10:00:00Z'::timestamptz)
) AS v(client_id, transaction_id, base_currency, base_amount, quote_currency, quote_amount, rate,
       idempotency_key, created_at)
ON v.client_id = c.client_id;
