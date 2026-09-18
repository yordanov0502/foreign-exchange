package zetta.foreignexchange.rest.model;

import java.math.BigDecimal;

public record BalanceResponse(String currency, BigDecimal amount) {
}
