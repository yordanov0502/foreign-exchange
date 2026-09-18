INSERT INTO clients (client_id, created_at)
VALUES
    ('CLIENT-001', now()),
    ('CLIENT-002', now());

INSERT INTO balances (client_id, currency, amount, created_at, updated_at)
SELECT c.id, v.currency, v.amount, now(), now()
FROM clients c
JOIN (VALUES
    ('CLIENT-001', 'USD', 10000.0000),
    ('CLIENT-001', 'EUR', 8000.0000),
    ('CLIENT-002', 'GBP', 5000.0000)
) AS v(client_id, currency, amount) ON v.client_id = c.client_id;
