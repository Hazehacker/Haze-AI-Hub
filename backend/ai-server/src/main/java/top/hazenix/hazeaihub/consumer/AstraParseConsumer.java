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
import top.hazenix.hazeaihub.bo.ParseMessage;
import top.hazenix.hazeaihub.config.RedisStreamConfig;
import top.hazenix.hazeaihub.entity.KbMedia;
import top.hazenix.hazeaihub.entity.MediaStatus;
import top.hazenix.hazeaihub.mapper.KbMediaMapper;
import top.hazenix.hazeaihub.parser.FileParser;
import top.hazenix.hazeaihub.parser.FileParserFactory;
import top.hazenix.hazeaihub.service.SseEmitterService;
import top.hazenix.hazeaihub.producer.RedissonStreamProducer;
import top.hazenix.hazeaihub.vo.ChunkResponse;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
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

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger errorCount = new AtomicInteger(0);

    /** 消费循环线程（单线程，专职拉取消息） */
    private ExecutorService consumerExecutor;
    /** 解析工作线程池（弹性扩缩容，执行实际解析任务） */
    private ThreadPoolExecutor workerPool;
    /** 重试调度器（延迟调度，指数退避后重新入队） */
    private ScheduledExecutorService retryScheduler;

    private String consumerName;
    private RStream<String, String> stream;

    @PostConstruct
    public void init() {
        // 1. 消费循环线程：单线程，专职从 Redis Stream 拉取消息
        consumerExecutor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1),
                r -> {
                    Thread t = new Thread(r, "astra-consumer-loop");
                    t.setDaemon(true);
                    return t;
                }
        );

        // 2. 解析工作线程池：弹性扩缩容，处理 I/O 密集型文件解析
        workerPool = new ThreadPoolExecutor(
                streamConfig.getWorkerCoreSize(),
                streamConfig.getWorkerMaxSize(),
                streamConfig.getWorkerKeepAliveSeconds(), TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(streamConfig.getWorkerQueueCapacity()),
                new ThreadFactory() {
                    private final AtomicInteger seq = new AtomicInteger(0);
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "astra-parser-" + seq.getAndIncrement());
                        t.setDaemon(true);
                        return t;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy() // 队列满时由消费者线程执行，自动反压限流
        );
        // 允许核心线程超时回收，实现真正的弹性缩容
        workerPool.allowCoreThreadTimeOut(true);

        // 3. 重试调度器：负责延迟指数退避的时间后将失败消息重新投递到主队列
        retryScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "astra-retry-scheduler");
            t.setDaemon(true);
            return t;
        });

        consumerName = streamConfig.getConsumerNamePrefix() + UUID.randomUUID().toString().substring(0, 8);
        stream = redissonClient.getStream(streamConfig.getQueueName());

        ensureConsumerGroup();

        // 启动消费循环
        running.set(true);
        consumerExecutor.submit(this::consumeLoop);
        log.info("AstraParseConsumer 启动, consumerName={}, workerPool[core={}, max={}, queue={}]",
                consumerName, streamConfig.getWorkerCoreSize(),
                streamConfig.getWorkerMaxSize(), streamConfig.getWorkerQueueCapacity());
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        log.info("AstraParseConsumer 开始关闭...");

        // 先关闭消费循环，停止拉取新消息
        shutdownExecutor(consumerExecutor, "consumerExecutor", 5);
        // 再关闭工作线程池，等待正在执行的解析任务完成
        shutdownExecutor(workerPool, "workerPool", 30);
        // 最后关闭重试调度器（等待已调度的重试任务完成投递）
        shutdownExecutor(retryScheduler, "retryScheduler", 10);

        log.info("AstraParseConsumer 已关闭");
    }

    private void shutdownExecutor(ExecutorService executor, String name, int timeoutSeconds) {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                log.warn("{} 未能在 {}s 内优雅关闭，强制终止", name, timeoutSeconds);
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 确保消费者组存在
     */
    private void ensureConsumerGroup() {
        try {
            stream.createGroup(StreamCreateGroupArgs.name(streamConfig.getConsumerGroup()));
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
                // 阻塞读取消息，neverDelivered 确保只读取从未被消费过的消息
                // 避免同一消息被多个消费者重复处理
                StreamReadGroupArgs args = StreamReadGroupArgs.neverDelivered()
                        .count(streamConfig.getBatchSize())
                        .timeout(Duration.ofMillis(streamConfig.getBlockTimeoutMs()));

                Map<StreamMessageId, Map<String, String>> entries = stream.readGroup(
                        streamConfig.getConsumerGroup(),
                        consumerName,
                        args
                );

                if (entries == null || entries.isEmpty()) {
                    continue;
                }

                errorCount.set(0);
                for (Map.Entry<StreamMessageId, Map<String, String>> entry : entries.entrySet()) {
                    final StreamMessageId msgId = entry.getKey();
                    final Map<String, String> fields = entry.getValue();
                    // 将解析任务提交到工作线程池并行处理
                    workerPool.submit(() -> processMessage(msgId, fields));
                }
            } catch (Exception e) {
                log.error("消费消息异常", e);
                if (running.get()) {
                    int errors = errorCount.incrementAndGet();
                    try {
                        // 指数退避：1s, 2s, 4s, 8s, max 30s
                        Thread.sleep(Math.min(30000L, 1000L << Math.min(errors, 5)));
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

        } catch (Exception e) {
            log.error("解析任务失败: mediaId={}", message.getMediaId(), e);
            handleFailure(message, messageId, e);
        }
    }

    /**
     * 处理失败 - 指数退避延迟重试
     * 退避策略：retryDelayMs * 2^(retryCount-1)，即 5s, 10s, 20s（默认 maxRetries=3）
     */
    private void handleFailure(ParseMessage message, StreamMessageId messageId, Exception e) {
        int maxRetries = streamConfig.getMaxRetries();
        int currentRetry = message.getRetryCount() != null ? message.getRetryCount() : 0;

        // 先确认原消息，避免被消费者组重复投递
        stream.ack(streamConfig.getConsumerGroup(), messageId);

        if (currentRetry < maxRetries) {
            message.incrementRetry();
            // 指数退避：baseDelay * 2^(retryCount-1)
            long delay = streamConfig.getRetryDelayMs() * (1L << (message.getRetryCount() - 1));
            retryScheduler.schedule(
                    () -> {
                        try {
                            streamProducer.sendParseTask(message);
                            log.info("重试消息已重新投递到主队列: mediaId={}, retryCount={}",
                                    message.getMediaId(), message.getRetryCount());
                        } catch (Exception ex) {
                            log.error("重试投递失败，直接进入死信队列: mediaId={}", message.getMediaId(), ex);
                            streamProducer.sendToDlq(message, ex.getMessage());
                            updateMediaStatus(message.getMediaId(), MediaStatus.FAILED, ex.getMessage());
                            sseEmitterService.sendError(message.getMediaId(), ex.getMessage());
                        }
                    },
                    delay, TimeUnit.MILLISECONDS
            );
            log.info("消息将在 {}ms 后重试: mediaId={}, retryCount={}/{}",
                    delay, message.getMediaId(), message.getRetryCount(), maxRetries);
        } else {
            // 超过最大重试次数，移入死信队列
            streamProducer.sendToDlq(message, e.getMessage());
            updateMediaStatus(message.getMediaId(), MediaStatus.FAILED, e.getMessage());
            sseEmitterService.sendError(message.getMediaId(), e.getMessage());
            log.warn("消息超过最大重试次数，移入死信队列: mediaId={}, retries={}",
                    message.getMediaId(), currentRetry);
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
}