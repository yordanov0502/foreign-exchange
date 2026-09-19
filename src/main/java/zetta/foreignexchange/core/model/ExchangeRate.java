package zetta.foreignexchange.core.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExchangeRate(LocalDate date,
                           String baseCurrency,
                           String quoteCurrency,
                           BigDecimal rate) {
}
