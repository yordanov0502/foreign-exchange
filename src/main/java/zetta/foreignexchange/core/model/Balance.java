package zetta.foreignexchange.core.model;

import java.math.BigDecimal;

public record Balance(String currency, BigDecimal amount) {
}
