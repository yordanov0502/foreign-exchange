package zetta.foreignexchange.core.model;

import java.math.BigDecimal;

public record ConversionInput(
        String clientId,
        String idempotencyKey,
        String baseCurrency,
        String quoteCurrency,
        BigDecimal baseAmount) {
}
