package zetta.foreignexchange.core.model;

import java.util.List;

public record ConversionResult(Conversion conversion,
                               List<Balance> updatedBalances) {
}
