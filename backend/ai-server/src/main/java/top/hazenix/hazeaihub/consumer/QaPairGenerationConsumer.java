package top.hazenix.hazeaihub.consumer;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamCreateGroupArgs;
import org.redisson.api.stream.StreamReadGroupArgs;
import top.hazenix.hazeaihub.bo.QaPairMessage;
import top.hazenix.hazeaihub.config.QaRedisStreamConfig;
import top.hazenix.hazeaihub.producer.QaPairStreamProducer;
import top.hazenix.hazeaihub.service.IQaPairGenerationService;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * QA对生成消费者 - 使用 Redisson RStream
 * 从 Redis Stream 消费 QA 生成任务
 */
@Slf4j
@org.springframework.stereotype.Component
@RequiredArgsConstructor
public class QaPairGenerationConsumer {

    private final RedissonClient redissonClient;
    private final QaRedisStreamConfig streamConfig;
    private final IQaPairGenerationService qaPairGenerationService;
    private final QaPairStreamProducer streamProducer;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executor = new ThreadPoolExecutor(
            2, 2, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(100)
    );
    private String consumerName;
    private RStream<String, String> stream;

    @PostConstruct
    public void init() {
        running.set(true);
        consumerName = streamConfig.getConsumerNamePrefix() + UUID.randomUUID().toString().substring(0, 8);

        stream = redissonClient.getStream(streamConfig.getQueueName());

        // 确保消费者组存在
        ensureConsumerGroup();

        // 启动消费者线程
        executor.submit(this::consumeLoop);
        log.info("QaPairGenerationConsumer 启动，consumerName={}", consumerName);
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("QaPairGenerationConsumer 关闭");
    }

    /**
     * 确保消费者组存在
     */
    private void ensureConsumerGroup() {
        try {
            stream.createGroup(StreamCreateGroupArgs.name(streamConfig.getConsumerGroup()).makeStream());
            log.info("创建QA消费者组成功: group={}", streamConfig.getConsumerGroup());
        } catch (Exception e) {
            log.debug("QA消费者组已存在: group={}, error={}", streamConfig.getConsumerGroup(), e.getMessage());
        }
    }

    /**
     * 消费者主循环
     */
    private void consumeLoop() {
        while (running.get()) {
            try {
                StreamReadGroupArgs args = StreamReadGroupArgs.neverDelivered()
                        .count(1)
                        .timeout(Duration.ofMillis(streamConfig.getBlockTimeoutMs()));

                Map<StreamMessageId, Map<String, String>> entries = stream.readGroup(
                        streamConfig.getConsumerGroup(),
                        consumerName,
                        args
                );

                if (entries == null || entries.isEmpty()) {
                    continue;
                }

                for (Map.Entry<StreamMessageId, Map<String, String>> entry : entries.entrySet()) {
                    processMessage(entry.getKey(), entry.getValue());
                }
            } catch (Exception e) {
                log.error("消费QA消息异常", e);
                if (running.get()) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    /**
     * 处理消息
     */
    private void processMessage(StreamMessageId messageId, Map<String, String> fields) {
        log.info("收到QA生成任务: messageId={}, fields={}", messageId, fields);

        QaPairMessage message = parseMessage(fields);
        if (message == null) {
            log.warn("QA消息格式错误，忽略: messageId={}", messageId);
            stream.ack(streamConfig.getConsumerGroup(), messageId);
            return;
        }

        try {
            // 调用 QA 生成服务
            qaPairGenerationService.generateQaPairs(message.getMediaId());

            // 确认消息
            stream.ack(streamConfig.getConsumerGroup(), messageId);
            log.info("QA生成任务完成: mediaId={}", message.getMediaId());

        } catch (Exception e) {
            log.error("QA生成任务失败: mediaId={}", message.getMediaId(), e);
            handleFailure(message, messageId, e);
        }
    }

    /**
     * 处理失败 - 使用延迟队列实现优雅重试
     */
    private void handleFailure(QaPairMessage message, StreamMessageId messageId, Exception e) {
        int maxRetries = streamConfig.getMaxRetries();
        int currentRetry = message.getRetryCount() != null ? message.getRetryCount() : 0;

        // 先确认原消息
        stream.ack(streamConfig.getConsumerGroup(), messageId);

        if (currentRetry < maxRetries) {
            // 增加重试次数并发送到延迟队列
            message.incrementRetry();
            streamProducer.sendDelayedRetry(message, streamConfig.getRetryDelayMs());
            log.info("QA消息进入延迟重试队列: mediaId={}, retryCount={}, delayMs={}",
                    message.getMediaId(), message.getRetryCount(), streamConfig.getRetryDelayMs());
        } else {
            // 移入死信队列
            streamProducer.sendToDlq(message, e.getMessage());
            log.warn("QA消息移入死信队列: mediaId={}", message.getMediaId());
        }
    }

    /**
     * 解析消息
     */
    private QaPairMessage parseMessage(Map<String, String> fields) {
        try {
            return QaPairMessage.builder()
                    .mediaId(Long.parseLong(fields.get("mediaId")))
                    .libraryId(Long.parseLong(fields.get("libraryId")))
                    .retryCount(fields.get("retryCount") != null ?
                            Integer.parseInt(fields.get("retryCount")) : 0)
                    .createdAt(fields.get("createdAt") != null ?
                            Long.parseLong(fields.get("createdAt")) : System.currentTimeMillis())
                    .build();
        } catch (Exception e) {
            log.error("解析QA消息失败", e);
            return null;
        }
    }
}
