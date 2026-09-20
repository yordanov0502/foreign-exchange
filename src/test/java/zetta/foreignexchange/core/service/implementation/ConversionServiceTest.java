package zetta.foreignexchange.core.service.implementation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import zetta.foreignexchange.core.exception.BalanceNotFoundException;
import zetta.foreignexchange.core.exception.ClientNotFoundException;
import zetta.foreignexchange.core.exception.ConversionFilterRequiredException;
import zetta.foreignexchange.core.exception.IdempotencyKeyConflictException;
import zetta.foreignexchange.core.exception.InsufficientFundsException;
import zetta.foreignexchange.core.exception.SameCurrencyException;
import zetta.foreignexchange.core.mapper.ConversionMapper;
import zetta.foreignexchange.core.model.Conversion;
import zetta.foreignexchange.core.model.ConversionHistoryQuery;
import zetta.foreignexchange.core.model.ConversionInput;
import zetta.foreignexchange.core.model.ConversionResult;
import zetta.foreignexchange.core.model.ExchangeRate;
import zetta.foreignexchange.core.processor.ConversionProcessor;
import zetta.foreignexchange.core.service.RateService;
import zetta.foreignexchange.core.validator.CurrencyValidator;
import zetta.foreignexchange.persistence.entity.ClientEntity;
import zetta.foreignexchange.persistence.entity.ConversionEntity;
import zetta.foreignexchange.persistence.repository.ClientRepository;
import zetta.foreignexchange.persistence.repository.ConversionRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class ConversionServiceTest {

    private static final String CLIENT_ID = "CLIENT-001";
    private static final String USD = "USD";
    private static final String EUR = "EUR";
    private static final BigDecimal BASE_AMOUNT = new BigDecimal("100.00");
    private static final BigDecimal RATE = new BigDecimal("0.86984");
    private static final LocalDate QUOTE_DATE = LocalDate.of(2026, 9, 20);
    private static final int PAGE = 0;
    private static final int SIZE = 20;

    @Mock
    private CurrencyValidator currencyValidator;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private RateService rateService;

    @Mock
    private ConversionProcessor conversionProcessor;

    @Mock
    private ConversionRepository conversionRepository;

    @Spy
    private ConversionMapper conversionMapper = Mappers.getMapper(ConversionMapper.class);

    @InjectMocks
    private ConversionServiceImpl conversionService;

    @Test
    void convert_withSufficientFunds_returnConversionResult() {
        ConversionInput conversionInput = buildConversionInput(null);
        ExchangeRate exchangeRate = buildExchangeRate();
        ConversionResult expectedConversionResult = Instancio.create(ConversionResult.class);

        when(clientRepository.existsByClientId(CLIENT_ID))
                .thenReturn(true);
        when(rateService.getExchangeRate(USD, EUR))
                .thenReturn(exchangeRate);
        when(conversionProcessor.processConversion(conversionInput, exchangeRate))
                .thenReturn(expectedConversionResult);

        ConversionResult conversionResult = conversionService.convert(conversionInput);

        assertSame(expectedConversionResult, conversionResult);

        verify(currencyValidator).validateCurrencyPair(USD, EUR);
        verify(clientRepository).existsByClientId(CLIENT_ID);
        verify(rateService).getExchangeRate(USD, EUR);
        verify(conversionProcessor).processConversion(conversionInput, exchangeRate);
    }

    @Test
    void convert_withUnknownClientId_throwClientNotFoundException() {
        ConversionInput conversionInput = buildConversionInput(null);

        when(clientRepository.existsByClientId(CLIENT_ID))
                .thenReturn(false);

        ClientNotFoundException exception = assertThrows(ClientNotFoundException.class,
                () -> conversionService.convert(conversionInput));

        assertEquals(CLIENT_ID, exception.getClientId());
        verifyNoInteractions(rateService, conversionProcessor);
    }

    @Test
    void convert_withCurrencyTheClientDoesNotHold_throwBalanceNotFoundException() {
        ConversionInput conversionInput = buildConversionInput(null);
        ExchangeRate exchangeRate = buildExchangeRate();
        BalanceNotFoundException balanceNotFoundException = new BalanceNotFoundException(CLIENT_ID, EUR);

        when(clientRepository.existsByClientId(CLIENT_ID))
                .thenReturn(true);
        when(rateService.getExchangeRate(USD, EUR))
                .thenReturn(exchangeRate);
        when(conversionProcessor.processConversion(conversionInput, exchangeRate))
                .thenThrow(balanceNotFoundException);

        BalanceNotFoundException exception = assertThrows(BalanceNotFoundException.class,
                () -> conversionService.convert(conversionInput));

        assertSame(balanceNotFoundException, exception);
    }

    @Test
    void convert_withInsufficientSourceBalance_throwInsufficientFundsException() {
        ConversionInput conversionInput = buildConversionInput(null);
        ExchangeRate exchangeRate = buildExchangeRate();
        InsufficientFundsException insufficientFundsException = new InsufficientFundsException(CLIENT_ID, USD);

        when(clientRepository.existsByClientId(CLIENT_ID))
                .thenReturn(true);
        when(rateService.getExchangeRate(USD, EUR))
                .thenReturn(exchangeRate);
        when(conversionProcessor.processConversion(conversionInput, exchangeRate))
                .thenThrow(insufficientFundsException);

        InsufficientFundsException exception = assertThrows(InsufficientFundsException.class,
                () -> conversionService.convert(conversionInput));

        assertSame(insufficientFundsException, exception);
    }

    @Test
    void convert_withoutIdempotencyKey_processConversion() {
        ConversionInput conversionInput = buildConversionInput(null);
        ExchangeRate exchangeRate = buildExchangeRate();
        ConversionResult expectedConversionResult = Instancio.create(ConversionResult.class);

        when(clientRepository.existsByClientId(CLIENT_ID))
                .thenReturn(true);
        when(rateService.getExchangeRate(USD, EUR))
                .thenReturn(exchangeRate);
        when(conversionProcessor.processConversion(conversionInput, exchangeRate))
                .thenReturn(expectedConversionResult);

        conversionService.convert(conversionInput);

        verify(conversionProcessor, never()).findExistingConversionResult(CLIENT_ID, null);
        verify(conversionProcessor).processConversion(conversionInput, exchangeRate);
    }

    @Test
    void convert_withIdenticalBaseAndQuoteCurrency_throwSameCurrencyException() {
        ConversionInput conversionInput = new ConversionInput(CLIENT_ID, null, USD, USD, BASE_AMOUNT);
        SameCurrencyException sameCurrencyException = new SameCurrencyException(USD, USD);
        doThrow(sameCurrencyException).when(currencyValidator).validateCurrencyPair(USD, USD);

        SameCurrencyException exception = assertThrows(SameCurrencyException.class,
                () -> conversionService.convert(conversionInput));

        assertSame(sameCurrencyException, exception);
        verifyNoInteractions(rateService, clientRepository, conversionProcessor);
    }

    @Test
    void convert_withReplayedIdempotencyKey_returnOriginalConversionWithoutSecondDebit() {
        String idempotencyKey = "IDEMPOTENCY-KEY-001";
        ConversionInput conversionInput = buildConversionInput(idempotencyKey);
        ConversionResult originalConversionResult = buildConversionResult(USD, EUR, BASE_AMOUNT);

        when(clientRepository.existsByClientId(CLIENT_ID))
                .thenReturn(true);
        when(conversionProcessor.findExistingConversionResult(CLIENT_ID, idempotencyKey))
                .thenReturn(Optional.of(originalConversionResult));

        ConversionResult conversionResult = conversionService.convert(conversionInput);

        assertSame(originalConversionResult, conversionResult);
        verify(conversionProcessor).findExistingConversionResult(CLIENT_ID, idempotencyKey);
        verifyNoInteractions(rateService);
        verify(conversionProcessor, never()).processConversion(any(), any());
    }

    @Test
    void convert_withIdempotencyKeyOfAnotherClient_processConversion() {
        String idempotencyKey = "IDEMPOTENCY-KEY-001";
        ConversionInput conversionInput = buildConversionInput(idempotencyKey);
        ExchangeRate exchangeRate = buildExchangeRate();
        ConversionResult expectedConversionResult = Instancio.create(ConversionResult.class);

        when(clientRepository.existsByClientId(CLIENT_ID))
                .thenReturn(true);
        when(conversionProcessor.findExistingConversionResult(CLIENT_ID, idempotencyKey))
                .thenReturn(Optional.empty());
        when(rateService.getExchangeRate(USD, EUR))
                .thenReturn(exchangeRate);
        when(conversionProcessor.processConversion(conversionInput, exchangeRate))
                .thenReturn(expectedConversionResult);

        ConversionResult conversionResult = conversionService.convert(conversionInput);

        assertSame(expectedConversionResult, conversionResult);
        verify(conversionProcessor).processConversion(conversionInput, exchangeRate);
    }

    @Test
    void convert_withConcurrentDuplicateIdempotencyKey_returnOriginalConversion() {
        String idempotencyKey = "IDEMPOTENCY-KEY-001";
        ConversionInput conversionInput = buildConversionInput(idempotencyKey);
        ExchangeRate exchangeRate = buildExchangeRate();
        ConversionResult winnerConversionResult = buildConversionResult(USD, EUR, BASE_AMOUNT);
        DataIntegrityViolationException duplicateKeyException = new DataIntegrityViolationException("duplicate key");

        when(clientRepository.existsByClientId(CLIENT_ID))
                .thenReturn(true);
        when(conversionProcessor.findExistingConversionResult(CLIENT_ID, idempotencyKey))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winnerConversionResult));
        when(rateService.getExchangeRate(USD, EUR))
                .thenReturn(exchangeRate);
        when(conversionProcessor.processConversion(conversionInput, exchangeRate))
                .thenThrow(duplicateKeyException);

        ConversionResult conversionResult = conversionService.convert(conversionInput);

        assertSame(winnerConversionResult, conversionResult);
    }

    @Test
    void convert_withIdempotencyKeyReusedForDifferentCurrencyPair_throwIdempotencyKeyConflictException() {
        String idempotencyKey = "IDEMPOTENCY-KEY-001";
        ConversionInput conversionInput = buildConversionInput(idempotencyKey);
        ConversionResult differentConversionResult = buildConversionResult(EUR, USD, BASE_AMOUNT);

        when(clientRepository.existsByClientId(CLIENT_ID))
                .thenReturn(true);
        when(conversionProcessor.findExistingConversionResult(CLIENT_ID, idempotencyKey))
                .thenReturn(Optional.of(differentConversionResult));

        IdempotencyKeyConflictException exception = assertThrows(IdempotencyKeyConflictException.class,
                () -> conversionService.convert(conversionInput));

        assertEquals(CLIENT_ID, exception.getClientId());
        assertEquals(idempotencyKey, exception.getIdempotencyKey());
        verifyNoInteractions(rateService);
        verify(conversionProcessor, never()).processConversion(any(), any());
    }

    @Test
    void convert_withConcurrentDuplicateIdempotencyKeyForDifferentAmount_throwIdempotencyKeyConflictException() {
        String idempotencyKey = "IDEMPOTENCY-KEY-001";
        ConversionInput conversionInput = buildConversionInput(idempotencyKey);
        ExchangeRate exchangeRate = buildExchangeRate();
        ConversionResult winnerConversionResult = buildConversionResult(USD, EUR, new BigDecimal("50.00"));
        DataIntegrityViolationException duplicateKeyException = new DataIntegrityViolationException("duplicate key");

        when(clientRepository.existsByClientId(CLIENT_ID))
                .thenReturn(true);
        when(conversionProcessor.findExistingConversionResult(CLIENT_ID, idempotencyKey))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winnerConversionResult));
        when(rateService.getExchangeRate(USD, EUR))
                .thenReturn(exchangeRate);
        when(conversionProcessor.processConversion(conversionInput, exchangeRate))
                .thenThrow(duplicateKeyException);

        IdempotencyKeyConflictException exception = assertThrows(IdempotencyKeyConflictException.class,
                () -> conversionService.convert(conversionInput));

        assertEquals(CLIENT_ID, exception.getClientId());
        assertEquals(idempotencyKey, exception.getIdempotencyKey());
    }

    @Test
    void getConversionHistory_withNoFilterSupplied_throwConversionFilterRequiredException() {
        ConversionHistoryQuery conversionHistoryQuery = new ConversionHistoryQuery(null, null, null, PAGE, SIZE);

        assertThrows(ConversionFilterRequiredException.class,
                () -> conversionService.getConversionHistory(conversionHistoryQuery));

        verifyNoInteractions(conversionRepository);
    }

    @SuppressWarnings("unchecked")
    @Test
    void getConversionHistory_withClientIdFilter_returnPageOfConversions() {
        ConversionHistoryQuery conversionHistoryQuery = new ConversionHistoryQuery(null, null, CLIENT_ID, PAGE, SIZE);
        ConversionEntity conversionEntity = buildConversionEntity();
        Page<ConversionEntity> conversionEntityPage = new PageImpl<>(List.of(conversionEntity));
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        when(conversionRepository.findAll(any(Specification.class), pageableCaptor.capture()))
                .thenReturn(conversionEntityPage);

        Page<Conversion> conversionPage = conversionService.getConversionHistory(conversionHistoryQuery);

        assertEquals(1, conversionPage.getContent().size());
        assertEquals(CLIENT_ID, conversionPage.getContent().getFirst().clientId());

        Pageable capturedPageable = pageableCaptor.getValue();
        assertEquals(PAGE, capturedPageable.getPageNumber());
        assertEquals(SIZE, capturedPageable.getPageSize());

        Sort.Order createdAtOrder = capturedPageable.getSort().getOrderFor("createdAt");
        Sort.Order idOrder = capturedPageable.getSort().getOrderFor("id");
        assertEquals(Sort.Direction.DESC, createdAtOrder.getDirection());
        assertEquals(Sort.Direction.DESC, idOrder.getDirection());
    }

    private ConversionEntity buildConversionEntity() {
        ClientEntity clientEntity = ClientEntity.builder()
                .clientId(CLIENT_ID)
                .build();
        return ConversionEntity.builder()
                .transactionId(UUID.randomUUID())
                .client(clientEntity)
                .baseCurrency(USD)
                .baseAmount(BASE_AMOUNT)
                .quoteCurrency(EUR)
                .quoteAmount(BASE_AMOUNT)
                .rate(RATE)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    private ConversionInput buildConversionInput(String idempotencyKey) {
        return new ConversionInput(CLIENT_ID, idempotencyKey, USD, EUR, BASE_AMOUNT);
    }

    private ExchangeRate buildExchangeRate() {
        return new ExchangeRate(QUOTE_DATE, USD, EUR, RATE);
    }

    private ConversionResult buildConversionResult(String baseCurrency, String quoteCurrency, BigDecimal baseAmount) {
        Conversion conversion = new Conversion(
                UUID.randomUUID(),
                CLIENT_ID,
                baseCurrency,
                baseAmount,
                quoteCurrency,
                baseAmount,
                RATE,
                OffsetDateTime.now());
        return new ConversionResult(conversion, List.of());
    }
}
