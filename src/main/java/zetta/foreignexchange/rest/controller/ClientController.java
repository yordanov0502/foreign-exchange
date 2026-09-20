package zetta.foreignexchange.rest.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zetta.foreignexchange.core.model.Balance;
import zetta.foreignexchange.core.service.BalanceService;
import zetta.foreignexchange.rest.error.ErrorResponse;
import zetta.foreignexchange.rest.mapper.ClientBalancesResponseMapper;
import zetta.foreignexchange.rest.model.ClientBalancesResponse;

import java.util.List;

@RestController
@RequestMapping("/clients")
@Tag(name = "Clients", description = "Per-client currency balances.")
@RequiredArgsConstructor
public class ClientController {

    private final BalanceService balanceService;
    private final ClientBalancesResponseMapper clientBalancesResponseMapper;

    @Operation(
            summary = "Get a client's current balances",
            description = "Returns one balance per currency the client holds, ordered by currency code.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Balances returned; the list is empty when the client holds no currency",
                content = @Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ClientBalancesResponse.class),
                        examples = @ExampleObject(
                                value = """
                                {
                                  "clientId": "CLIENT-001",
                                  "balances": [
                                    {
                                      "currency": "EUR",
                                      "amount": 8000
                                    },
                                    {
                                      "currency": "USD",
                                      "amount": 10000
                                    }
                                  ]
                                }
                                """))),
        @ApiResponse(
                responseCode = "404",
                description = "No client exists with the supplied clientId",
                content = @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(
                                value = """
                                {
                                  "code": "CLIENT_NOT_FOUND",
                                  "message": "Client with ID:CLIENT-999 was not found.",
                                  "status": 404,
                                  "path": "/clients/CLIENT-999/balances"
                                }
                                """))),
        @ApiResponse(
                responseCode = "500",
                description = "Internal error has occurred",
                content = @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(
                                value = """
                                {
                                  "code": "INTERNAL_SERVER_ERROR",
                                  "message": "An unexpected internal error has occurred.",
                                  "status": 500,
                                  "path": "/clients/CLIENT-999/balances"
                                }
                                """)))
    })
    @GetMapping(value = "/{clientId}/balances", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ClientBalancesResponse> getClientBalances(@PathVariable final String clientId) {
        List<Balance> balances = balanceService.getClientBalances(clientId);
        return ResponseEntity.ok(clientBalancesResponseMapper.mapToClientBalancesResponse(clientId, balances));
    }
}
