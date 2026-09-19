package zetta.foreignexchange.common.integrations.frankfurter.configuration;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import zetta.foreignexchange.common.integrations.frankfurter.exception.FrankfurterGeneralException;
import zetta.foreignexchange.common.integrations.frankfurter.exception.FrankfurterPairNotQuotableException;

import java.util.Map;

class FrankfurterErrorDecoderTest {

    private static final String METHOD_KEY = "FrankfurterFeignClient#fetchLatestExchangeRates(String,String)";
    private static final String REQUEST_URL = "https://api.frankfurter.dev/v2/rate/USD/BTC";

    private final FrankfurterErrorDecoder frankfurterErrorDecoder = new FrankfurterErrorDecoder();

    @Test
    void decode_responseWithUnprocessableContentStatus_returnFrankfurterPairNotQuotableException() {
        Exception exception =
                frankfurterErrorDecoder.decode(METHOD_KEY, buildResponse(HttpStatus.UNPROCESSABLE_CONTENT.value()));

        assertInstanceOf(FrankfurterPairNotQuotableException.class, exception);
    }

    @Test
    void decode_responseWithNotFoundStatus_returnFrankfurterPairNotQuotableException() {
        Exception exception = frankfurterErrorDecoder.decode(METHOD_KEY, buildResponse(HttpStatus.NOT_FOUND.value()));

        assertInstanceOf(FrankfurterPairNotQuotableException.class, exception);
    }

    @Test
    void decode_withInternalServerError_returnFrankfurterProviderException() {
        Exception exception =
                frankfurterErrorDecoder.decode(METHOD_KEY, buildResponse(HttpStatus.INTERNAL_SERVER_ERROR.value()));

        assertInstanceOf(FrankfurterGeneralException.class, exception);
    }

    private Response buildResponse(int status) {
        Request request = Request.create(Request.HttpMethod.GET, REQUEST_URL, Map.of(), null, null, null);
        return Response.builder().status(status).request(request).build();
    }
}
