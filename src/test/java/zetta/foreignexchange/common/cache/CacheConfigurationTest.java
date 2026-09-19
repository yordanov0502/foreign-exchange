package zetta.foreignexchange.common.cache;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;

import java.time.Duration;

class CacheConfigurationTest {

    private static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final long CACHE_MAX_SIZE = 1000L;
    private static final String UNREGISTERED_CACHE_NAME = "unregisteredCache";

    private CacheManager cacheManager;

    @BeforeEach
    void setUpCacheManager() {
        CacheProperties cacheProperties = new CacheProperties();
        cacheProperties.setTtl(CACHE_TTL);
        cacheProperties.setMaxSize(CACHE_MAX_SIZE);

        cacheManager = new CacheConfiguration(cacheProperties).cacheManager();
    }

    @Test
    void getCache_withRegisteredCacheName_returnConfiguredCache() {
        assertNotNull(cacheManager.getCache(CacheConfiguration.EXCHANGE_RATE));
    }

    /**
     * Guards the {@code setCacheNames} call: in dynamic mode this returns a cache built from Caffeine's
     * default builder — unbounded and never expiring — instead of null.
     */
    @Test
    void getCache_withUnregisteredCacheName_returnNullInsteadOfCreatingCache() {
        assertNull(cacheManager.getCache(UNREGISTERED_CACHE_NAME));
    }
}
