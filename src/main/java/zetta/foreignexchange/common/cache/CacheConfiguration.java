package zetta.foreignexchange.common.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

@Configuration
@EnableCaching
@RequiredArgsConstructor
public class CacheConfiguration {

    public static final String EXCHANGE_RATE = "exchangeRate";

    private final CacheProperties cacheProperties;

    @Bean
    CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.registerCustomCache(EXCHANGE_RATE, buildExchangeRateCache());
        cacheManager.setCacheNames(Collections.emptyList());
        return cacheManager;
    }

    private Cache<Object, Object> buildExchangeRateCache() {
        return Caffeine.newBuilder()
                .maximumSize(cacheProperties.getMaxSize())
                .expireAfterWrite(cacheProperties.getTtl())
                .build();
    }
}
