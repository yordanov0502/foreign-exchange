package zetta.foreignexchange.rest.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import zetta.foreignexchange.core.model.Balance;
import zetta.foreignexchange.rest.model.BalanceResponse;
import zetta.foreignexchange.rest.model.ClientBalancesResponse;

import java.math.BigDecimal;
import java.util.List;

class ClientBalancesResponseMapperTest {

    private static final String CLIENT_ID = "CLIENT-001";
    private static final String EUR = "EUR";
    private static final String USD = "USD";
    private static final BigDecimal EIGHT_THOUSAND = new BigDecimal("8000.0000");
    private static final BigDecimal TEN_THOUSAND = new BigDecimal("10000.0000");

    private final ClientBalancesResponseMapper clientBalancesResponseMapper =
            Mappers.getMapper(ClientBalancesResponseMapper.class);

    @Test
    void mapToBalanceResponse_withBalance_mapCurrencyAndAmount() {
        Balance balance = new Balance(USD, TEN_THOUSAND);

        BalanceResponse balanceResponse = clientBalancesResponseMapper.mapToBalanceResponse(balance);

        assertNotNull(balanceResponse);
        assertEquals(USD, balanceResponse.currency());
        assertEquals(TEN_THOUSAND, balanceResponse.amount());
    }

    @Test
    void mapToBalanceResponse_nullBalance_returnNull() {
        assertNull(clientBalancesResponseMapper.mapToBalanceResponse(null));
    }

    @Test
    void mapToBalanceResponses_multipleBalances_mapAllPreservingOrder() {
        List<Balance> balances = List.of(new Balance(EUR, EIGHT_THOUSAND), new Balance(USD, TEN_THOUSAND));

        List<BalanceResponse> balanceResponses = clientBalancesResponseMapper.mapToBalanceResponses(balances);

        assertNotNull(balanceResponses);
        assertEquals(balances.size(), balanceResponses.size());
        assertEquals(EUR, balanceResponses.getFirst().currency());
        assertEquals(EIGHT_THOUSAND, balanceResponses.getFirst().amount());
        assertEquals(USD, balanceResponses.getLast().currency());
        assertEquals(TEN_THOUSAND, balanceResponses.getLast().amount());
    }

    @Test
    void mapToBalanceResponses_emptyBalances_returnEmptyList() {
        List<BalanceResponse> balanceResponses = clientBalancesResponseMapper.mapToBalanceResponses(List.of());

        assertNotNull(balanceResponses);
        assertTrue(balanceResponses.isEmpty());
    }

    @Test
    void mapToClientBalancesResponse_clientIdAndBalances_mapClientIdAndBalances() {
        List<Balance> balances = List.of(new Balance(EUR, EIGHT_THOUSAND), new Balance(USD, TEN_THOUSAND));

        ClientBalancesResponse clientBalancesResponse =
                clientBalancesResponseMapper.mapToClientBalancesResponse(CLIENT_ID, balances);

        assertNotNull(clientBalancesResponse);
        assertEquals(CLIENT_ID, clientBalancesResponse.clientId());
        assertEquals(balances.size(), clientBalancesResponse.balances().size());
        assertEquals(EUR, clientBalancesResponse.balances().getFirst().currency());
        assertEquals(EIGHT_THOUSAND, clientBalancesResponse.balances().getFirst().amount());
        assertEquals(USD, clientBalancesResponse.balances().getLast().currency());
        assertEquals(TEN_THOUSAND, clientBalancesResponse.balances().getLast().amount());
    }

    @Test
    void mapToClientBalancesResponse_emptyBalances_returnResponseWithEmptyBalances() {
        ClientBalancesResponse clientBalancesResponse =
                clientBalancesResponseMapper.mapToClientBalancesResponse(CLIENT_ID, List.of());

        assertNotNull(clientBalancesResponse);
        assertEquals(CLIENT_ID, clientBalancesResponse.clientId());
        assertTrue(clientBalancesResponse.balances().isEmpty());
    }
}
