package com.groundwork.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfiguration implements CachingConfigurer {
    private static final Logger log = LoggerFactory.getLogger(CacheConfiguration.class);

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                warn("read", cache, exception);
            }
            @Override public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                warn("write", cache, exception);
            }
            @Override public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                warn("evict", cache, exception);
            }
            @Override public void handleCacheClearError(RuntimeException exception, Cache cache) {
                warn("clear", cache, exception);
            }
        };
    }

    private void warn(String operation, Cache cache, RuntimeException exception) {
        log.warn("Cache {} failed for {}; continuing without cache: {}", operation, cache.getName(), exception.getMessage());
    }
}
