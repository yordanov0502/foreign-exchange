package zetta.foreignexchange.rest.mapper;

import org.mapstruct.Mapper;
import zetta.foreignexchange.core.model.ConversionInput;
import zetta.foreignexchange.rest.model.ConversionRequest;

@Mapper(componentModel = "spring")
public interface ConversionRequestMapper {

    ConversionInput mapToConversionInput(String clientId, String idempotencyKey, ConversionRequest conversionRequest);
}
