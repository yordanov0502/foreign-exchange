package zetta.foreignexchange.core.exception;

import lombok.Getter;

/**
 * No usable rate could be obtained for the pair: the provider timed out, was unreachable, failed or
 * answered with something the current service cannot read or in some way is wrong.
 */
@Getter
public class ExchangeRateUnavailableException extends RuntimeException {

    private final String baseCurrency;
    private final String quoteCurrency;

    public ExchangeRateUnavailableException(String baseCurrency, String quoteCurrency, Throwable cause) {
        super(cause);
        this.baseCurrency = baseCurrency;
        this.quoteCurrency = quoteCurrency;
    }
}
