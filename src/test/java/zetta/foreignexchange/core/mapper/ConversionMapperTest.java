package zetta.foreignexchange.core.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import zetta.foreignexchange.core.model.Conversion;
import zetta.foreignexchange.persistence.entity.ClientEntity;
import zetta.foreignexchange.persistence.entity.ConversionEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

class ConversionMapperTest {

    private static final String USD = "USD";
    private static final String EUR = "EUR";
    private static final String CLIENT_ID = "CLIENT-001";
    private static final BigDecimal BASE_AMOUNT = new BigDecimal("100.0000");
    private static final BigDecimal QUOTE_AMOUNT = new BigDecimal("86.9840");
    private static final BigDecimal RATE = new BigDecimal("0.86984");
    private static final UUID TRANSACTION_ID = UUID.randomUUID();
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.now();

    private final ConversionMapper conversionMapper = Mappers.getMapper(ConversionMapper.class);

    @Test
    void mapToConversion_withConversionEntity_mapEveryField() {
        ClientEntity clientEntity = ClientEntity.builder()
                .clientId(CLIENT_ID)
                .build();
        ConversionEntity conversionEntity = ConversionEntity.builder()
                .transactionId(TRANSACTION_ID)
                .client(clientEntity)
                .baseCurrency(USD)
                .baseAmount(BASE_AMOUNT)
                .quoteCurrency(EUR)
                .quoteAmount(QUOTE_AMOUNT)
                .rate(RATE)
                .createdAt(CREATED_AT)
                .build();

        Conversion conversion = conversionMapper.mapToConversion(conversionEntity);

        assertNotNull(conversion);
        assertEquals(TRANSACTION_ID, conversion.transactionId());
        assertEquals(CLIENT_ID, conversion.clientId());
        assertEquals(USD, conversion.baseCurrency());
        assertEquals(BASE_AMOUNT, conversion.baseAmount());
        assertEquals(EUR, conversion.quoteCurrency());
        assertEquals(QUOTE_AMOUNT, conversion.quoteAmount());
        assertEquals(RATE, conversion.rate());
        assertEquals(CREATED_AT, conversion.timestamp());
    }

    @Test
    void mapToConversion_withNullConversionEntity_returnNull() {
        assertNull(conversionMapper.mapToConversion(null));
    }
}
