package zetta.foreignexchange.rest.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import zetta.foreignexchange.core.constant.CurrencyConstant;
import zetta.foreignexchange.core.constant.MoneyConstant;

import java.math.BigDecimal;

@Schema(description = "A request to convert an amount from one currency to another for a client.")
public record ConversionRequest(
        @JsonProperty("sourceCurrency")
        @NotBlank
        @Pattern(regexp = CurrencyConstant.CURRENCY_CODE_PATTERN)
        @Schema(description = "Currency being converted from.", example = "USD")
        String baseCurrency,

        @JsonProperty("targetCurrency")
        @NotBlank
        @Pattern(regexp = CurrencyConstant.CURRENCY_CODE_PATTERN)
        @Schema(description = "Currency being converted to.", example = "EUR")
        String quoteCurrency,

        @JsonProperty("sourceAmount")
        @NotNull
        @Positive
        @Digits(integer = MoneyConstant.MAX_INTEGER_DIGITS, fraction = MoneyConstant.MAX_FRACTION_DIGITS)
        @Schema(description = "Amount of source currency to convert.", example = "100.00")
        BigDecimal baseAmount) {
}
