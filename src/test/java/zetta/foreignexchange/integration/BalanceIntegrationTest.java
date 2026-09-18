package zetta.foreignexchange.integration;

import static java.lang.String.format;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Transactional
public class BalanceIntegrationTest extends BaseIntegrationTestSetUp {

    private static final String GET_CLIENT_BALANCES = "/clients/{clientId}/balances";
    private static final String CLIENT_ID_PATH_VARIABLE = "{clientId}";
    private static final String CLIENT_NOT_FOUND_CODE = "CLIENT_NOT_FOUND";
    private static final String CLIENT_NOT_FOUND_MESSAGE = "Client with ID:%s was not found.";

    @Test
    void getClientBalances_withClientHavingBalances_returnClientBalances() throws Exception {
        ResultActions result = getClientBalances(CLIENT_TEST_ID);
        BigDecimal eurBalance = new BigDecimal("1200.0000");
        BigDecimal usdBalance = new BigDecimal("1500.0000");

        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(CLIENT_TEST_ID))
                .andExpect(jsonPath("$.balances", hasSize(2)))
                .andExpect(jsonPath("$.balances[0].currency").value("EUR"))
                .andExpect(jsonPath("$.balances[0].amount").value(comparesEqualTo(eurBalance), BigDecimal.class))
                .andExpect(jsonPath("$.balances[1].currency").value("USD"))
                .andExpect(jsonPath("$.balances[1].amount").value(comparesEqualTo(usdBalance), BigDecimal.class));
    }

    @Test
    void getClientBalances_withClientWithoutBalances_returnClientBalances() throws Exception {
        ResultActions result = getClientBalances(CLIENT_TEST_WITHOUT_BALANCES_ID);

        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(CLIENT_TEST_WITHOUT_BALANCES_ID))
                .andExpect(jsonPath("$.balances", hasSize(0)));
    }

    @Test
    void getClientBalances_withNonExistingClientId_returnClientBalances() throws Exception {
        String nonExistingClientId = "non-existing-client-id";
        ResultActions result = getClientBalances(nonExistingClientId);

        result.andExpect(status().isNotFound())
                .andExpectAll(
                        jsonPath("$.code").value(CLIENT_NOT_FOUND_CODE),
                        jsonPath("$.message").value(format(CLIENT_NOT_FOUND_MESSAGE, nonExistingClientId)),
                        jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()),
                        jsonPath("$.path").value(buildClientBalancesPath(nonExistingClientId)));
    }

    private String buildClientBalancesPath(String clientId) {
        return GET_CLIENT_BALANCES.replace(CLIENT_ID_PATH_VARIABLE, clientId);
    }

    private ResultActions getClientBalances(String clientId) throws Exception {
        return mockMvc.perform(get(GET_CLIENT_BALANCES, clientId)
                .contentType(APPLICATION_JSON_VALUE));
    }
}
