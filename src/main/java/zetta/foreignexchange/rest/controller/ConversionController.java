package zetta.foreignexchange.rest.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zetta.foreignexchange.core.constant.IdempotencyConstant;
import zetta.foreignexchange.core.constant.PaginationConstant;
import zetta.foreignexchange.core.model.Conversion;
import zetta.foreignexchange.core.model.ConversionHistoryQuery;
import zetta.foreignexchange.core.model.ConversionInput;
import zetta.foreignexchange.core.model.ConversionResult;
import zetta.foreignexchange.core.service.ConversionService;
import zetta.foreignexchange.rest.error.ErrorResponse;
import zetta.foreignexchange.rest.mapper.ConversionHistoryQueryMapper;
import zetta.foreignexchange.rest.mapper.ConversionHistoryResponseMapper;
import zetta.foreignexchange.rest.mapper.ConversionRequestMapper;
import zetta.foreignexchange.rest.mapper.ConversionResponseMapper;
import zetta.foreignexchange.rest.model.ConversionHistoryFilterRequest;
import zetta.foreignexchange.rest.model.ConversionHistoryResponse;
import zetta.foreignexchange.rest.model.ConversionRequest;
import zetta.foreignexchange.rest.model.ConversionResponse;

@RestController
@RequestMapping("/conversions")
@Tag(name = "Conversions", description = "Convert an amount from one currency to another for a client.")
@RequiredArgsConstructor
@Validated
public class ConversionController {

    private static final String CLIENT_ID_HEADER = "X-Client-Id";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final ConversionRequestMapper conversionRequestMapper;
    private final ConversionResponseMapper conversionResponseMapper;
    private final ConversionHistoryQueryMapper conversionHistoryQueryMapper;
    private final ConversionHistoryResponseMapper conversionHistoryResponseMapper;
    private final ConversionService conversionService;

    @Operation(
            summary = "Convert an amount from one currency to another for a client",
            description = "Debits the source currency, credits the target currency and records the conversion. "
                    + "Replaying the same Idempotency-Key for the same client "
                    + "returns the original conversion instead of debiting twice.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "The conversion was recorded (or, on a replayed Idempotency-Key, "
                        + "the original conversion is returned)",
                content = @Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ConversionResponse.class),
                        examples = @ExampleObject(
                                value = """
                                {
                                  "transactionId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                                  "sourceCurrency": "USD",
                                  "sourceAmount": 100.0000,
                                  "targetCurrency": "EUR",
                                  "targetAmount": 86.9840,
                                  "rate": 0.86984,
                                  "timestamp": "2026-09-20T12:00:00Z",
                                  "balances": [
                                    { "currency": "EUR", "amount": 1286.9840 },
                                    { "currency": "USD", "amount": 1400.0000 }
                                  ]
                                }
                                """))),
        @ApiResponse(
                responseCode = "404",
                description = "Unknown clientId or the client holds no balance in the requested currency",
                content = @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = {
                            @ExampleObject(
                                    name = "CLIENT_NOT_FOUND",
                                    value = """
                                    {
                                      "code": "CLIENT_NOT_FOUND",
                                      "message": "Client with ID:CLIENT-999 was not found.",
                                      "status": 404,
                                      "path": "/conversions"
                                    }
                                    """),
                            @ExampleObject(
                                    name = "BALANCE_NOT_FOUND",
                                    value = """
                                    {
                                      "code": "BALANCE_NOT_FOUND",
                                      "message": "Client CLIENT-001 has no balance in EUR.",
                                      "status": 404,
                                      "path": "/conversions"
                                    }
                                    """)
                        })),
        @ApiResponse(
                responseCode = "409",
                description = "The Idempotency-Key was already used by this client for a conversion request "
                        + "with different currencies or amount",
                content = @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(
                                value = """
                                {
                                  "code": "IDEMPOTENCY_KEY_CONFLICT",
                                  "message": "Client CLIENT-001 already used Idempotency-Key IDEMPOTENCY-KEY-001 \
                                for a different conversion request.",
                                  "status": 409,
                                  "path": "/conversions"
                                }
                                """))),
        @ApiResponse(
                responseCode = "422",
                description = "Insufficient funds, identical source/target currency "
                        + "or an unsupported currency pair",
                content = @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = {
                            @ExampleObject(
                                    name = "INSUFFICIENT_FUNDS",
                                    value = """
                                    {
                                      "code": "INSUFFICIENT_FUNDS",
                                      "message": "Client CLIENT-001 has insufficient USD balance for this conversion.",
                                      "status": 422,
                                      "path": "/conversions"
                                    }
                                    """),
                            @ExampleObject(
                                    name = "SAME_CURRENCY",
                                    value = """
                                    {
                                      "code": "SAME_CURRENCY",
                                      "message": "Currency pair USD/USD must contain two different currencies.",
                                      "status": 422,
                                      "path": "/conversions"
                                    }
                                    """),
                            @ExampleObject(
                                    name = "UNSUPPORTED_CURRENCY_PAIR",
                                    value = """
                                    {
                                      "code": "UNSUPPORTED_CURRENCY_PAIR",
                                      "message": "Currency pair USD/XXX is not supported.",
                                      "status": 422,
                                      "path": "/conversions"
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
                                  "path": "/conversions"
                                }
                                """)))
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ConversionResponse> createConversion(
            @Parameter(description = "Client identifier", example = "CLIENT-001")
            @RequestHeader(CLIENT_ID_HEADER) @NotBlank final String clientId,
            @Parameter(description = "Idempotency key")
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false)
            @Size(max = IdempotencyConstant.MAX_KEY_LENGTH) final String idempotencyKey,
            @Valid @RequestBody ConversionRequest conversionRequest) {

        ConversionInput conversionInput =
                conversionRequestMapper.mapToConversionInput(clientId, idempotencyKey, conversionRequest);
        ConversionResult conversionResult = conversionService.convert(conversionInput);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(conversionResponseMapper.mapToConversionResponse(conversionResult));
    }

    @Operation(
            summary = "Get a paginated, filtered conversion history",
            description = "Filters by transactionId, date (a whole UTC day) and/or clientId. "
                    + "At least one filter must be provided.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "A page of conversion history (empty content if nothing matches)",
                content = @Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ConversionHistoryResponse.class),
                        examples = @ExampleObject(
                                value = """
                                {
                                  "content": [
                                    {
                                      "transactionId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                                      "clientId": "CLIENT-001",
                                      "sourceCurrency": "USD",
                                      "sourceAmount": 100.0000,
                                      "targetCurrency": "EUR",
                                      "targetAmount": 86.9840,
                                      "rate": 0.86984,
                                      "timestamp": "2026-09-20T12:00:00Z"
                                    }
                                  ],
                                  "page": 0,
                                  "size": 20,
                                  "totalElements": 1,
                                  "totalPages": 1
                                }
                                """))),
        @ApiResponse(
                responseCode = "400",
                description = "No filter supplied, a filter is malformed, or page/size is out of bounds",
                content = @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = {
                            @ExampleObject(
                                    name = "CONVERSION_FILTER_REQUIRED",
                                    value = """
                                    {
                                      "code": "CONVERSION_FILTER_REQUIRED",
                                      "message": "At least one of transactionId, date or clientId must be supplied.",
                                      "status": 400,
                                      "path": "/conversions"
                                    }
                                    """),
                            @ExampleObject(
                                    name = "FIELD_ERROR",
                                    value = """
                                    {
                                      "code": "FIELD_ERROR",
                                      "message": "Either you submitted a request that is missing a mandatory field \
                                or the value of a field does not match the format expected.",
                                      "status": 400,
                                      "path": "/conversions"
                                    }
                                """)
                        }))
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ConversionHistoryResponse> getConversionHistory(
            final ConversionHistoryFilterRequest conversionHistoryFilterRequest,
            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(value = "page", defaultValue = PaginationConstant.DEFAULT_PAGE_NUMBER)
            @Min(PaginationConstant.MIN_PAGE_NUMBER) final int page,
            @Parameter(description = "Maximum number of items per page", example = "20")
            @RequestParam(value = "size", defaultValue = PaginationConstant.DEFAULT_PAGE_SIZE)
            @Min(PaginationConstant.MIN_PAGE_SIZE) @Max(PaginationConstant.MAX_PAGE_SIZE) final int size) {

        ConversionHistoryQuery conversionHistoryQuery =
                conversionHistoryQueryMapper.mapToConversionHistoryQuery(conversionHistoryFilterRequest, page, size);
        Page<Conversion> conversionPage = conversionService.getConversionHistory(conversionHistoryQuery);

        return ResponseEntity.ok(conversionHistoryResponseMapper.mapToConversionHistoryResponse(conversionPage));
    }
}
