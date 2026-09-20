package zetta.foreignexchange.rest.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import zetta.foreignexchange.core.model.ConversionResult;
import zetta.foreignexchange.rest.model.ConversionResponse;

@Mapper(componentModel = "spring", uses = ClientBalancesResponseMapper.class)
public interface ConversionResponseMapper {

    @Mapping(target = "transactionId", source = "conversion.transactionId")
    @Mapping(target = "baseCurrency", source = "conversion.baseCurrency")
    @Mapping(target = "baseAmount", source = "conversion.baseAmount")
    @Mapping(target = "quoteCurrency", source = "conversion.quoteCurrency")
    @Mapping(target = "quoteAmount", source = "conversion.quoteAmount")
    @Mapping(target = "rate", source = "conversion.rate")
    @Mapping(target = "timestamp", source = "conversion.timestamp")
    @Mapping(target = "balances", source = "updatedBalances")
    ConversionResponse mapToConversionResponse(ConversionResult conversionResult);
}
