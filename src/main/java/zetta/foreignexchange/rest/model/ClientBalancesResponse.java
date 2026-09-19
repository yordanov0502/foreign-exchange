package zetta.foreignexchange.rest.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "A client's current balances across all currencies they hold.")
public record ClientBalancesResponse(
        @Schema(description = "Caller-supplied client identifier.", example = "CLIENT-001")
        String clientId,
        @Schema(description = "One balance per currency the client holds, ordered by currency code.")
        List<BalanceResponse> balances) {
}
