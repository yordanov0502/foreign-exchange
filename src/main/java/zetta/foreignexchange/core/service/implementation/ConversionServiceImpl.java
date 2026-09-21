package zetta.foreignexchange.core.service.implementation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zetta.foreignexchange.core.exception.ClientNotFoundException;
import zetta.foreignexchange.core.exception.ConversionFilterRequiredException;
import zetta.foreignexchange.core.exception.IdempotencyKeyConflictException;
import zetta.foreignexchange.core.mapper.ConversionMapper;
import zetta.foreignexchange.core.model.Conversion;
import zetta.foreignexchange.core.model.ConversionHistoryQuery;
import zetta.foreignexchange.core.model.ConversionInput;
import zetta.foreignexchange.core.model.ConversionResult;
import zetta.foreignexchange.core.model.ExchangeRate;
import zetta.foreignexchange.core.processor.ConversionProcessor;
import zetta.foreignexchange.core.service.ConversionService;
import zetta.foreignexchange.core.service.RateService;
import zetta.foreignexchange.core.validator.CurrencyValidator;
import zetta.foreignexchange.persistence.constant.ConversionAttributeConstant;
import zetta.foreignexchange.persistence.entity.ConversionEntity;
import zetta.foreignexchange.persistence.repository.ClientRepository;
import zetta.foreignexchange.persistence.repository.ConversionRepository;
import zetta.foreignexchange.persistence.specification.ConversionSpecification;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
class ConversionServiceImpl implements ConversionService {

    private static final Sort CONVERSION_HISTORY_SORT = Sort
            .by(Sort.Direction.DESC, ConversionAttributeConstant.CREATED_AT)
            .and(Sort.by(Sort.Direction.DESC, ConversionAttributeConstant.ID));

    private final CurrencyValidator currencyValidator;
    private final ClientRepository clientRepository;
    private final RateService rateService;
    private final ConversionProcessor conversionProcessor;
    private final ConversionRepository conversionRepository;
    private final ConversionMapper conversionMapper;

    @Override
    public ConversionResult convert(ConversionInput conversionInput) {
        currencyValidator.validateCurrencyPair(conversionInput.baseCurrency(), conversionInput.quoteCurrency());
        validateClientExists(conversionInput.clientId());

        Optional<ConversionResult> existingConversionResult = findExistingConversionResult(conversionInput);
        if (existingConversionResult.isPresent()) {
            return existingConversionResult.get();
        }

        ExchangeRate exchangeRate = rateService.getExchangeRate(
                conversionInput.baseCurrency(), conversionInput.quoteCurrency());

        return processConversion(conversionInput, exchangeRate);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Conversion> getConversionHistory(ConversionHistoryQuery conversionHistoryQuery) {
        validateAtLeastOneFilterSupplied(conversionHistoryQuery);

        Specification<ConversionEntity> conversionSpecification = buildConversionSpecification(conversionHistoryQuery);
        Pageable pageable =
                PageRequest.of(conversionHistoryQuery.page(), conversionHistoryQuery.size(), CONVERSION_HISTORY_SORT);

        Page<ConversionEntity> conversionEntityPage = conversionRepository.findAll(conversionSpecification, pageable);
        return conversionEntityPage.map(conversionMapper::mapToConversion);
    }

    /**
     * Two concurrent requests carrying the same idempotency key can both pass the earlier existence check
     * before either has persisted. The loser then fails the {@code (clientId, idempotencyKey)} unique
     * constraint on insert; instead of surfacing that as a DB error, its result is recovered here by
     * re-reading the row the winner just persisted.
     */
    private ConversionResult processConversion(ConversionInput conversionInput, ExchangeRate exchangeRate) {
        try {
            return conversionProcessor.processConversion(conversionInput, exchangeRate);
        } catch (DataIntegrityViolationException duplicateIdempotencyKeyException) {
            log.warn("Idempotency key race detected: clientId={}, idempotencyKey={}; recovering winner's result",
                    conversionInput.clientId(),
                    conversionInput.idempotencyKey());
            return findExistingConversionResult(conversionInput)
                    .orElseThrow(() -> duplicateIdempotencyKeyException);
        }
    }

    private Optional<ConversionResult> findExistingConversionResult(ConversionInput conversionInput) {
        if (conversionInput.idempotencyKey() == null) {
            return Optional.empty();
        }

        Optional<ConversionResult> existingConversionResult = conversionProcessor.findExistingConversionResult(
                conversionInput.clientId(), conversionInput.idempotencyKey());
        existingConversionResult.ifPresent(
                conversionResult -> validateIdempotencyKeyReuse(conversionInput, conversionResult));

        return existingConversionResult;
    }

    /**
     * The {@code (clientId, idempotencyKey)} pair only proves this request has been seen before, not that
     * it is the same request. A caller reusing an idempotency key for a genuinely different conversion
     * (different currencies or amount) must not silently receive back someone else's result.
     */
    private void validateIdempotencyKeyReuse(ConversionInput conversionInput, ConversionResult conversionResult) {
        Conversion existingConversion = conversionResult.conversion();
        boolean isSameRequest = existingConversion.baseCurrency().equals(conversionInput.baseCurrency())
                && existingConversion.quoteCurrency().equals(conversionInput.quoteCurrency())
                && existingConversion.baseAmount().compareTo(conversionInput.baseAmount()) == 0;

        if (!isSameRequest) {
            throw new IdempotencyKeyConflictException(conversionInput.clientId(), conversionInput.idempotencyKey());
        }
    }

    private void validateClientExists(String clientId) {
        if (!clientRepository.existsByClientId(clientId)) {
            throw new ClientNotFoundException(clientId);
        }
    }

    private void validateAtLeastOneFilterSupplied(ConversionHistoryQuery conversionHistoryQuery) {
        boolean noFilterSupplied = conversionHistoryQuery.transactionId() == null
                && conversionHistoryQuery.date() == null
                && conversionHistoryQuery.clientId() == null;

        if (noFilterSupplied) {
            throw new ConversionFilterRequiredException();
        }
    }

    private Specification<ConversionEntity> buildConversionSpecification(ConversionHistoryQuery conversionHistoryQuery) {
        DayRange dayRange = computeDayRange(conversionHistoryQuery.date());

        return Specification.allOf(
                ConversionSpecification.hasTransactionId(conversionHistoryQuery.transactionId()),
                ConversionSpecification.hasClientId(conversionHistoryQuery.clientId()),
                ConversionSpecification.isCreatedOnOrAfter(dayRange.startOfDay()),
                ConversionSpecification.isCreatedBefore(dayRange.startOfNextDay()));
    }

    private DayRange computeDayRange(LocalDate date) {
        if (date == null) {
            return new DayRange(null, null);
        }

        OffsetDateTime startOfDay = date.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime startOfNextDay = date.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        return new DayRange(startOfDay, startOfNextDay);
    }

    private record DayRange(OffsetDateTime startOfDay, OffsetDateTime startOfNextDay) {
    }
}
