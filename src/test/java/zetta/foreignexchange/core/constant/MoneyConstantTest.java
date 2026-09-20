package zetta.foreignexchange.core.constant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import zetta.foreignexchange.persistence.constant.EntityConstant;

class MoneyConstantTest {

    @Test
    void shouldKeepFractionDigitsAlignedWithPersistedMoneyScale() {
        assertThat(MoneyConstant.MAX_FRACTION_DIGITS).isEqualTo(EntityConstant.MONEY_SCALE);
    }

    @Test
    void shouldKeepIntegerDigitsWithinPersistedMoneyPrecision() {
        assertThat(MoneyConstant.MAX_INTEGER_DIGITS + MoneyConstant.MAX_FRACTION_DIGITS)
                .isEqualTo(EntityConstant.MONEY_PRECISION);
    }
}
