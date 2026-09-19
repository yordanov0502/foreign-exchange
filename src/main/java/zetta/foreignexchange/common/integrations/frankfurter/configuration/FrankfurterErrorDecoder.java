package zetta.foreignexchange.common.integrations.frankfurter.configuration;

import feign.Response;
import feign.codec.ErrorDecoder;
import org.springframework.http.HttpStatus;
import zetta.foreignexchange.common.integrations.frankfurter.exception.FrankfurterGeneralException;
import zetta.foreignexchange.common.integrations.frankfurter.exception.FrankfurterPairNotQuotableException;

class FrankfurterErrorDecoder implements ErrorDecoder {

    private static final String NOT_QUOTABLE_MESSAGE = "Frankfurter cannot quote this pair, HTTP %d";
    private static final String PROVIDER_ERROR_MESSAGE = "Frankfurter request failed, HTTP %d";

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        if (status == HttpStatus.UNPROCESSABLE_CONTENT.value() || status == HttpStatus.NOT_FOUND.value()) {
            return new FrankfurterPairNotQuotableException(NOT_QUOTABLE_MESSAGE.formatted(status));
        }
        return new FrankfurterGeneralException(PROVIDER_ERROR_MESSAGE.formatted(status));
    }
}
