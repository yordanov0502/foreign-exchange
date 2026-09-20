package zetta.foreignexchange.persistence.entity;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

class BalanceEntityTest {

    private static final String USD = "USD";
    private static final BigDecimal STARTING_AMOUNT = new BigDecimal("100.0000");

    @Test
    void debit_withAmountBelowBalance_reduceAmount() {
        BalanceEntity balanceEntity = buildBalanceEntity(STARTING_AMOUNT);

        balanceEntity.debit(new BigDecimal("40.0000"));

        assertThat(balanceEntity.getAmount(), comparesEqualTo(new BigDecimal("60.0000")));
    }

    @Test
    void debit_withAmountEqualToBalance_leaveZeroAmount() {
        BalanceEntity balanceEntity = buildBalanceEntity(STARTING_AMOUNT);

        balanceEntity.debit(STARTING_AMOUNT);

        assertThat(balanceEntity.getAmount(), comparesEqualTo(BigDecimal.ZERO));
    }

    @Test
    void debit_withAmountAboveBalance_throwIllegalStateException() {
        BalanceEntity balanceEntity = buildBalanceEntity(STARTING_AMOUNT);

        assertThrows(IllegalStateException.class, () -> balanceEntity.debit(new BigDecimal("100.0001")));

        assertEquals(0, STARTING_AMOUNT.compareTo(balanceEntity.getAmount()));
    }

    @Test
    void credit_withAmount_increaseAmount() {
        BalanceEntity balanceEntity = buildBalanceEntity(STARTING_AMOUNT);

        balanceEntity.credit(new BigDecimal("25.5000"));

        assertThat(balanceEntity.getAmount(), comparesEqualTo(new BigDecimal("125.5000")));
    }

    private BalanceEntity buildBalanceEntity(BigDecimal amount) {
        return BalanceEntity.builder()
                .currency(USD)
                .amount(amount)
                .build();
    }
}
