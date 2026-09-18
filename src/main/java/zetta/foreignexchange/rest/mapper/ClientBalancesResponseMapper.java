package zetta.foreignexchange.rest.mapper;

import org.mapstruct.Mapper;
import zetta.foreignexchange.core.model.Balance;
import zetta.foreignexchange.rest.model.BalanceResponse;
import zetta.foreignexchange.rest.model.ClientBalancesResponse;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ClientBalancesResponseMapper {

    BalanceResponse mapToBalanceResponse(Balance balance);

    List<BalanceResponse> mapToBalanceResponses(List<Balance> balances);

    ClientBalancesResponse mapToClientBalancesResponse(String clientId, List<Balance> balances);
}
