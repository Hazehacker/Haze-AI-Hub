package top.hazenix.hazeaihub.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.TimeUnit;

/**
 * 缓存配置类
 */
@Configuration
public class CacheConfiguration {

    /**
     * Caffeine 本地缓存管理器
     */
    @Bean
    @Primary
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.registerCustomCache(
                "models",
                Caffeine.newBuilder()
                        .maximumSize(100)
                        .expireAfterWrite(12, TimeUnit.HOURS)
                        .recordStats()
                        .build()
        );
        cacheManager.registerCustomCache(
                "groups",
                Caffeine.newBuilder()
                        .maximumSize(500)
                        .expireAfterWrite(30, TimeUnit.MINUTES)
                        .recordStats()
                        .build()
        );
        cacheManager.registerCustomCache(
                "libraries",
                Caffeine.newBuilder()
                        .maximumSize(500)
                        .expireAfterWrite(30, TimeUnit.MINUTES)
                        .recordStats()
                        .build()
        );
        return cacheManager;
    }
}