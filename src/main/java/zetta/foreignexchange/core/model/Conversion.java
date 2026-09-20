package zetta.foreignexchange.core.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record Conversion(
        UUID transactionId,
        String clientId,
        String baseCurrency,
        BigDecimal baseAmount,
        String quoteCurrency,
        BigDecimal quoteAmount,
        BigDecimal rate,
        OffsetDateTime timestamp) {
}
