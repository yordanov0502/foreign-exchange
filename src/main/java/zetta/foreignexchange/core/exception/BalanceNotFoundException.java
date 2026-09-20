package zetta.foreignexchange.core.exception;

import lombok.Getter;

/**
 * The client exists but holds no balance row for the requested currency.
 */
@Getter
public class BalanceNotFoundException extends RuntimeException {

    private final String clientId;
    private final String currency;

    public BalanceNotFoundException(String clientId, String currency) {
        this.clientId = clientId;
        this.currency = currency;
    }
}
