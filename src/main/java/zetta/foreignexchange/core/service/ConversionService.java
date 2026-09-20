package zetta.foreignexchange.core.service;

import zetta.foreignexchange.core.model.ConversionInput;
import zetta.foreignexchange.core.model.ConversionResult;

public interface ConversionService {

    ConversionResult convert(ConversionInput conversionInput);
}
