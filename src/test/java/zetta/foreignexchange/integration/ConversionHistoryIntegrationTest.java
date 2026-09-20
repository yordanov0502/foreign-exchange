package zetta.foreignexchange.integration;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

public class ConversionHistoryIntegrationTest extends BaseIntegrationTestSetUp {

    private static final String CONVERSIONS_URL = "/conversions";
    private static final String CLIENT_ID_PARAM = "clientId";
    private static final String TRANSACTION_ID_PARAM = "transactionId";
    private static final String DATE_PARAM = "date";
    private static final String PAGE_PARAM = "page";
    private static final String SIZE_PARAM = "size";
    private static final String UNKNOWN_CLIENT_ID = "CLIENT-DOES-NOT-EXIST-HISTORY";
    private static final String FIXTURE_DATE = "2020-01-15";
    private static final String FIXTURE_DATE_WITHOUT_HISTORY_002_CONVERSIONS = "2020-01-16";
    private static final String MALFORMED_DATE = "2020-13-45";
    private static final String MALFORMED_TRANSACTION_ID = "not-a-uuid";
    private static final String NON_NUMERIC_VALUE = "abc";
    private static final String HISTORY_001_TXN_A = "11111111-1111-1111-1111-111111111111";
    private static final String HISTORY_001_TXN_B = "22222222-2222-2222-2222-222222222222";
    private static final String HISTORY_001_TXN_C = "33333333-3333-3333-3333-333333333333";
    private static final String USD = "USD";
    private static final String EUR = "EUR";
    private static final String CONVERSION_FILTER_REQUIRED_CODE = "CONVERSION_FILTER_REQUIRED";
    private static final String CONVERSION_FILTER_REQUIRED_MESSAGE =
            "At least one of transactionId, date or clientId must be supplied.";
    private static final int HISTORY_001_TOTAL_CONVERSIONS = 3;
    private static final int HISTORY_FIXTURE_DATE_TOTAL_CONVERSIONS = 3;
    private static final int DEFAULT_PAGE_SIZE = 20;

    @Test
    void getConversionHistory_withClientIdFilter_returnClientConversionsNewestFirst() throws Exception {
        ResultActions result = getConversionHistory(Map.of(CLIENT_ID_PARAM, CLIENT_TEST_HISTORY_FIRST_ID));

        result.andExpect(status().isOk())
                .andExpectAll(
                        jsonPath("$.content", hasSize(HISTORY_001_TOTAL_CONVERSIONS)),
                        jsonPath("$.content[0].transactionId").value(HISTORY_001_TXN_C),
                        jsonPath("$.content[1].transactionId").value(HISTORY_001_TXN_B),
                        jsonPath("$.content[2].transactionId").value(HISTORY_001_TXN_A),
                        jsonPath("$.content[0].clientId").value(CLIENT_TEST_HISTORY_FIRST_ID),
                        jsonPath("$.page").value(0),
                        jsonPath("$.size").value(DEFAULT_PAGE_SIZE),
                        jsonPath("$.totalElements").value(HISTORY_001_TOTAL_CONVERSIONS),
                        jsonPath("$.totalPages").value(1),
                        jsonPath("$.pageable").doesNotExist(),
                        jsonPath("$.numberOfElements").doesNotExist(),
                        jsonPath("$.sort").doesNotExist(),
                        jsonPath("$.first").doesNotExist(),
                        jsonPath("$.last").doesNotExist(),
                        jsonPath("$.empty").doesNotExist());
    }

    @Test
    void getConversionHistory_withTransactionIdFilter_returnSingleConversion() throws Exception {
        ResultActions result = getConversionHistory(Map.of(TRANSACTION_ID_PARAM, HISTORY_001_TXN_A));

        result.andExpect(status().isOk())
                .andExpectAll(
                        jsonPath("$.content", hasSize(1)),
                        jsonPath("$.content[0].transactionId").value(HISTORY_001_TXN_A),
                        jsonPath("$.content[0].clientId").value(CLIENT_TEST_HISTORY_FIRST_ID),
                        jsonPath("$.content[0].sourceCurrency").value(USD),
                        jsonPath("$.content[0].targetCurrency").value(EUR),
                        jsonPath("$.content[0].sourceAmount").exists(),
                        jsonPath("$.content[0].targetAmount").exists(),
                        jsonPath("$.content[0].rate").exists(),
                        jsonPath("$.content[0].timestamp").exists(),
                        jsonPath("$.totalElements").value(1));
    }

    @Test
    void getConversionHistory_withDateFilter_returnConversionsRecordedOnThatUtcDay() throws Exception {
        ResultActions result = getConversionHistory(Map.of(DATE_PARAM, FIXTURE_DATE));

        result.andExpect(status().isOk())
                .andExpectAll(
                        jsonPath("$.content", hasSize(HISTORY_FIXTURE_DATE_TOTAL_CONVERSIONS)),
                        jsonPath("$.content[?(@.transactionId=='%s')]".formatted(HISTORY_001_TXN_C)).isEmpty(),
                        jsonPath("$.content[?(@.transactionId=='%s')]".formatted(HISTORY_001_TXN_A)).exists(),
                        jsonPath("$.content[?(@.transactionId=='%s')]".formatted(HISTORY_001_TXN_B)).exists(),
                        jsonPath("$.page").value(0),
                        jsonPath("$.size").value(DEFAULT_PAGE_SIZE),
                        jsonPath("$.totalElements").value(HISTORY_FIXTURE_DATE_TOTAL_CONVERSIONS),
                        jsonPath("$.totalPages").value(1));
    }

    @Test
    void getConversionHistory_withClientIdAndDateFilter_returnIntersection() throws Exception {
        ResultActions result = getConversionHistory(
                Map.of(CLIENT_ID_PARAM, CLIENT_TEST_HISTORY_FIRST_ID, DATE_PARAM, FIXTURE_DATE));

        result.andExpect(status().isOk())
                .andExpectAll(
                        jsonPath("$.content", hasSize(2)),
                        jsonPath("$.content[0].transactionId").value(HISTORY_001_TXN_B),
                        jsonPath("$.content[1].transactionId").value(HISTORY_001_TXN_A),
                        jsonPath("$.totalElements").value(2));
    }

    @Test
    void getConversionHistory_withClientIdAndDateFilterMatchingNothing_returnEmptyPage() throws Exception {
        ResultActions result = getConversionHistory(
                Map.of(CLIENT_ID_PARAM, CLIENT_TEST_HISTORY_SECOND_ID,
                        DATE_PARAM, FIXTURE_DATE_WITHOUT_HISTORY_002_CONVERSIONS));

        result.andExpect(status().isOk())
                .andExpectAll(
                        jsonPath("$.content", hasSize(0)),
                        jsonPath("$.totalElements").value(0));
    }

    @Test
    void getConversionHistory_withUnknownClientId_returnEmptyPage() throws Exception {
        ResultActions result = getConversionHistory(Map.of(CLIENT_ID_PARAM, UNKNOWN_CLIENT_ID));

        result.andExpect(status().isOk())
                .andExpectAll(
                        jsonPath("$.content", hasSize(0)),
                        jsonPath("$.totalElements").value(0));
    }

    @Test
    void getConversionHistory_withNoFilter_returnBadRequest() throws Exception {
        ResultActions result = getConversionHistory(Map.of());

        result.andExpect(status().isBadRequest())
                .andExpectAll(
                        jsonPath("$.code").value(CONVERSION_FILTER_REQUIRED_CODE),
                        jsonPath("$.message").value(CONVERSION_FILTER_REQUIRED_MESSAGE),
                        jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()),
                        jsonPath("$.path").value(CONVERSIONS_URL));
    }

    @Test
    void getConversionHistory_withMalformedDate_returnBadRequest() throws Exception {
        ResultActions result = getConversionHistory(Map.of(DATE_PARAM, MALFORMED_DATE));

        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.path").value(CONVERSIONS_URL));
    }

    @Test
    void getConversionHistory_withMalformedTransactionId_returnBadRequest() throws Exception {
        ResultActions result = getConversionHistory(Map.of(TRANSACTION_ID_PARAM, MALFORMED_TRANSACTION_ID));

        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.path").value(CONVERSIONS_URL));
    }

    @Test
    void getConversionHistory_withSizeAboveMaximum_returnBadRequest() throws Exception {
        ResultActions result = getConversionHistory(
                Map.of(CLIENT_ID_PARAM, CLIENT_TEST_HISTORY_FIRST_ID, SIZE_PARAM, "101"));

        result.andExpect(status().isBadRequest());
    }

    @Test
    void getConversionHistory_withNegativePage_returnBadRequest() throws Exception {
        ResultActions result = getConversionHistory(
                Map.of(CLIENT_ID_PARAM, CLIENT_TEST_HISTORY_FIRST_ID, PAGE_PARAM, "-1"));

        result.andExpect(status().isBadRequest());
    }

    @Test
    void getConversionHistory_withSizeBelowMinimum_returnBadRequest() throws Exception {
        ResultActions result = getConversionHistory(
                Map.of(CLIENT_ID_PARAM, CLIENT_TEST_HISTORY_FIRST_ID, SIZE_PARAM, "0"));

        result.andExpect(status().isBadRequest());
    }

    @Test
    void getConversionHistory_withNonNumericPage_returnBadRequest() throws Exception {
        ResultActions result = getConversionHistory(
                Map.of(CLIENT_ID_PARAM, CLIENT_TEST_HISTORY_FIRST_ID, PAGE_PARAM, NON_NUMERIC_VALUE));

        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.path").value(CONVERSIONS_URL));
    }

    @Test
    void getConversionHistory_withNonNumericSize_returnBadRequest() throws Exception {
        ResultActions result = getConversionHistory(
                Map.of(CLIENT_ID_PARAM, CLIENT_TEST_HISTORY_FIRST_ID, SIZE_PARAM, NON_NUMERIC_VALUE));

        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.path").value(CONVERSIONS_URL));
    }

    @Test
    void getConversionHistory_withSizeTwo_returnStablePagesCoveringEveryRowOnce() throws Exception {
        ResultActions firstPage = getConversionHistory(
                Map.of(CLIENT_ID_PARAM, CLIENT_TEST_HISTORY_FIRST_ID, SIZE_PARAM, "2", PAGE_PARAM, "0"));
        ResultActions secondPage = getConversionHistory(
                Map.of(CLIENT_ID_PARAM, CLIENT_TEST_HISTORY_FIRST_ID, SIZE_PARAM, "2", PAGE_PARAM, "1"));

        firstPage.andExpect(status().isOk())
                .andExpectAll(
                        jsonPath("$.content", hasSize(2)),
                        jsonPath("$.content[0].transactionId").value(HISTORY_001_TXN_C),
                        jsonPath("$.content[1].transactionId").value(HISTORY_001_TXN_B),
                        jsonPath("$.totalElements").value(HISTORY_001_TOTAL_CONVERSIONS),
                        jsonPath("$.totalPages").value(2));

        secondPage.andExpect(status().isOk())
                .andExpectAll(
                        jsonPath("$.content", hasSize(1)),
                        jsonPath("$.content[0].transactionId").value(HISTORY_001_TXN_A),
                        jsonPath("$.totalElements").value(HISTORY_001_TOTAL_CONVERSIONS),
                        jsonPath("$.totalPages").value(2));
    }

    @Test
    void getConversionHistory_withPageBeyondLastPage_returnEmptyContentAndFullTotals() throws Exception {
        ResultActions result = getConversionHistory(
                Map.of(CLIENT_ID_PARAM, CLIENT_TEST_HISTORY_FIRST_ID, PAGE_PARAM, "5"));

        result.andExpect(status().isOk())
                .andExpectAll(
                        jsonPath("$.content", hasSize(0)),
                        jsonPath("$.totalElements").value(HISTORY_001_TOTAL_CONVERSIONS),
                        jsonPath("$.totalPages").value(1));
    }

    @Test
    void getConversionHistory_withoutPageAndSize_returnDefaultPageAndSize() throws Exception {
        ResultActions result = getConversionHistory(Map.of(CLIENT_ID_PARAM, CLIENT_TEST_HISTORY_FIRST_ID));

        result.andExpect(status().isOk())
                .andExpectAll(
                        jsonPath("$.page").value(0),
                        jsonPath("$.size").value(DEFAULT_PAGE_SIZE));
    }

    private ResultActions getConversionHistory(Map<String, String> queryParameters) throws Exception {
        MockHttpServletRequestBuilder requestBuilder = get(CONVERSIONS_URL).contentType(APPLICATION_JSON_VALUE);
        queryParameters.forEach(requestBuilder::param);

        return mockMvc.perform(requestBuilder);
    }
}
