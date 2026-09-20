package zetta.foreignexchange.rest.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import zetta.foreignexchange.core.model.Conversion;
import zetta.foreignexchange.rest.model.ConversionHistoryResponse;

import java.util.List;

class ConversionHistoryResponseMapperTest {

    private static final int PAGE_NUMBER = 0;
    private static final int PAGE_SIZE = 20;

    private final ConversionHistoryResponseMapper conversionHistoryResponseMapper =
            Mappers.getMapper(ConversionHistoryResponseMapper.class);

    @Test
    void mapToConversionHistoryResponse_withPopulatedPage_mapContentAndPaginationMetadata() {
        Conversion conversion = Instancio.create(Conversion.class);
        Page<Conversion> conversionPage =
                new PageImpl<>(List.of(conversion), PageRequest.of(PAGE_NUMBER, PAGE_SIZE), 1);

        ConversionHistoryResponse conversionHistoryResponse =
                conversionHistoryResponseMapper.mapToConversionHistoryResponse(conversionPage);

        assertNotNull(conversionHistoryResponse);
        assertEquals(1, conversionHistoryResponse.content().size());
        assertEquals(conversion.transactionId(), conversionHistoryResponse.content().getFirst().transactionId());
        assertEquals(conversion.clientId(), conversionHistoryResponse.content().getFirst().clientId());
        assertEquals(conversion.baseCurrency(), conversionHistoryResponse.content().getFirst().baseCurrency());
        assertEquals(conversion.baseAmount(), conversionHistoryResponse.content().getFirst().baseAmount());
        assertEquals(conversion.quoteCurrency(), conversionHistoryResponse.content().getFirst().quoteCurrency());
        assertEquals(conversion.quoteAmount(), conversionHistoryResponse.content().getFirst().quoteAmount());
        assertEquals(conversion.rate(), conversionHistoryResponse.content().getFirst().rate());
        assertEquals(conversion.timestamp(), conversionHistoryResponse.content().getFirst().timestamp());
        assertEquals(PAGE_NUMBER, conversionHistoryResponse.page());
        assertEquals(PAGE_SIZE, conversionHistoryResponse.size());
        assertEquals(1, conversionHistoryResponse.totalElements());
        assertEquals(1, conversionHistoryResponse.totalPages());
    }

    @Test
    void mapToConversionHistoryResponse_withEmptyPage_returnEmptyContent() {
        Page<Conversion> emptyConversionPage = new PageImpl<>(List.of(), PageRequest.of(PAGE_NUMBER, PAGE_SIZE), 0);

        ConversionHistoryResponse conversionHistoryResponse =
                conversionHistoryResponseMapper.mapToConversionHistoryResponse(emptyConversionPage);

        assertNotNull(conversionHistoryResponse);
        assertTrue(conversionHistoryResponse.content().isEmpty());
        assertEquals(0, conversionHistoryResponse.totalElements());
        assertEquals(0, conversionHistoryResponse.totalPages());
    }
}
