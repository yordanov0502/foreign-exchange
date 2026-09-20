package zetta.foreignexchange.core.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import zetta.foreignexchange.core.model.Conversion;
import zetta.foreignexchange.persistence.entity.ConversionEntity;

@Mapper(componentModel = "spring")
public interface ConversionMapper {

    @Mapping(target = "timestamp", source = "createdAt")
    Conversion mapToConversion(ConversionEntity conversionEntity);
}
