package zetta.foreignexchange.core.exception;

import lombok.Getter;

/**
 * The base and quote currency of the pair are identical; a currency cannot be converted into itself.
 */
@Getter
public class SameCurrencyException extends RuntimeException {

    private final String baseCurrency;
    private final String quoteCurrency;

    public SameCurrencyException(String baseCurrency, String quoteCurrency) {
        this.baseCurrency = baseCurrency;
        this.quoteCurrency = quoteCurrency;
    }
}
