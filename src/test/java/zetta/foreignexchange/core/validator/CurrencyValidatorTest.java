package zetta.foreignexchange.core.validator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import zetta.foreignexchange.common.integrations.frankfurter.response.FrankfurterRatePairResponse;
import zetta.foreignexchange.core.exception.ExchangeRateUnavailableException;
import zetta.foreignexchange.core.exception.SameCurrencyException;
import zetta.foreignexchange.core.exception.UnsupportedCurrencyPairException;

import java.math.BigDecimal;
import java.time.LocalDate;

class CurrencyValidatorTest {

    private static final String USD = "USD";
    private static final String EUR = "EUR";
    private static final String GBP = "GBP";
    private static final String LOWERCASE_CURRENCY = "usd";
    private static final String WRONG_LENGTH_CURRENCY = "US";
    private static final String UNRECOGNIZED_CURRENCY = "ZZZ";
    private static final LocalDate QUOTE_DATE = LocalDate.of(2026, 9, 20);
    private static final BigDecimal PROVIDER_RATE = new BigDecimal("0.86984");

    private final CurrencyValidator currencyValidator = new CurrencyValidator();

    @Test
    void validateCurrencyPair_withRecognizedCurrencies_doNotThrowException() {
        assertDoesNotThrow(() -> currencyValidator.validateCurrencyPair(USD, EUR));
    }

    @Test
    void validateCurrencyPair_withLowercaseBaseCurrency_throwUnsupportedCurrencyPairException() {
        UnsupportedCurrencyPairException exception = assertThrows(
                UnsupportedCurrencyPairException.class,
                () -> currencyValidator.validateCurrencyPair(LOWERCASE_CURRENCY, EUR));

        assertEquals(LOWERCASE_CURRENCY, exception.getBaseCurrency());
        assertEquals(EUR, exception.getQuoteCurrency());
    }

    @Test
    void validateCurrencyPair_withLowercaseQuoteCurrency_throwUnsupportedCurrencyPairException() {
        UnsupportedCurrencyPairException exception = assertThrows(
                UnsupportedCurrencyPairException.class,
                () -> currencyValidator.validateCurrencyPair(USD, LOWERCASE_CURRENCY));

        assertEquals(USD, exception.getBaseCurrency());
        assertEquals(LOWERCASE_CURRENCY, exception.getQuoteCurrency());
    }

    @Test
    void validateCurrencyPair_withWrongLengthCurrency_throwUnsupportedCurrencyPairException() {
        assertThrows(UnsupportedCurrencyPairException.class,
                () -> currencyValidator.validateCurrencyPair(WRONG_LENGTH_CURRENCY, EUR));
    }

    @Test
    void validateCurrencyPair_withNullCurrency_throwUnsupportedCurrencyPairException() {
        assertThrows(UnsupportedCurrencyPairException.class, () -> currencyValidator.validateCurrencyPair(USD, null));
    }

    @Test
    void validateCurrencyPair_withUnrecognizedCurrency_throwUnsupportedCurrencyPairException() {
        UnsupportedCurrencyPairException exception = assertThrows(
                UnsupportedCurrencyPairException.class,
                () -> currencyValidator.validateCurrencyPair(USD, UNRECOGNIZED_CURRENCY));

        assertEquals(USD, exception.getBaseCurrency());
        assertEquals(UNRECOGNIZED_CURRENCY, exception.getQuoteCurrency());
    }

    @Test
    void validateCurrencyPair_withIdenticalCurrencies_throwSameCurrencyException() {
        SameCurrencyException exception = assertThrows(
                SameCurrencyException.class,
                () -> currencyValidator.validateCurrencyPair(USD, USD));

        assertEquals(USD, exception.getBaseCurrency());
        assertEquals(USD, exception.getQuoteCurrency());
    }

    @Test
    void validateCurrencyPair_withLowercaseIdenticalCurrencies_throwUnsupportedCurrencyPairException() {
        assertThrows(UnsupportedCurrencyPairException.class,
                () -> currencyValidator.validateCurrencyPair(LOWERCASE_CURRENCY, LOWERCASE_CURRENCY));
    }

    @Test
    void validateCurrencyPairsMatch_withMatchingPair_doNotThrowException() {
        FrankfurterRatePairResponse frankfurterRatePairResponse = buildRatePairResponse(USD, EUR);

        assertDoesNotThrow(() -> currencyValidator.validateCurrencyPairsMatch(USD, EUR, frankfurterRatePairResponse));
    }

    @Test
    void validateCurrencyPairsMatch_withMismatchedBase_throwExchangeRateUnavailableException() {
        FrankfurterRatePairResponse frankfurterRatePairResponse = buildRatePairResponse(GBP, EUR);

        ExchangeRateUnavailableException exception = assertThrows(
                ExchangeRateUnavailableException.class,
                () -> currencyValidator.validateCurrencyPairsMatch(USD, EUR, frankfurterRatePairResponse));

        assertEquals(USD, exception.getBaseCurrency());
        assertEquals(EUR, exception.getQuoteCurrency());
    }

    @Test
    void validateCurrencyPairsMatch_withMismatchedQuote_throwExchangeRateUnavailableException() {
        FrankfurterRatePairResponse frankfurterRatePairResponse = buildRatePairResponse(USD, GBP);

        assertThrows(ExchangeRateUnavailableException.class,
                () -> currencyValidator.validateCurrencyPairsMatch(USD, EUR, frankfurterRatePairResponse));
    }

    private FrankfurterRatePairResponse buildRatePairResponse(String base, String quote) {
        return new FrankfurterRatePairResponse(QUOTE_DATE, base, quote, PROVIDER_RATE);
    }
}
