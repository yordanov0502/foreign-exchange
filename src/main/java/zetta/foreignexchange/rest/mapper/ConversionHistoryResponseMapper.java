package zetta.foreignexchange.rest.mapper;

import org.mapstruct.Mapper;
import org.springframework.data.domain.Page;
import zetta.foreignexchange.core.model.Conversion;
import zetta.foreignexchange.rest.model.ConversionHistoryItemResponse;
import zetta.foreignexchange.rest.model.ConversionHistoryResponse;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ConversionHistoryResponseMapper {

    ConversionHistoryItemResponse mapToConversionHistoryItemResponse(Conversion conversion);

    default ConversionHistoryResponse mapToConversionHistoryResponse(Page<Conversion> conversionPage) {
        List<ConversionHistoryItemResponse> content = conversionPage.getContent().stream()
                .map(this::mapToConversionHistoryItemResponse)
                .toList();

        return new ConversionHistoryResponse(
                content,
                conversionPage.getNumber(),
                conversionPage.getSize(),
                conversionPage.getTotalElements(),
                conversionPage.getTotalPages());
    }
}
