package zetta.foreignexchange.rest.controller;

import static java.util.Collections.emptyList;
import static org.instancio.Select.field;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import zetta.foreignexchange.core.model.Balance;
import zetta.foreignexchange.core.service.BalanceService;
import zetta.foreignexchange.rest.mapper.ClientBalancesResponseMapper;
import zetta.foreignexchange.rest.model.ClientBalancesResponse;

import java.util.List;

@ExtendWith(MockitoExtension.class)
public class ClientControllerTest {

    private static final String CLIENT_ID = "CLIENT-999";
    private static final String EUR = "EUR";
    private static final String USD = "USD";

    @Mock
    private BalanceService balanceService;

    @Spy
    private ClientBalancesResponseMapper clientBalancesResponseMapper =
            Mappers.getMapper(ClientBalancesResponseMapper.class);

    @InjectMocks
    private ClientController clientController;

    @Test
    void getClientBalances_withSingleBalance_returnClientBalancesResponse() {
        Balance balance = Instancio.create(Balance.class);

        when(balanceService.getClientBalances(CLIENT_ID))
                .thenReturn(List.of(balance));

        ResponseEntity<ClientBalancesResponse> response = clientController.getClientBalances(CLIENT_ID);

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        ClientBalancesResponse clientBalancesResponse = response.getBody();
        assertEquals(CLIENT_ID, clientBalancesResponse.clientId());
        assertEquals(balance.currency(), clientBalancesResponse.balances().getFirst().currency());
        assertEquals(balance.amount(), clientBalancesResponse.balances().getFirst().amount());

        verify(balanceService).getClientBalances(CLIENT_ID);
        verify(clientBalancesResponseMapper).mapToClientBalancesResponse(CLIENT_ID, List.of(balance));
    }

    @Test
    void getClientBalances_withEmptyBalances_returnClientBalancesResponse() {
        when(balanceService.getClientBalances(CLIENT_ID))
                .thenReturn(emptyList());

        ResponseEntity<ClientBalancesResponse> response = clientController.getClientBalances(CLIENT_ID);

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        ClientBalancesResponse clientBalancesResponse = response.getBody();
        assertEquals(CLIENT_ID, clientBalancesResponse.clientId());
        assertTrue(clientBalancesResponse.balances().isEmpty());

        verify(balanceService).getClientBalances(CLIENT_ID);
        verify(clientBalancesResponseMapper).mapToClientBalancesResponse(CLIENT_ID, emptyList());
    }

    @Test
    void getClientBalances_withMultipleBalances_returnClientBalancesResponse() {
        Balance firstBalance = Instancio.of(Balance.class)
                .set(field(Balance::currency), EUR)
                .create();
        Balance secondBalance = Instancio.of(Balance.class)
                .set(field(Balance::currency), USD)
                .create();
        List<Balance> balances = List.of(firstBalance, secondBalance);

        when(balanceService.getClientBalances(CLIENT_ID))
                .thenReturn(balances);

        ResponseEntity<ClientBalancesResponse> response = clientController.getClientBalances(CLIENT_ID);

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        ClientBalancesResponse clientBalancesResponse = response.getBody();
        assertEquals(CLIENT_ID, clientBalancesResponse.clientId());
        assertEquals(balances.size(), clientBalancesResponse.balances().size());
        assertEquals(firstBalance.currency(), clientBalancesResponse.balances().getFirst().currency());
        assertEquals(firstBalance.amount(), clientBalancesResponse.balances().getFirst().amount());
        assertEquals(secondBalance.currency(), clientBalancesResponse.balances().getLast().currency());
        assertEquals(secondBalance.amount(), clientBalancesResponse.balances().getLast().amount());

        verify(balanceService).getClientBalances(CLIENT_ID);
        verify(clientBalancesResponseMapper).mapToClientBalancesResponse(CLIENT_ID, balances);
    }

}
