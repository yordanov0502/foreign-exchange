package zetta.foreignexchange.core.model;

import java.time.LocalDate;
import java.util.UUID;

public record ConversionHistoryQuery(
        UUID transactionId,
        LocalDate date,
        String clientId,
        int page,
        int size) {
}
