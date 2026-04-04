package top.hazenix.hazeaihub.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamAddArgs;
import org.springframework.stereotype.Component;
import top.hazenix.hazeaihub.bo.ParseMessage;
import top.hazenix.hazeaihub.config.RedisStreamConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Redisson Stream 消息生产者
 * 负责发送解析任务消息、延迟重试消息、死信消息
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedissonStreamProducer {

    private final RedissonClient redissonClient;
    private final RedisStreamConfig streamConfig;

    /**
     * 发送解析任务到主队列
     */
    public void sendParseTask(ParseMessage message) {
        RStream<String, String> stream = redissonClient.getStream(streamConfig.getQueueName());

        Map<String, String> fields = toMap(message);
        StreamAddArgs<String, String> args = StreamAddArgs.entries(fields);
        StreamMessageId messageId = stream.add(args);

        log.info("发送解析任务成功: mediaId={}, messageId={}", message.getMediaId(), messageId);
    }

    /**
     * 发送重试消息到主队列
     * 消息立即入队，消费者的消费间隔通过指数退避控制
     */
    public void sendDelayedRetry(ParseMessage message, long delayMs) {
        RStream<String, String> stream = redissonClient.getStream(streamConfig.getQueueName());
        Map<String, String> fields = toMap(message);
        StreamMessageId messageId = stream.add(StreamAddArgs.entries(fields));
        log.info("发送重试消息: mediaId={}, retryCount={}, messageId={}",
                message.getMediaId(), message.getRetryCount(), messageId);
    }

    /**
     * 发送消息到死信队列
     */
    public void sendToDlq(ParseMessage message, String error) {
        RStream<String, String> dlqStream = redissonClient.getStream(streamConfig.getDlqName());

        Map<String, String> fields = toMap(message);
        fields.put("error", error != null ? error : "Unknown error");
        
        StreamAddArgs<String, String> args = StreamAddArgs.entries(fields);
        StreamMessageId messageId = dlqStream.add(args);

        log.warn("消息移入死信队列: mediaId={}, messageId={}, error={}",
                message.getMediaId(), messageId, error);
    }

    private Map<String, String> toMap(ParseMessage message) {
        if (message.getMediaId() == null || message.getLibraryId() == null) {
            throw new IllegalArgumentException("mediaId and libraryId are required");
        }
        Map<String, String> map = new HashMap<>();
        map.put("mediaId", String.valueOf(message.getMediaId()));
        map.put("libraryId", String.valueOf(message.getLibraryId()));
        map.put("fileType", message.getFileType() != null ? message.getFileType() : "");
        map.put("ossKey", message.getOssKey() != null ? message.getOssKey() : "");
        map.put("retryCount", String.valueOf(message.getRetryCount() != null ? message.getRetryCount() : 0));
        map.put("createdAt", String.valueOf(message.getCreatedAt() != null ? message.getCreatedAt() : System.currentTimeMillis()));
        return map;
    }
}