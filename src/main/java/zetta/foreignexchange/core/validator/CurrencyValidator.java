package zetta.foreignexchange.core.validator;

import org.springframework.stereotype.Component;
import zetta.foreignexchange.common.integrations.frankfurter.response.FrankfurterRatePairResponse;
import zetta.foreignexchange.core.constant.CurrencyConstant;
import zetta.foreignexchange.core.exception.ExchangeRateUnavailableException;
import zetta.foreignexchange.core.exception.SameCurrencyException;
import zetta.foreignexchange.core.exception.UnsupportedCurrencyPairException;

import java.util.Currency;
import java.util.Objects;
import java.util.regex.Pattern;

@Component
public class CurrencyValidator {

    private static final Pattern CURRENCY_CODE_PATTERN = Pattern.compile(CurrencyConstant.CURRENCY_CODE_PATTERN);

    public void validateCurrencyPair(String baseCurrency, String quoteCurrency) {
        validateRegEx(baseCurrency, quoteCurrency);
        validateCurrencyCodesAreSupported(baseCurrency, quoteCurrency);
        validateCurrenciesAreDifferent(baseCurrency, quoteCurrency);
    }

    public void validateCurrencyPairsMatch(
            String baseCurrency, String quoteCurrency, FrankfurterRatePairResponse frankfurterRatePairResponse) {

        if (!Objects.equals(baseCurrency, frankfurterRatePairResponse.base())
                || !Objects.equals(quoteCurrency, frankfurterRatePairResponse.quote())) {
            throw new ExchangeRateUnavailableException(baseCurrency, quoteCurrency, null);
        }
    }

    private void validateCurrenciesAreDifferent(String baseCurrency, String quoteCurrency) {
        if (Objects.equals(baseCurrency, quoteCurrency)) {
            throw new SameCurrencyException(baseCurrency, quoteCurrency);
        }
    }

    private void validateCurrencyCodesAreSupported(String baseCurrency, String quoteCurrency) {
        try {
            Currency.getInstance(baseCurrency);
            Currency.getInstance(quoteCurrency);
        } catch (IllegalArgumentException exception) {
            throw new UnsupportedCurrencyPairException(baseCurrency, quoteCurrency, exception);
        }
    }

    private void validateRegEx(String baseCurrency, String quoteCurrency) {
        if (isInvalidCurrencyCode(baseCurrency) || isInvalidCurrencyCode(quoteCurrency)) {
            throw new UnsupportedCurrencyPairException(baseCurrency, quoteCurrency, null);
        }
    }

    private boolean isInvalidCurrencyCode(String currency) {
        return currency == null || !CURRENCY_CODE_PATTERN.matcher(currency).matches();
    }

}
