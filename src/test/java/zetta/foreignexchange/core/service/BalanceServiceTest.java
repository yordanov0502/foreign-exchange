package zetta.foreignexchange.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import zetta.foreignexchange.core.exception.ClientNotFoundException;
import zetta.foreignexchange.core.mapper.BalanceMapper;
import zetta.foreignexchange.core.model.Balance;
import zetta.foreignexchange.core.service.implementation.BalanceServiceImpl;
import zetta.foreignexchange.persistence.entity.BalanceEntity;
import zetta.foreignexchange.persistence.repository.BalanceRepository;
import zetta.foreignexchange.persistence.repository.ClientRepository;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    private static final String CLIENT_ID = "CLIENT-999";

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private BalanceRepository balanceRepository;

    @Spy
    private BalanceMapper balanceMapper = Mappers.getMapper(BalanceMapper.class);

    @InjectMocks
    private BalanceServiceImpl balanceService;

    @Test
    void getClientBalances_withClientId_returnBalances() {
        BalanceEntity firstBalanceEntity = Instancio.create(BalanceEntity.class);
        BalanceEntity secondBalanceEntity = Instancio.create(BalanceEntity.class);
        List<BalanceEntity> balanceEntities = List.of(firstBalanceEntity, secondBalanceEntity);

        when(clientRepository.existsByClientId(CLIENT_ID))
                .thenReturn(true);
        when(balanceRepository.findByClientClientIdOrderByCurrencyAsc(CLIENT_ID))
                .thenReturn(balanceEntities);

        List<Balance> balances = balanceService.getClientBalances(CLIENT_ID);

        assertNotNull(balances);
        assertEquals(balanceEntities.size(), balances.size());
        assertEquals(firstBalanceEntity.getCurrency(), balances.get(0).currency());
        assertEquals(firstBalanceEntity.getAmount(), balances.get(0).amount());
        assertEquals(secondBalanceEntity.getCurrency(), balances.get(1).currency());
        assertEquals(secondBalanceEntity.getAmount(), balances.get(1).amount());

        verify(clientRepository).existsByClientId(CLIENT_ID);
        verify(balanceRepository).findByClientClientIdOrderByCurrencyAsc(CLIENT_ID);
        verify(balanceMapper).mapToBalances(balanceEntities);
    }

    @Test
    void getClientBalances_withNonExistingClientId_returnClientNotFoundException() {
        when(clientRepository.existsByClientId(CLIENT_ID))
                .thenReturn(false);

        ClientNotFoundException exception = assertThrows(ClientNotFoundException.class,
                () -> balanceService.getClientBalances(CLIENT_ID));

        assertEquals(CLIENT_ID, exception.getClientId());

        verify(clientRepository).existsByClientId(CLIENT_ID);
        verifyNoInteractions(balanceRepository, balanceMapper);
    }
}