package zetta.foreignexchange.core.service;

import zetta.foreignexchange.core.model.ExchangeRate;

public interface RateService {

    ExchangeRate getExchangeRate(String baseCurrency, String quoteCurrency);
}
