package zetta.foreignexchange.common.integrations.frankfurter.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FrankfurterRatePairResponse(LocalDate date,
                                          String base,
                                          String quote,
                                          BigDecimal rate) {
}
