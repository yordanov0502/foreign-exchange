ALTER TABLE conversions RENAME COLUMN source_currency TO base_currency;
ALTER TABLE conversions RENAME COLUMN source_amount TO base_amount;
ALTER TABLE conversions RENAME COLUMN target_currency TO quote_currency;
ALTER TABLE conversions RENAME COLUMN target_amount TO quote_amount;

ALTER TABLE conversions RENAME CONSTRAINT conversions_source_currency_iso TO conversions_base_currency_iso;
ALTER TABLE conversions RENAME CONSTRAINT conversions_target_currency_iso TO conversions_quote_currency_iso;
