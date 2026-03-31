package top.hazenix.hazeaihub.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamAddArgs;
import org.springframework.stereotype.Component;
import top.hazenix.hazeaihub.bo.QaPairMessage;
import top.hazenix.hazeaihub.config.QaRedisStreamConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * QA对生成消息生产者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QaPairStreamProducer {

    private final RedissonClient redissonClient;
    private final QaRedisStreamConfig streamConfig;

    /**
     * 发送QA生成任务到队列
     */
    public void sendQaTask(QaPairMessage message) {
        RStream<String, String> stream = redissonClient.getStream(streamConfig.getQueueName());

        Map<String, String> fields = toMap(message);
        StreamAddArgs<String, String> args = StreamAddArgs.entries(fields);
        StreamMessageId messageId = stream.add(args);

        log.info("发送QA生成任务成功: mediaId={}, messageId={}", message.getMediaId(), messageId);
    }

    /**
     * 发送延迟重试消息
     */
    public void sendDelayedRetry(QaPairMessage message, long delayMs) {
        RStream<String, String> stream = redissonClient.getStream(streamConfig.getQueueName());

        Map<String, String> fields = toMap(message);
        StreamAddArgs<String, String> args = StreamAddArgs.entries(fields);
        StreamMessageId messageId = stream.add(args);

        log.info("发送QA延迟重试消息: mediaId={}, delayMs={}, messageId={}",
                message.getMediaId(), delayMs, messageId);
    }

    /**
     * 发送消息到死信队列
     */
    public void sendToDlq(QaPairMessage message, String error) {
        RStream<String, String> dlqStream = redissonClient.getStream(streamConfig.getDlqName());

        Map<String, String> fields = toMap(message);
        fields.put("error", error != null ? error : "Unknown error");

        StreamAddArgs<String, String> args = StreamAddArgs.entries(fields);
        StreamMessageId messageId = dlqStream.add(args);

        log.warn("QA消息移入死信队列: mediaId={}, messageId={}, error={}",
                message.getMediaId(), messageId, error);
    }

    private Map<String, String> toMap(QaPairMessage message) {
        Map<String, String> map = new HashMap<>();
        map.put("mediaId", String.valueOf(message.getMediaId()));
        map.put("libraryId", String.valueOf(message.getLibraryId()));
        map.put("retryCount", String.valueOf(message.getRetryCount() != null ? message.getRetryCount() : 0));
        map.put("createdAt", String.valueOf(message.getCreatedAt() != null ? message.getCreatedAt() : System.currentTimeMillis()));
        return map;
    }
}
