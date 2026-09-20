package zetta.foreignexchange.rest.model;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.UUID;

@ParameterObject
@Schema(description = "Filters for GET /conversions. At least one of the three must be supplied.")
public record ConversionHistoryFilterRequest(
        @Parameter(description = "Match a single conversion by its transaction identifier.")
        UUID transactionId,

        @Parameter(description = "Match every conversion recorded on this UTC calendar day.", example = "2020-01-15")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate date,

        @Parameter(description = "Match every conversion belonging to this clientId.", example = "CLIENT-001")
        String clientId) {
}
