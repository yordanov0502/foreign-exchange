package zetta.foreignexchange.rest.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zetta.foreignexchange.core.model.ExchangeRate;
import zetta.foreignexchange.core.service.RateService;
import zetta.foreignexchange.rest.error.ErrorResponse;
import zetta.foreignexchange.rest.mapper.ExchangeRateResponseMapper;
import zetta.foreignexchange.rest.model.ExchangeRateResponse;

@RestController
@RequestMapping("/rates")
@Tag(name = "Rates", description = "Exchange rates for currencies")
@RequiredArgsConstructor
public class RateController {

    private final RateService rateService;
    private final ExchangeRateResponseMapper exchangeRateResponseMapper;

    @Operation(
            summary = "Get the current exchange rate for a currency pair",
            description = "Rates are fetched from 3rd party provider. "
                    + "An identical source and target currency is rejected as SAME_CURRENCY.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "The current rate for the pair",
                content = @Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ExchangeRateResponse.class),
                        examples = @ExampleObject(
                                value = """
                                {
                                  "date": "2026-09-20",
                                  "baseCurrency": "USD",
                                  "quoteCurrency": "EUR",
                                  "rate": 0.86984
                                }
                                """))),
        @ApiResponse(
                responseCode = "422",
                description = "A currency code is not a valid ISO-4217 code, is malformed, "
                        + "or the source and target currency are identical",
                content = @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = {
                            @ExampleObject(
                                    name = "UNSUPPORTED_CURRENCY_PAIR",
                                    value = """
                                    {
                                      "code": "UNSUPPORTED_CURRENCY_PAIR",
                                      "message": "Currency pair USD/XXX is not supported.",
                                      "status": 422,
                                      "path": "/rates"
                                    }
                                    """),
                            @ExampleObject(
                                    name = "SAME_CURRENCY",
                                    value = """
                                    {
                                      "code": "SAME_CURRENCY",
                                      "message": "Currency pair USD/USD must contain two different currencies.",
                                      "status": 422,
                                      "path": "/rates"
                                    }
                                    """)
                        })),
        @ApiResponse(
                responseCode = "502",
                description = "The rate provider timed out, was unreachable or returned an unusable answer",
                content = @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(
                                value = """
                                {
                                  "code": "EXCHANGE_RATE_UNAVAILABLE",
                                  "message": "Exchange rate for currency pair USD/EUR is currently unavailable.",
                                  "status": 502,
                                  "path": "/rates"
                                }
                                """)))
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ExchangeRateResponse> getExchangeRate(
            @Parameter(description = "Currency to convert from", example = "USD")
            @RequestParam("from") final String baseCurrency,
            @Parameter(description = "Currency to convert to", example = "EUR")
            @RequestParam("to") final String quoteCurrency) {

        ExchangeRate exchangeRate = rateService.getExchangeRate(baseCurrency, quoteCurrency);
        return ResponseEntity.ok(exchangeRateResponseMapper.mapToExchangeRateResponse(exchangeRate));
    }
}
