package zetta.foreignexchange.core.processor;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zetta.foreignexchange.core.constant.MoneyConstant;
import zetta.foreignexchange.core.exception.BalanceNotFoundException;
import zetta.foreignexchange.core.exception.ClientNotFoundException;
import zetta.foreignexchange.core.exception.InsufficientFundsException;
import zetta.foreignexchange.core.mapper.BalanceMapper;
import zetta.foreignexchange.core.mapper.ConversionMapper;
import zetta.foreignexchange.core.model.Balance;
import zetta.foreignexchange.core.model.ConversionInput;
import zetta.foreignexchange.core.model.ConversionResult;
import zetta.foreignexchange.core.model.ExchangeRate;
import zetta.foreignexchange.persistence.constant.EntityConstant;
import zetta.foreignexchange.persistence.entity.BalanceEntity;
import zetta.foreignexchange.persistence.entity.ClientEntity;
import zetta.foreignexchange.persistence.entity.ConversionEntity;
import zetta.foreignexchange.persistence.repository.BalanceRepository;
import zetta.foreignexchange.persistence.repository.ClientRepository;
import zetta.foreignexchange.persistence.repository.ConversionRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns the atomic lock -> debit -> credit -> insert unit of work. Kept as a separate bean from
 * {@code ConversionServiceImpl} so {@code @Transactional} applies through a real Spring proxy, and so the
 * rate provider call in the service never happens while a database transaction is open.
 */
@Component
@RequiredArgsConstructor
public class ConversionProcessor {

    private final ClientRepository clientRepository;
    private final BalanceRepository balanceRepository;
    private final ConversionRepository conversionRepository;
    private final ConversionMapper conversionMapper;
    private final BalanceMapper balanceMapper;

    @Transactional
    public ConversionResult processConversion(ConversionInput conversionInput, ExchangeRate exchangeRate) {
        LockedBalances lockedBalances = lockBalances(
                conversionInput.clientId(), conversionInput.baseCurrency(), conversionInput.quoteCurrency());

        BigDecimal requestedBaseAmount = formatAmount(conversionInput.baseAmount());
        validateSufficientFunds(conversionInput.clientId(), lockedBalances.baseBalanceEntity(), requestedBaseAmount);

        ComputedAmounts computedAmounts =
                new ComputedAmounts(requestedBaseAmount, computeQuoteAmount(requestedBaseAmount, exchangeRate.rate()));
        debitAndCreditBalances(lockedBalances, computedAmounts);

        ConversionEntity savedConversionEntity = saveConversion(conversionInput, exchangeRate, computedAmounts);
        List<Balance> updatedBalances = getUpdatedBalances(conversionInput.clientId());

        return new ConversionResult(conversionMapper.mapToConversion(savedConversionEntity), updatedBalances);
    }

    @Transactional(readOnly = true)
    public Optional<ConversionResult> findExistingConversionResult(String clientId, String idempotencyKey) {
        return conversionRepository.findByClientClientIdAndIdempotencyKey(clientId, idempotencyKey)
                .map(this::toConversionResult);
    }

    private List<Balance> getUpdatedBalances(String clientId) {
        List<BalanceEntity> updatedBalanceEntities = balanceRepository.findByClientClientIdOrderByCurrencyAsc(clientId);
        return balanceMapper.mapToBalances(updatedBalanceEntities);
    }

    private BigDecimal formatAmount(BigDecimal amount) {
        return amount.setScale(EntityConstant.MONEY_SCALE, MoneyConstant.ROUNDING_MODE);
    }

    private ConversionResult toConversionResult(ConversionEntity conversionEntity) {
        List<Balance> updatedBalances = balanceMapper.mapToBalances(
                balanceRepository.findByClientClientIdOrderByCurrencyAsc(conversionEntity.getClient().getClientId()));
        return new ConversionResult(conversionMapper.mapToConversion(conversionEntity), updatedBalances);
    }

    /** Postgres does not guarantee ORDER BY determines lock acquisition order, so the two rows are
     * locked via two sequential calls in ascending currency-code order, regardless of which is the
     * base or the quote, to avoid a cross-pair deadlock (e.g. USD->EUR racing EUR->USD).
     */
    private LockedBalances lockBalances(String clientId, String baseCurrency, String quoteCurrency) {
        boolean baseCurrencyLocksFirst = baseCurrency.compareTo(quoteCurrency) < 0;
        String firstCurrency = baseCurrencyLocksFirst ? baseCurrency : quoteCurrency;
        String secondCurrency = baseCurrencyLocksFirst ? quoteCurrency : baseCurrency;

        BalanceEntity firstBalanceEntity = findAndLockBalance(clientId, firstCurrency);
        BalanceEntity secondBalanceEntity = findAndLockBalance(clientId, secondCurrency);

        return baseCurrencyLocksFirst
                ? new LockedBalances(firstBalanceEntity, secondBalanceEntity)
                : new LockedBalances(secondBalanceEntity, firstBalanceEntity);
    }

    private BalanceEntity findAndLockBalance(String clientId, String currency) {
        return balanceRepository.findAndLockByClientClientIdAndCurrency(clientId, currency)
                .orElseThrow(() -> new BalanceNotFoundException(clientId, currency));
    }

    private void debitAndCreditBalances(LockedBalances lockedBalances, ComputedAmounts computedAmounts) {
        lockedBalances.baseBalanceEntity().debit(computedAmounts.baseAmount());
        lockedBalances.quoteBalanceEntity().credit(computedAmounts.quoteAmount());
    }

    private void validateSufficientFunds(String clientId, BalanceEntity baseBalanceEntity, BigDecimal requestedBaseAmount) {
        BigDecimal currentBaseAmount = baseBalanceEntity.getAmount();
        if (currentBaseAmount.compareTo(requestedBaseAmount) < 0) {
            throw new InsufficientFundsException(clientId, baseBalanceEntity.getCurrency());
        }
    }

    private BigDecimal computeQuoteAmount(BigDecimal baseAmount, BigDecimal rate) {
        return baseAmount.multiply(rate).setScale(EntityConstant.MONEY_SCALE, MoneyConstant.ROUNDING_MODE);
    }

    private ConversionEntity saveConversion(
            ConversionInput conversionInput, ExchangeRate exchangeRate, ComputedAmounts computedAmounts) {

        ClientEntity clientEntity = getClientEntity(conversionInput.clientId());

        ConversionEntity conversionEntity = ConversionEntity.builder()
                .transactionId(UUID.randomUUID())
                .client(clientEntity)
                .baseCurrency(conversionInput.baseCurrency())
                .baseAmount(computedAmounts.baseAmount())
                .quoteCurrency(conversionInput.quoteCurrency())
                .quoteAmount(computedAmounts.quoteAmount())
                .rate(exchangeRate.rate())
                .idempotencyKey(conversionInput.idempotencyKey())
                .build();

        return conversionRepository.saveAndFlush(conversionEntity);
    }

    private ClientEntity getClientEntity(String clientId) {
        return clientRepository.findByClientId(clientId)
                .orElseThrow(() -> new ClientNotFoundException(clientId));
    }

    private record LockedBalances(BalanceEntity baseBalanceEntity, BalanceEntity quoteBalanceEntity) {
    }

    private record ComputedAmounts(BigDecimal baseAmount, BigDecimal quoteAmount) {
    }

}
