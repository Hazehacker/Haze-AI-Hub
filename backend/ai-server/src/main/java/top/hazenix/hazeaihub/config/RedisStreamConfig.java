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

    /**
     * 每次从 Stream 批量读取的消息数量
     */
    private int batchSize = 5;

    /**
     * 工作线程池核心线程数（默认 CPU 核心数）
     */
    private int workerCoreSize = Runtime.getRuntime().availableProcessors();

    /**
     * 工作线程池最大线程数（默认 CPU 核心数 * 4，适配 I/O 密集型解析任务）
     */
    private int workerMaxSize = Runtime.getRuntime().availableProcessors() * 4;

    /**
     * 工作线程池队列容量
     */
    private int workerQueueCapacity = 50;

    /**
     * 非核心工作线程空闲存活时间(秒)
     */
    private long workerKeepAliveSeconds = 60;
}
