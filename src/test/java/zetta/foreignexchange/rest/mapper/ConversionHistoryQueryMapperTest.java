package zetta.foreignexchange.rest.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import zetta.foreignexchange.core.model.ConversionHistoryQuery;
import zetta.foreignexchange.rest.model.ConversionHistoryFilterRequest;

import java.time.LocalDate;
import java.util.UUID;

class ConversionHistoryQueryMapperTest {

    private static final UUID TRANSACTION_ID = UUID.randomUUID();
    private static final LocalDate DATE = LocalDate.of(2020, 1, 15);
    private static final String CLIENT_ID = "CLIENT-001";
    private static final int PAGE = 2;
    private static final int SIZE = 50;

    private final ConversionHistoryQueryMapper conversionHistoryQueryMapper =
            Mappers.getMapper(ConversionHistoryQueryMapper.class);

    @Test
    void mapToConversionHistoryQuery_withAllFilters_mapEveryField() {
        ConversionHistoryFilterRequest conversionHistoryFilterRequest =
                new ConversionHistoryFilterRequest(TRANSACTION_ID, DATE, CLIENT_ID);

        ConversionHistoryQuery conversionHistoryQuery = conversionHistoryQueryMapper.mapToConversionHistoryQuery(
                conversionHistoryFilterRequest, PAGE, SIZE);

        assertNotNull(conversionHistoryQuery);
        assertEquals(TRANSACTION_ID, conversionHistoryQuery.transactionId());
        assertEquals(DATE, conversionHistoryQuery.date());
        assertEquals(CLIENT_ID, conversionHistoryQuery.clientId());
        assertEquals(PAGE, conversionHistoryQuery.page());
        assertEquals(SIZE, conversionHistoryQuery.size());
    }
}
