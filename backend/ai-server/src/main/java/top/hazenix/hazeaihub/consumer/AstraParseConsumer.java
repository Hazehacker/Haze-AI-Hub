package top.hazenix.hazeaihub.consumer;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import top.hazenix.hazeaihub.config.RedisStreamConfig;
import top.hazenix.hazeaihub.entity.KbMedia;
import top.hazenix.hazeaihub.entity.MediaStatus;
import top.hazenix.hazeaihub.mapper.KbMediaMapper;
import top.hazenix.hazeaihub.parser.FileParser;
import top.hazenix.hazeaihub.parser.FileParserFactory;
import top.hazenix.hazeaihub.service.SseEmitterService;
import top.hazenix.hazeaihub.vo.ChunkResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Astra 解析消费者
 * 从 Redis Stream 消费解析任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AstraParseConsumer {

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisStreamConfig streamConfig;
    private final KbMediaMapper mediaMapper;
    private final FileParserFactory parserFactory;
    private final SseEmitterService sseEmitterService;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executor;
    private String consumerName;

    @PostConstruct
    public void init() {
        running.set(true);
        consumerName = streamConfig.getConsumerNamePrefix() + UUID.randomUUID().toString().substring(0, 8);

        // 确保消费者组存在
        ensureConsumerGroup();

        // 启动消费者线程
        executor = Executors.newFixedThreadPool(2);
        executor.submit(this::consumeLoop);
        log.info("AstraParseConsumer 启动，consumerName={}", consumerName);
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
        log.info("AstraParseConsumer 关闭");
    }

    /**
     * 确保消费者组存在
     */
    private void ensureConsumerGroup() {
        String queueName = streamConfig.getQueueName();
        String consumerGroup = streamConfig.getConsumerGroup();

        try {
            // 尝试创建消费者组（如果已存在会抛异常，忽略即可）
            stringRedisTemplate.opsForStream().add(
                    StreamRecords.newRecord()
                            .in(queueName)
                            .of(Map.of("init", "x"))
            );
            stringRedisTemplate.opsForStream().createGroup(queueName, consumerGroup);
            log.info("创建消费者组成功: group={}", consumerGroup);
        } catch (Exception e) {
            // 消费者组已存在，忽略
            log.debug("消费者组已存在: group={}", consumerGroup);
        }
    }

    /**
     * 消费者主循环
     */
    private void consumeLoop() {
        while (running.get()) {
            try {
                // 阻塞读取消息
                List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream()
                        .read(
                                Consumer.from(streamConfig.getConsumerGroup(), consumerName),
                                StreamReadOptions.empty().count(1).block(Duration.ofMillis(streamConfig.getBlockTimeoutMs())),
                                StreamOffset.create(streamConfig.getQueueName(), ReadOffset.lastConsumed())
                        );

                if (records == null || records.isEmpty()) {
                    continue;
                }

                for (MapRecord<String, Object, Object> record : records) {
                    processMessage(record);
                }
            } catch (Exception e) {
                log.error("消费消息异常", e);
                if (running.get()) {
                    try {
                        Thread.sleep(1000); // 避免疯狂重试
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
    private void processMessage(MapRecord<String, Object, Object> record) {
        String messageId = record.getId().getValue();
        Map<Object, Object> fields = record.getValue();

        log.info("收到解析任务: messageId={}, fields={}", messageId, fields);

        ParseMessage message = parseMessage(fields);
        if (message == null) {
            log.warn("解析消息格式错误，忽略: messageId={}", messageId);
            acknowledgeMessage(messageId);
            return;
        }

        try {
            // 更新媒体状态为 PARSING
            updateMediaStatus(message.getMediaId(), MediaStatus.PARSING, null);

            // 发送进度事件
            sseEmitterService.sendProgress(message.getMediaId(), 0, 0, 0);

            // 获取解析器
            FileParser parser = parserFactory.getParser(message.getFileType());
            if (parser == null) {
                throw new RuntimeException("不支持的文件类型: " + message.getFileType());
            }

            // 执行解析
            List<ChunkResponse> chunks = parser.parse(message);

            // 保存分片
            // TODO: 保存到数据库

            // 更新媒体状态为 PARSED
            updateMediaStatus(message.getMediaId(), MediaStatus.PARSED, null, chunks.size());

            // 发送完成事件
            sseEmitterService.sendComplete(message.getMediaId(), chunks.size());

            // 确认消息
            acknowledgeMessage(messageId);
            log.info("解析任务完成: mediaId={}, chunks={}", message.getMediaId(), chunks.size());

        } catch (Exception e) {
            log.error("解析任务失败: mediaId={}", message.getMediaId(), e);
            handleFailure(message, messageId, e);
        }
    }

    /**
     * 处理失败
     */
    private void handleFailure(ParseMessage message, String messageId, Exception e) {
        int maxRetries = streamConfig.getMaxRetries();
        int currentRetry = message.getRetryCount() != null ? message.getRetryCount() : 0;

        if (currentRetry < maxRetries) {
            // 重新入队
            message.incrementRetry();
            requeueMessage(message);
            log.info("消息重新入队: mediaId={}, retryCount={}", message.getMediaId(), message.getRetryCount());
        } else {
            // 移入死信队列
            moveToDlq(message, e.getMessage());
            updateMediaStatus(message.getMediaId(), MediaStatus.FAILED, e.getMessage());
            sseEmitterService.sendError(message.getMediaId(), e.getMessage());
            log.warn("消息移入死信队列: mediaId={}", message.getMediaId());
        }

        acknowledgeMessage(messageId);
    }

    /**
     * 重新入队
     */
    private void requeueMessage(ParseMessage message) {
        try {
            Thread.sleep(streamConfig.getRetryDelayMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // XADD 重新入队
        stringRedisTemplate.opsForStream().add(
                StreamRecords.newRecord()
                        .in(streamConfig.getQueueName())
                        .of(Map.of(
                                "mediaId", String.valueOf(message.getMediaId()),
                                "libraryId", String.valueOf(message.getLibraryId()),
                                "fileType", message.getFileType(),
                                "ossKey", message.getOssKey(),
                                "retryCount", String.valueOf(message.getRetryCount()),
                                "createdAt", String.valueOf(message.getCreatedAt())
                        ))
        );
    }

    /**
     * 移入死信队列
     */
    private void moveToDlq(ParseMessage message, String error) {
        stringRedisTemplate.opsForStream().add(
                StreamRecords.newRecord()
                        .in(streamConfig.getDlqName())
                        .of(Map.of(
                                "mediaId", String.valueOf(message.getMediaId()),
                                "libraryId", String.valueOf(message.getLibraryId()),
                                "fileType", message.getFileType(),
                                "ossKey", message.getOssKey(),
                                "retryCount", String.valueOf(message.getRetryCount()),
                                "createdAt", String.valueOf(message.getCreatedAt()),
                                "error", error != null ? error : "Unknown error"
                        ))
        );
    }

    /**
     * 确认消息
     */
    private void acknowledgeMessage(String messageId) {
        stringRedisTemplate.opsForStream().acknowledge(
                streamConfig.getQueueName(),
                streamConfig.getConsumerGroup(),
                messageId
        );
    }

    /**
     * 解析消息
     */
    private ParseMessage parseMessage(Map<Object, Object> fields) {
        try {
            return ParseMessage.builder()
                    .mediaId(Long.parseLong(String.valueOf(fields.get("mediaId"))))
                    .libraryId(Long.parseLong(String.valueOf(fields.get("libraryId"))))
                    .fileType(String.valueOf(fields.get("fileType")))
                    .ossKey(String.valueOf(fields.get("ossKey")))
                    .retryCount(fields.get("retryCount") != null ?
                            Integer.parseInt(String.valueOf(fields.get("retryCount"))) : 0)
                    .createdAt(fields.get("createdAt") != null ?
                            Long.parseLong(String.valueOf(fields.get("createdAt"))) : System.currentTimeMillis())
                    .build();
        } catch (Exception e) {
            log.error("解析消息失败", e);
            return null;
        }
    }

    /**
     * 更新媒体状态
     */
    private void updateMediaStatus(Long mediaId, MediaStatus status, String errorMessage) {
        updateMediaStatus(mediaId, status, errorMessage, 0);
    }

    private void updateMediaStatus(Long mediaId, MediaStatus status, String errorMessage, int totalChunks) {
        KbMedia media = mediaMapper.selectById(mediaId);
        if (media != null) {
            media.setStatus(status.getCode());
            if (errorMessage != null) {
                media.setErrorMessage(errorMessage);
            }
            if (totalChunks > 0) {
                media.setTotalChunks(totalChunks);
                media.setParsedChunks(totalChunks);
            }
            mediaMapper.updateById(media);
        }
    }
}
