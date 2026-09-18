package zetta.foreignexchange.core.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import zetta.foreignexchange.core.model.Balance;
import zetta.foreignexchange.persistence.entity.BalanceEntity;

import java.math.BigDecimal;
import java.util.List;

class BalanceMapperTest {

    private static final String EUR = "EUR";
    private static final String USD = "USD";
    private static final BigDecimal EIGHT_THOUSAND = new BigDecimal("8000.0000");
    private static final BigDecimal TEN_THOUSAND = new BigDecimal("10000.0000");

    private final BalanceMapper balanceMapper = Mappers.getMapper(BalanceMapper.class);

    @Test
    void mapToBalance_balanceEntity_shouldMapCurrencyAndAmount() {
        BalanceEntity balanceEntity = buildBalanceEntity(USD, TEN_THOUSAND);

        Balance balance = balanceMapper.mapToBalance(balanceEntity);

        assertNotNull(balance);
        assertEquals(USD, balance.currency());
        assertEquals(TEN_THOUSAND, balance.amount());
    }

    @Test
    void mapToBalance_nullBalanceEntity_shouldReturnNull() {
        assertNull(balanceMapper.mapToBalance(null));
    }

    @Test
    void mapToBalances_multipleBalanceEntities_shouldMapAllPreservingOrder() {
        List<BalanceEntity> balanceEntities =
                List.of(buildBalanceEntity(EUR, EIGHT_THOUSAND), buildBalanceEntity(USD, TEN_THOUSAND));

        List<Balance> balances = balanceMapper.mapToBalances(balanceEntities);

        assertNotNull(balances);
        assertEquals(balanceEntities.size(), balances.size());
        assertEquals(EUR, balances.getFirst().currency());
        assertEquals(EIGHT_THOUSAND, balances.getFirst().amount());
        assertEquals(USD, balances.getLast().currency());
        assertEquals(TEN_THOUSAND, balances.getLast().amount());
    }

    @Test
    void mapToBalances_emptyBalanceEntities_shouldReturnEmptyList() {
        List<Balance> balances = balanceMapper.mapToBalances(List.of());

        assertNotNull(balances);
        assertTrue(balances.isEmpty());
    }

    private BalanceEntity buildBalanceEntity(String currency, BigDecimal amount) {
        return BalanceEntity.builder()
                .currency(currency)
                .amount(amount)
                .build();
    }
}
