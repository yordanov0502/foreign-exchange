package zetta.foreignexchange.core.service;

import org.springframework.data.domain.Page;
import zetta.foreignexchange.core.model.Conversion;
import zetta.foreignexchange.core.model.ConversionHistoryQuery;
import zetta.foreignexchange.core.model.ConversionInput;
import zetta.foreignexchange.core.model.ConversionResult;

public interface ConversionService {

    ConversionResult convert(ConversionInput conversionInput);

    Page<Conversion> getConversionHistory(ConversionHistoryQuery conversionHistoryQuery);
}
