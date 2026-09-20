package zetta.foreignexchange.core.exception;

import lombok.Getter;

/**
 * The client's balance in the source currency is lower than the amount requested for conversion.
 */
@Getter
public class InsufficientFundsException extends RuntimeException {

    private final String clientId;
    private final String currency;

    public InsufficientFundsException(String clientId, String currency) {
        this.clientId = clientId;
        this.currency = currency;
    }
}
