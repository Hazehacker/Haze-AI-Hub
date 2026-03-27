package top.hazenix.hazeaihub.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * SSE 事件推送服务
 */
@Slf4j
@Service
public class SseEmitterService {

    private static final long DEFAULT_TIMEOUT = 5 * 60 * 1000L; // 5 分钟
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * 创建 SSE 连接
     * @param mediaId 媒体文件ID
     * @return SseEmitter
     */
    public SseEmitter createEmitter(Long mediaId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);

        emitters.put(mediaId, emitter);

        emitter.onCompletion(() -> {
            log.debug("SSE连接完成: mediaId={}", mediaId);
            emitters.remove(mediaId);
        });

        emitter.onTimeout(() -> {
            log.debug("SSE连接超时: mediaId={}", mediaId);
            emitters.remove(mediaId);
        });

        emitter.onError(e -> {
            log.error("SSE连接错误: mediaId={}", mediaId, e);
            emitters.remove(mediaId);
        });

        log.info("SSE连接建立: mediaId={}", mediaId);
        return emitter;
    }

    /**
     * 发送进度事件
     */
    public void sendProgress(Long mediaId, int totalChunks, int parsedChunks, int percent) {
        String data = String.format(
                "{\"mediaId\":%d,\"status\":\"PARSING\",\"totalChunks\":%d,\"parsedChunks\":%d,\"percent\":%d}",
                mediaId, totalChunks, parsedChunks, percent
        );
        sendEvent(mediaId, "progress", data);
    }

    /**
     * 发送完成事件
     */
    public void sendComplete(Long mediaId, int totalChunks) {
        String data = String.format(
                "{\"mediaId\":%d,\"status\":\"PARSED\",\"totalChunks\":%d,\"parsedChunks\":%d}",
                mediaId, totalChunks, totalChunks
        );
        sendEvent(mediaId, "complete", data);
        complete(mediaId);
    }

    /**
     * 发送错误事件
     */
    public void sendError(Long mediaId, String error) {
        String data = String.format(
                "{\"mediaId\":%d,\"status\":\"FAILED\",\"error\":\"%s\"}",
                mediaId, escapeJson(error)
        );
        sendEvent(mediaId, "error", data);
        complete(mediaId);
    }

    /**
     * 发送事件
     */
    private void sendEvent(Long mediaId, String eventName, String data) {
        SseEmitter emitter = emitters.get(mediaId);
        if (emitter == null) {
            log.debug("SSE连接不存在，跳过事件: mediaId={}, event={}", mediaId, eventName);
            return;
        }

        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data));
            log.debug("SSE事件发送成功: mediaId={}, event={}", mediaId, eventName);
        } catch (IOException e) {
            log.error("SSE事件发送失败: mediaId={}, event={}", mediaId, eventName, e);
            emitters.remove(mediaId);
        }
    }

    /**
     * 完成连接
     */
    private void complete(Long mediaId) {
        SseEmitter emitter = emitters.get(mediaId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.debug("SSE连接已完成: mediaId={}", mediaId);
            }
            emitters.remove(mediaId);
        }
    }

    /**
     * 关闭连接
     */
    public void close(Long mediaId) {
        complete(mediaId);
    }

    /**
     * JSON 转义
     */
    private String escapeJson(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
