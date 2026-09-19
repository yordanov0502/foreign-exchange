package zetta.foreignexchange.common.cache;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "cache.currency-rate-pair")
@Getter
@Setter
public class CacheProperties {

    private Duration ttl;

    private long maxSize;
}
