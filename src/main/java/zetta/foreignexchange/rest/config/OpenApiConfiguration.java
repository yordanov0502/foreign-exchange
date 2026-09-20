package zetta.foreignexchange.rest.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    private static final String API_TITLE = "Foreign Exchange Service API";
    private static final String API_VERSION = "1.0.0";
    private static final String API_DESCRIPTION =
            "Live exchange rates, currency conversions against per-client balances with Idempotency-Key replay "
                    + "support, per-client balance lookup and a paginated, filtered conversion history. "
                    + "There is no authentication by design - the client identifier is supplied "
                    + "by the caller via the X-Client-Id header.";

    @Bean
    OpenAPI foreignExchangeOpenApi() {
        return new OpenAPI().info(new Info()
                .title(API_TITLE)
                .version(API_VERSION)
                .description(API_DESCRIPTION));
    }
}
