package zetta.foreignexchange.rest.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "The current exchange rate for a currency pair.")
public record ExchangeRateResponse(
        @Schema(description = "Date the provider published this quote.", example = "2026-09-20")
        LocalDate date,
        @Schema(description = "Currency being converted from.", example = "USD")
        String baseCurrency,
        @Schema(description = "Currency being converted to.", example = "EUR")
        String quoteCurrency,
        @Schema(description = "Units of target currency per one unit of source currency.", example = "0.86984")
        BigDecimal rate) {
}
