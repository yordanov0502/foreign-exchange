package zetta.foreignexchange.core.processor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import zetta.foreignexchange.core.exception.BalanceNotFoundException;
import zetta.foreignexchange.core.exception.InsufficientFundsException;
import zetta.foreignexchange.core.mapper.BalanceMapper;
import zetta.foreignexchange.core.mapper.ConversionMapper;
import zetta.foreignexchange.core.model.ConversionInput;
import zetta.foreignexchange.core.model.ConversionResult;
import zetta.foreignexchange.core.model.ExchangeRate;
import zetta.foreignexchange.persistence.entity.BalanceEntity;
import zetta.foreignexchange.persistence.entity.ClientEntity;
import zetta.foreignexchange.persistence.entity.ConversionEntity;
import zetta.foreignexchange.persistence.repository.BalanceRepository;
import zetta.foreignexchange.persistence.repository.ClientRepository;
import zetta.foreignexchange.persistence.repository.ConversionRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class ConversionProcessorTest {

    private static final String CLIENT_ID = "CLIENT-001";
    private static final String USD = "USD";
    private static final String EUR = "EUR";
    private static final BigDecimal BASE_STARTING_AMOUNT = new BigDecimal("500.0000");
    private static final BigDecimal QUOTE_STARTING_AMOUNT = new BigDecimal("200.0000");
    private static final BigDecimal BASE_AMOUNT = new BigDecimal("100.0000");
    private static final BigDecimal RATE = new BigDecimal("0.86984");
    private static final BigDecimal EXPECTED_QUOTE_AMOUNT = new BigDecimal("86.9840");
    private static final LocalDate QUOTE_DATE = LocalDate.of(2026, 9, 20);

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private BalanceRepository balanceRepository;

    @Mock
    private ConversionRepository conversionRepository;

    @Spy
    private ConversionMapper conversionMapper = Mappers.getMapper(ConversionMapper.class);

    @Spy
    private BalanceMapper balanceMapper = Mappers.getMapper(BalanceMapper.class);

    @InjectMocks
    private ConversionProcessor conversionProcessor;

    @Test
    void processConversion_withSufficientFunds_debitSourceCreditTargetAndPersistConversion() {
        BalanceEntity baseBalanceEntity = buildBalanceEntity(USD, BASE_STARTING_AMOUNT);
        BalanceEntity quoteBalanceEntity = buildBalanceEntity(EUR, QUOTE_STARTING_AMOUNT);
        ClientEntity clientEntity = buildClientEntity();
        ConversionInput conversionInput = buildConversionInput();
        ExchangeRate exchangeRate = buildExchangeRate();

        when(balanceRepository.findAndLockByClientClientIdAndCurrency(CLIENT_ID, EUR))
                .thenReturn(Optional.of(quoteBalanceEntity));
        when(balanceRepository.findAndLockByClientClientIdAndCurrency(CLIENT_ID, USD))
                .thenReturn(Optional.of(baseBalanceEntity));
        when(clientRepository.findByClientId(CLIENT_ID))
                .thenReturn(Optional.of(clientEntity));
        when(conversionRepository.saveAndFlush(any(ConversionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(balanceRepository.findByClientClientIdOrderByCurrencyAsc(CLIENT_ID))
                .thenReturn(List.of(quoteBalanceEntity, baseBalanceEntity));

        ConversionResult conversionResult = conversionProcessor.processConversion(conversionInput, exchangeRate);

        assertThat(baseBalanceEntity.getAmount(), comparesEqualTo(new BigDecimal("400.0000")));
        assertThat(quoteBalanceEntity.getAmount(), comparesEqualTo(new BigDecimal("286.9840")));

        assertNotNull(conversionResult);
        assertEquals(USD, conversionResult.conversion().baseCurrency());
        assertThat(conversionResult.conversion().baseAmount(), comparesEqualTo(BASE_AMOUNT));
        assertEquals(EUR, conversionResult.conversion().quoteCurrency());
        assertThat(conversionResult.conversion().quoteAmount(), comparesEqualTo(EXPECTED_QUOTE_AMOUNT));
        assertThat(conversionResult.conversion().rate(), comparesEqualTo(RATE));
        assertEquals(2, conversionResult.updatedBalances().size());

        ArgumentCaptor<ConversionEntity> conversionEntityCaptor = ArgumentCaptor.forClass(ConversionEntity.class);
        verify(conversionRepository).saveAndFlush(conversionEntityCaptor.capture());
        ConversionEntity persistedConversionEntity = conversionEntityCaptor.getValue();
        assertEquals(clientEntity, persistedConversionEntity.getClient());
        assertEquals(USD, persistedConversionEntity.getBaseCurrency());
        assertThat(persistedConversionEntity.getBaseAmount(), comparesEqualTo(BASE_AMOUNT));
        assertEquals(EUR, persistedConversionEntity.getQuoteCurrency());
        assertThat(persistedConversionEntity.getQuoteAmount(), comparesEqualTo(EXPECTED_QUOTE_AMOUNT));
        assertNotNull(persistedConversionEntity.getTransactionId());

        InOrder lockOrder = inOrder(balanceRepository);
        lockOrder.verify(balanceRepository).findAndLockByClientClientIdAndCurrency(CLIENT_ID, EUR);
        lockOrder.verify(balanceRepository).findAndLockByClientClientIdAndCurrency(CLIENT_ID, USD);
    }

    @Test
    void processConversion_withTargetCurrencyClientDoesNotHold_throwBalanceNotFoundException() {
        ConversionInput conversionInput = buildConversionInput();
        ExchangeRate exchangeRate = buildExchangeRate();

        when(balanceRepository.findAndLockByClientClientIdAndCurrency(CLIENT_ID, EUR))
                .thenReturn(Optional.empty());

        BalanceNotFoundException exception = assertThrows(BalanceNotFoundException.class,
                () -> conversionProcessor.processConversion(conversionInput, exchangeRate));

        assertEquals(CLIENT_ID, exception.getClientId());
        assertEquals(EUR, exception.getCurrency());
        verifyNoInteractions(conversionRepository);
    }

    @Test
    void processConversion_withSourceCurrencyClientDoesNotHold_throwBalanceNotFoundException() {
        ConversionInput conversionInput = new ConversionInput(CLIENT_ID, null, EUR, USD, BASE_AMOUNT);
        ExchangeRate exchangeRate = new ExchangeRate(QUOTE_DATE, EUR, USD, RATE);

        when(balanceRepository.findAndLockByClientClientIdAndCurrency(CLIENT_ID, EUR))
                .thenReturn(Optional.empty());

        BalanceNotFoundException exception = assertThrows(BalanceNotFoundException.class,
                () -> conversionProcessor.processConversion(conversionInput, exchangeRate));

        assertEquals(CLIENT_ID, exception.getClientId());
        assertEquals(EUR, exception.getCurrency());
        verifyNoInteractions(conversionRepository);
    }

    @Test
    void processConversion_withInsufficientSourceBalance_throwInsufficientFundsExceptionAndPersistNothing() {
        BalanceEntity baseBalanceEntity = buildBalanceEntity(USD, new BigDecimal("10.0000"));
        BalanceEntity quoteBalanceEntity = buildBalanceEntity(EUR, QUOTE_STARTING_AMOUNT);
        ConversionInput conversionInput = buildConversionInput();
        ExchangeRate exchangeRate = buildExchangeRate();

        when(balanceRepository.findAndLockByClientClientIdAndCurrency(CLIENT_ID, EUR))
                .thenReturn(Optional.of(quoteBalanceEntity));
        when(balanceRepository.findAndLockByClientClientIdAndCurrency(CLIENT_ID, USD))
                .thenReturn(Optional.of(baseBalanceEntity));

        InsufficientFundsException exception = assertThrows(InsufficientFundsException.class,
                () -> conversionProcessor.processConversion(conversionInput, exchangeRate));

        assertEquals(CLIENT_ID, exception.getClientId());
        assertEquals(USD, exception.getCurrency());
        assertThat(baseBalanceEntity.getAmount(), comparesEqualTo(new BigDecimal("10.0000")));
        assertThat(quoteBalanceEntity.getAmount(), comparesEqualTo(QUOTE_STARTING_AMOUNT));
        verifyNoInteractions(conversionRepository);
    }

    @Test
    void findExistingConversionResult_withExistingIdempotencyKey_returnOriginalResult() {
        ConversionEntity conversionEntity = buildConversionEntity();
        BalanceEntity balanceEntity = buildBalanceEntity(USD, BASE_STARTING_AMOUNT);

        when(conversionRepository.findByClientClientIdAndIdempotencyKey(CLIENT_ID, "IDEMPOTENCY-KEY-001"))
                .thenReturn(Optional.of(conversionEntity));
        when(balanceRepository.findByClientClientIdOrderByCurrencyAsc(CLIENT_ID))
                .thenReturn(List.of(balanceEntity));

        Optional<ConversionResult> conversionResult =
                conversionProcessor.findExistingConversionResult(CLIENT_ID, "IDEMPOTENCY-KEY-001");

        assertNotNull(conversionResult);
        assertTrue(conversionResult.isPresent());
        assertEquals(USD, conversionResult.get().conversion().baseCurrency());
        assertEquals(1, conversionResult.get().updatedBalances().size());
    }

    @Test
    void findExistingConversionResult_withoutExistingIdempotencyKey_returnEmpty() {
        when(conversionRepository.findByClientClientIdAndIdempotencyKey(CLIENT_ID, "IDEMPOTENCY-KEY-001"))
                .thenReturn(Optional.empty());

        Optional<ConversionResult> conversionResult =
                conversionProcessor.findExistingConversionResult(CLIENT_ID, "IDEMPOTENCY-KEY-001");

        assertFalse(conversionResult.isPresent());
        verifyNoInteractions(balanceRepository);
    }

    private ConversionInput buildConversionInput() {
        return new ConversionInput(CLIENT_ID, null, USD, EUR, ConversionProcessorTest.BASE_AMOUNT);
    }

    private ExchangeRate buildExchangeRate() {
        return new ExchangeRate(QUOTE_DATE, USD, EUR, RATE);
    }

    private BalanceEntity buildBalanceEntity(String currency, BigDecimal amount) {
        return BalanceEntity.builder()
                .currency(currency)
                .amount(amount)
                .build();
    }

    private ClientEntity buildClientEntity() {
        return ClientEntity.builder()
                .clientId(CLIENT_ID)
                .build();
    }

    private ConversionEntity buildConversionEntity() {
        return ConversionEntity.builder()
                .client(buildClientEntity())
                .baseCurrency(USD)
                .baseAmount(BASE_AMOUNT)
                .quoteCurrency(EUR)
                .quoteAmount(EXPECTED_QUOTE_AMOUNT)
                .rate(RATE)
                .idempotencyKey("IDEMPOTENCY-KEY-001")
                .createdAt(OffsetDateTime.now())
                .build();
    }
}
