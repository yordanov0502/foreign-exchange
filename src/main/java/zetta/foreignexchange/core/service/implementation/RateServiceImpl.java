package zetta.foreignexchange.core.service.implementation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import zetta.foreignexchange.common.cache.CacheConfiguration;
import zetta.foreignexchange.common.integrations.frankfurter.FrankfurterFeignClient;
import zetta.foreignexchange.common.integrations.frankfurter.exception.FrankfurterGeneralException;
import zetta.foreignexchange.common.integrations.frankfurter.exception.FrankfurterPairNotQuotableException;
import zetta.foreignexchange.common.integrations.frankfurter.response.FrankfurterRatePairResponse;
import zetta.foreignexchange.core.exception.ExchangeRateUnavailableException;
import zetta.foreignexchange.core.exception.UnsupportedCurrencyPairException;
import zetta.foreignexchange.core.mapper.RateMapper;
import zetta.foreignexchange.core.model.ExchangeRate;
import zetta.foreignexchange.core.service.RateService;
import zetta.foreignexchange.core.validator.CurrencyValidator;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateServiceImpl implements RateService {

    private final CurrencyValidator currencyValidator;
    private final FrankfurterFeignClient frankfurterFeignClient;
    private final RateMapper rateMapper;

    @Override
    @Cacheable(cacheNames = CacheConfiguration.EXCHANGE_RATE, key = "#baseCurrency + '-' + #quoteCurrency", sync = true)
    public ExchangeRate getExchangeRate(String baseCurrency, String quoteCurrency) {
        currencyValidator.validateCurrencyPair(baseCurrency, quoteCurrency);
        FrankfurterRatePairResponse frankfurterRatePairResponse = fetchExchangeRate(baseCurrency, quoteCurrency);
        currencyValidator.validateCurrencyPairsMatch(baseCurrency, quoteCurrency, frankfurterRatePairResponse);
        return rateMapper.mapToRate(frankfurterRatePairResponse);
    }

    private FrankfurterRatePairResponse fetchExchangeRate(String baseCurrency, String quoteCurrency) {
        log.debug("Fetching live exchange rate from Frankfurter for {}/{}", baseCurrency, quoteCurrency);
        FrankfurterRatePairResponse frankfurterRatePairResponse;
        try {
            frankfurterRatePairResponse = frankfurterFeignClient.fetchLatestExchangeRates(baseCurrency, quoteCurrency);
            if (hasAnyNullValue(frankfurterRatePairResponse)) {
                throw new ExchangeRateUnavailableException(baseCurrency, quoteCurrency, null);
            }
        } catch (FrankfurterPairNotQuotableException notQuotableException) {
            throw new UnsupportedCurrencyPairException(baseCurrency, quoteCurrency, notQuotableException);
        } catch (FrankfurterGeneralException generalException) {
            throw new ExchangeRateUnavailableException(baseCurrency, quoteCurrency, generalException);
        }
        return frankfurterRatePairResponse;
    }

    private boolean hasAnyNullValue(FrankfurterRatePairResponse frankfurterRatePairResponse) {
        if (frankfurterRatePairResponse == null) {
            return true;
        }
        return frankfurterRatePairResponse.date() == null
                || frankfurterRatePairResponse.base() == null
                || frankfurterRatePairResponse.quote() == null
                || frankfurterRatePairResponse.rate() == null;
    }
}
