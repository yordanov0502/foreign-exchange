package zetta.foreignexchange.core.constant;

import zetta.foreignexchange.persistence.constant.EntityConstant;

import java.math.RoundingMode;

public final class MoneyConstant {

    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    public static final int MAX_FRACTION_DIGITS = EntityConstant.MONEY_SCALE;
    public static final int MAX_INTEGER_DIGITS = EntityConstant.MONEY_PRECISION - MAX_FRACTION_DIGITS;

    private MoneyConstant() {}
}
