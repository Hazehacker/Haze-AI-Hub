package top.hazenix.hazeaihub.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 配置类
 * Redisson 会自动从 application.yaml 的 spring.data.redis 配置中读取连接参数
 */
@Slf4j
@Configuration
public class RedissonConfiguration {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        log.info("RedissonClient 初始化");
        return Redisson.create();
    }
}
