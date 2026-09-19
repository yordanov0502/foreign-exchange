package zetta.foreignexchange.rest.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import zetta.foreignexchange.core.model.ExchangeRate;
import zetta.foreignexchange.rest.model.ExchangeRateResponse;

@Mapper(componentModel = "spring")
public interface ExchangeRateResponseMapper {

    @Mapping(target = "baseCurrency", source = "baseCurrency")
    @Mapping(target = "quoteCurrency", source = "quoteCurrency")
    ExchangeRateResponse mapToExchangeRateResponse(ExchangeRate exchangeRate);
}
