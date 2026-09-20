package zetta.foreignexchange.rest.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "The result of converting an amount from one currency to another for a client.")
public record ConversionResponse(
        @Schema(description = "Identifier assigned to this conversion.")
        UUID transactionId,

        @JsonProperty("sourceCurrency")
        @Schema(description = "Currency converted from.", example = "USD")
        String baseCurrency,

        @JsonProperty("sourceAmount")
        @Schema(description = "Amount of source currency converted.", example = "100.00")
        BigDecimal baseAmount,

        @JsonProperty("targetCurrency")
        @Schema(description = "Currency converted to.", example = "EUR")
        String quoteCurrency,

        @JsonProperty("targetAmount")
        @Schema(description = "Amount of target currency credited.", example = "86.9840")
        BigDecimal quoteAmount,

        @Schema(description = "Exchange rate applied to this conversion.", example = "0.86984")
        BigDecimal rate,

        @Schema(description = "Instant the conversion was recorded.")
        OffsetDateTime timestamp,

        @Schema(description = "The client's balances after this conversion.")
        List<BalanceResponse> balances) {
}
