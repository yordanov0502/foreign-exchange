package zetta.foreignexchange.rest.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "A client's balance in a single currency.")
public record BalanceResponse(
        @Schema(description = "ISO-4217 currency code.", example = "EUR")
        String currency,
        @Schema(description = "Units of currency the client currently holds.", example = "8000")
        BigDecimal amount) {
}
