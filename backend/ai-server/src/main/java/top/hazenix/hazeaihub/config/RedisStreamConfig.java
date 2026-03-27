package top.hazenix.hazeaihub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Redis Stream 配置属性
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "astra.redis-stream")
public class RedisStreamConfig {

    /**
     * 主解析队列名称
     */
    private String queueName = "astra:parse:queue";

    /**
     * 死信队列名称
     */
    private String dlqName = "astra:parse:dlq";

    /**
     * 消费者组名称
     */
    private String consumerGroup = "astra-parse-group";

    /**
     * 消费者名称前缀
     */
    private String consumerNamePrefix = "consumer-";

    /**
     * 最大重试次数
     */
    private int maxRetries = 3;

    /**
     * 重试延迟(毫秒)
     */
    private long retryDelayMs = 5000;

    /**
     * 阻塞等待时间(毫秒)
     */
    private long blockTimeoutMs = 5000;
}
