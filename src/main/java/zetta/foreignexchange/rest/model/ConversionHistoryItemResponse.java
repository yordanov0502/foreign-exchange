package zetta.foreignexchange.rest.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "One previously executed conversion.")
public record ConversionHistoryItemResponse(
        @Schema(description = "Identifier assigned to this conversion.")
        UUID transactionId,

        @Schema(description = "Client the conversion belongs to.", example = "CLIENT-001")
        String clientId,

        @JsonProperty("sourceCurrency")
        @Schema(description = "Currency converted from.", example = "USD")
        String baseCurrency,

        @JsonProperty("sourceAmount")
        @Schema(description = "Amount of source currency converted.", example = "100.0000")
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
        OffsetDateTime timestamp) {
}
