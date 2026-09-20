package zetta.foreignexchange.rest.mapper;

import org.mapstruct.Mapper;
import zetta.foreignexchange.core.model.ConversionHistoryQuery;
import zetta.foreignexchange.rest.model.ConversionHistoryFilterRequest;

@Mapper(componentModel = "spring")
public interface ConversionHistoryQueryMapper {

    ConversionHistoryQuery mapToConversionHistoryQuery(
            ConversionHistoryFilterRequest conversionHistoryFilterRequest, int page, int size);
}
