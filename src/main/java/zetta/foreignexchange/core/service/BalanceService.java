package zetta.foreignexchange.core.service;

import zetta.foreignexchange.core.model.Balance;

import java.util.List;

public interface BalanceService {

    List<Balance> getClientBalances(String clientId);
}
