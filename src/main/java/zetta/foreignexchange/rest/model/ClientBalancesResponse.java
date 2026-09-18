package zetta.foreignexchange.rest.model;

import java.util.List;

public record ClientBalancesResponse(String clientId, List<BalanceResponse> balances) {
}
