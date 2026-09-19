package zetta.foreignexchange.common.integrations.frankfurter.configuration;

import feign.Client;
import feign.Request;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import zetta.foreignexchange.common.integrations.frankfurter.exception.FrankfurterGeneralException;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
public class FrankfurterConfiguration {

    private static final boolean FOLLOW_REDIRECTS = true;
    private static final String MESSAGE = "Frankfurter was unreachable or timed out";

    @Bean
    Request.Options frankfurterRequestOptions(FrankfurterClientProperties frankfurterClientProperties) {
        return new Request.Options(
                frankfurterClientProperties.getConnectTimeout().toMillis(),
                TimeUnit.MILLISECONDS,
                frankfurterClientProperties.getReadTimeout().toMillis(),
                TimeUnit.MILLISECONDS,
                FOLLOW_REDIRECTS);
    }

    @Bean
    Client frankfurterHttpClient() {
        Client delegate = new feign.okhttp.OkHttpClient(new OkHttpClient());
        return (request, options) -> {
            try {
                return delegate.execute(request, options);
            } catch (IOException ioException) {
                log.error(MESSAGE, ioException.getCause());
                throw new FrankfurterGeneralException(MESSAGE);
            }
        };
    }

    @Bean
    Retryer frankfurterRetryer() {
        return Retryer.NEVER_RETRY;
    }

    @Bean
    ErrorDecoder frankfurterErrorDecoder() {
        return new FrankfurterErrorDecoder();
    }
}
