package top.hazenix.hazeaihub.consumer;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamCreateGroupArgs;
import org.redisson.api.stream.StreamReadGroupArgs;
import top.hazenix.hazeaihub.bo.ParseMessage;
import top.hazenix.hazeaihub.config.RedisStreamConfig;
import top.hazenix.hazeaihub.entity.KbMedia;
import top.hazenix.hazeaihub.entity.MediaStatus;
import top.hazenix.hazeaihub.mapper.KbMediaMapper;
import top.hazenix.hazeaihub.parser.FileParser;
import top.hazenix.hazeaihub.parser.FileParserFactory;
import top.hazenix.hazeaihub.service.SseEmitterService;
import top.hazenix.hazeaihub.producer.RedissonStreamProducer;
import top.hazenix.hazeaihub.producer.QaPairStreamProducer;
import top.hazenix.hazeaihub.bo.QaPairMessage;
import top.hazenix.hazeaihub.vo.ChunkResponse;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Astra 解析消费者 - 使用 Redisson RStream
 * 从 Redis Stream 消费解析任务
 */
@Slf4j
@org.springframework.stereotype.Component
@RequiredArgsConstructor
public class AstraParseConsumer {

    private final RedissonClient redissonClient;
    private final RedisStreamConfig streamConfig;
    private final KbMediaMapper mediaMapper;
    private final FileParserFactory parserFactory;
    private final SseEmitterService sseEmitterService;
    private final RedissonStreamProducer streamProducer;
    private final QaPairStreamProducer qaPairStreamProducer;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executor = new ThreadPoolExecutor(
            2, 2, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(100)
    );
    private String consumerName;
    private RStream<String, String> stream;
    private RDelayedQueue<String> delayedQueue;

    @PostConstruct
    public void init() {
        running.set(true);
        consumerName = streamConfig.getConsumerNamePrefix() + UUID.randomUUID().toString().substring(0, 8);

        stream = redissonClient.getStream(streamConfig.getQueueName());
        delayedQueue = redissonClient.getDelayedQueue(redissonClient.getQueue(streamConfig.getQueueName()));

        // 确保消费者组存在
        ensureConsumerGroup();

        // 启动消费者线程
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
        try {
            stream.createGroup(StreamCreateGroupArgs.name(streamConfig.getConsumerGroup()).makeStream());
            log.info("创建消费者组成功: group={}", streamConfig.getConsumerGroup());
        } catch (Exception e) {
            log.debug("消费者组已存在: group={}, error={}", streamConfig.getConsumerGroup(), e.getMessage());
        }
    }

    /**
     * 消费者主循环
     */
    private void consumeLoop() {
        while (running.get()) {
            try {
                // 阻塞读取消息，使用 Redisson 的消费者组 API
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
                log.error("消费消息异常", e);
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
        log.info("收到解析任务: messageId={}, fields={}", messageId, fields);

        ParseMessage message = parseMessage(fields);
        if (message == null) {
            log.warn("解析消息格式错误，忽略: messageId={}", messageId);
            stream.ack(streamConfig.getConsumerGroup(), messageId);
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

            // 更新解析完成状态
            int totalChunks = chunks.size();
            updateMediaStatus(message.getMediaId(), MediaStatus.PARSED, null, totalChunks);
            sseEmitterService.sendComplete(message.getMediaId(), totalChunks);

            // 确认消息
            stream.ack(streamConfig.getConsumerGroup(), messageId);
            log.info("解析任务完成: mediaId={}", message.getMediaId());

            // 发送 QA 生成任务到队列
            sendQaTask(message);

        } catch (Exception e) {
            log.error("解析任务失败: mediaId={}", message.getMediaId(), e);
            handleFailure(message, messageId, e);
        }
    }

    /**
     * 处理失败 - 使用延迟队列实现优雅重试
     */
    private void handleFailure(ParseMessage message, StreamMessageId messageId, Exception e) {
        int maxRetries = streamConfig.getMaxRetries();
        int currentRetry = message.getRetryCount() != null ? message.getRetryCount() : 0;

        // 先确认原消息
        stream.ack(streamConfig.getConsumerGroup(), messageId);

        if (currentRetry < maxRetries) {
            // 增加重试次数并发送到延迟队列
            message.incrementRetry();
            streamProducer.sendDelayedRetry(message, streamConfig.getRetryDelayMs());
            log.info("消息进入延迟重试队列: mediaId={}, retryCount={}, delayMs={}",
                    message.getMediaId(), message.getRetryCount(), streamConfig.getRetryDelayMs());
        } else {
            // 移入死信队列
            streamProducer.sendToDlq(message, e.getMessage());
            updateMediaStatus(message.getMediaId(), MediaStatus.FAILED, e.getMessage());
            sseEmitterService.sendError(message.getMediaId(), e.getMessage());
            log.warn("消息移入死信队列: mediaId={}", message.getMediaId());
        }
    }


    /**
     * 解析消息
     */
    private ParseMessage parseMessage(Map<String, String> fields) {
        try {
            return ParseMessage.builder()
                    .mediaId(Long.parseLong(fields.get("mediaId")))
                    .libraryId(Long.parseLong(fields.get("libraryId")))
                    .fileType(fields.get("fileType"))
                    .ossKey(fields.get("ossKey"))
                    .retryCount(fields.get("retryCount") != null ?
                            Integer.parseInt(fields.get("retryCount")) : 0)
                    .createdAt(fields.get("createdAt") != null ?
                            Long.parseLong(fields.get("createdAt")) : System.currentTimeMillis())
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

    /**
     * 发送 QA 生成任务
     */
    private void sendQaTask(ParseMessage message) {
        try {
            QaPairMessage qaMessage = QaPairMessage.builder()
                    .mediaId(message.getMediaId())
                    .libraryId(message.getLibraryId())
                    .retryCount(0)
                    .createdAt(System.currentTimeMillis())
                    .build();
            qaPairStreamProducer.sendQaTask(qaMessage);
            log.info("QA生成任务已发送: mediaId={}", message.getMediaId());
        } catch (Exception e) {
            log.error("发送QA生成任务失败: mediaId={}", message.getMediaId(), e);
        }
    }
}