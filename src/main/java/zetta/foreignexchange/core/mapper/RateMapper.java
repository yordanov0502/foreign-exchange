package zetta.foreignexchange.core.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import zetta.foreignexchange.common.integrations.frankfurter.response.FrankfurterRatePairResponse;
import zetta.foreignexchange.core.model.ExchangeRate;
import zetta.foreignexchange.persistence.constant.EntityConstant;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Mapper(componentModel = "spring")
public interface RateMapper {

    @Mapping(target = "baseCurrency", source = "base")
    @Mapping(target = "quoteCurrency", source = "quote")
    @Mapping(target = "rate", source = "rate", qualifiedByName = "scaleRate")
    ExchangeRate mapToRate(FrankfurterRatePairResponse frankfurterRatePairResponse);

    @Named("scaleRate")
    default BigDecimal scaleRate(BigDecimal rate) {
        return rate == null ? null : rate.setScale(EntityConstant.RATE_SCALE, RoundingMode.HALF_UP);
    }
}
