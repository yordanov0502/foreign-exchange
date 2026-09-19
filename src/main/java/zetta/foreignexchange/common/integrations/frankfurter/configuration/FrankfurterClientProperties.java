package zetta.foreignexchange.common.integrations.frankfurter.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "frankfurter.client")
@Getter
@Setter
public class FrankfurterClientProperties {

    private String url;

    private Duration connectTimeout;

    private Duration readTimeout;
}
