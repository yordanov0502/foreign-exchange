package zetta.foreignexchange.core.exception;

import lombok.Getter;

/**
 * The currency pair cannot be quoted: a code is not a valid ISO-4217 code or the provider will not quote
 * the pair. The caller's input is the problem, not the provider.
 */
@Getter
public class UnsupportedCurrencyPairException extends RuntimeException {

    private final String baseCurrency;
    private final String quoteCurrency;

    public UnsupportedCurrencyPairException(String baseCurrency, String quoteCurrency, Throwable cause) {
        super(cause);
        this.baseCurrency = baseCurrency;
        this.quoteCurrency = quoteCurrency;
    }
}
