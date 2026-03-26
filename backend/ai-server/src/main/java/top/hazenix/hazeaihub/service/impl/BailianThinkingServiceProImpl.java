package top.hazenix.hazeaihub.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import top.hazenix.hazeaihub.constant.MessageConstant;
import top.hazenix.hazeaihub.context.BaseContext;
import top.hazenix.hazeaihub.entity.ChatMessage;
import top.hazenix.hazeaihub.entity.ChatSession;
import top.hazenix.hazeaihub.mapper.ChatMessageMapper;
import top.hazenix.hazeaihub.mapper.ChatSessionMapper;
import top.hazenix.hazeaihub.service.IBailianThinkingService;
import top.hazenix.hazeaihub.service.IChatMessageService;
import top.hazenix.hazeaihub.service.IChatSessionService;
import top.hazenix.hazeaihub.service.ITitleGenerationService;
import top.hazenix.hazeaihub.service.impl.stream.MessageBuilder;
import top.hazenix.hazeaihub.service.impl.stream.StreamChunkParser;

import java.net.ConnectException;
import java.net.SocketException;
import java.nio.channels.UnresolvedAddressException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 支持思考过程的聊天服务（Pro版本）
 * 使用 PostgreSQL 持久化存储会话数据和消息数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BailianThinkingServiceProImpl implements IBailianThinkingService {

    private final WebClient webClient;
    private final IChatSessionService chatSessionService;
    private final IChatMessageService chatMessageService;
    private final ITitleGenerationService titleGenerationService;
    private final StreamChunkParser streamChunkParser;
    private final MessageBuilder messageBuilder;

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    // 默认模型
    @Value("${ai.bailian.default.model:deepseek-r1}")
    private String model;

    @Override
    @Transactional
    public Flux<Map<String, String>> chatWithThinking(String userMessage,
                                                       Boolean enableThinking,
                                                       Integer thinkingBudget,
                                                       Long sessionId,
                                                       Long groupId,
                                                       String modelName) {

        log.info("开始处理聊天请求: sessionId={}, userMessage={}", sessionId, userMessage);

        boolean isNewSession = (sessionId == null);
        Long[] finalSessionId = {sessionId};

        // 创建会话并保存用户消息
        // a. 如果不是已有对话，就创建会话
        finalSessionId[0] = createSessionIfNeeded(isNewSession, finalSessionId[0], groupId);
        chatMessageService.saveUserMessage(finalSessionId[0], userMessage);

        // 构建请求
        String selectedModel = (modelName != null && !modelName.trim().isEmpty()) ? modelName : model;
        List<Map<String, String>> messages = buildMessages(finalSessionId[0], userMessage);
        Map<String, Object> requestBody = messageBuilder.buildRequestBody(
                selectedModel, messages, enableThinking, thinkingBudget);

        // 构建初始事件流
        Flux<Map<String, String>> initialFlux = buildSessionCreatedFlux(isNewSession, finalSessionId[0]);

        return initialFlux.concatWith(streamChat(selectedModel, requestBody, finalSessionId[0],
                isNewSession, enableThinking, thinkingBudget));
    }

    private Long createSessionIfNeeded(boolean isNewSession, Long sessionId, Long groupId) {
        if (!isNewSession) {
            return sessionId;
        }

        try {
            Long userId = BaseContext.getCurrentId();
            if (userId == null) {
                throw new RuntimeException("用户未登录");
            }

            ChatSession newSession = chatSessionService.createSession(userId, "chat", "新对话");
            Long newSessionId = newSession.getId();

            if (groupId != null) {
                chatSessionService.updateSession(newSessionId, null, groupId);
            }

            log.info("创建新会话成功: sessionId={}", newSessionId);
            return newSessionId;

        } catch (Exception e) {
            log.error("创建会话失败", e);
            throw new RuntimeException("创建会话失败: " + e.getMessage());
        }
    }



    private List<Map<String, String>> buildMessages(Long sessionId, String userMessage) {
        try {
            List<ChatMessage> historyMessages = chatMessageService.getMessagesBySessionId(sessionId, 10);
            return messageBuilder.buildApiMessages(historyMessages, userMessage);
        } catch (Exception e) {
            log.warn("获取历史消息失败: {}", e.getMessage());
            throw new RuntimeException("获取历史消息失败");
        }
    }

    private Flux<Map<String, String>> buildSessionCreatedFlux(boolean isNewSession, Long sessionId) {
        if (!isNewSession) {
            return Flux.empty();
        }
        Map<String, String> event = new HashMap<>();
        // 会话创建事件，携带会话ID
        event.put("type", "session-created");
        event.put("content", String.valueOf(sessionId));
        return Flux.just(event);
    }

    private Flux<Map<String, String>> streamChat(String model,
                                                   Map<String, Object> requestBody,
                                                   Long sessionId,
                                                   boolean isNewSession,
                                                   Boolean enableThinking,
                                                   Integer thinkingBudget) {

        StringBuilder assistantResponse = new StringBuilder();
        StringBuilder thinkingContent = new StringBuilder();
        LocalDateTime[] startTime = {LocalDateTime.now()};
        LocalDateTime[] endTime = {null};
        boolean[] thinkingEnded = {false};
        AtomicInteger retryCount = new AtomicInteger(0);
        int maxRetries = 2;

        return webClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .flatMap(this::splitLines)
                .filter(line -> line.startsWith("data: ") && !line.contains("[DONE]"))
                .map(line -> line.substring(6).trim())
                .filter(json -> !json.isEmpty())
                .mapNotNull(json -> streamChunkParser.parse(json))
                .doOnNext(result -> collectContent(result, assistantResponse, thinkingContent,
                        thinkingEnded, endTime, startTime))
                .doOnComplete(() -> onStreamComplete(sessionId, assistantResponse, thinkingContent,
                        startTime[0], endTime[0], model, enableThinking, thinkingBudget, isNewSession))
                .doOnError(error -> log.error("API调用出错 (重试: {}/{})", retryCount.get(), maxRetries, error))
                .retry(maxRetries)
                .onErrorResume(error -> handleError(error));
    }

    private Flux<String> splitLines(DataBuffer dataBuffer) {
        try {
            byte[] bytes = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(bytes);
            String content = new String(bytes, StandardCharsets.UTF_8);
            List<String> lines = new ArrayList<>();
            for (String line : content.split("\\r?\\n")) {
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
            return Flux.fromIterable(lines);
        } finally {
            DataBufferUtils.release(dataBuffer);
        }
    }

    private void collectContent(Map<String, String> result,
                                 StringBuilder assistantResponse,
                                 StringBuilder thinkingContent,
                                 boolean[] thinkingEnded,
                                 LocalDateTime[] endTime,
                                 LocalDateTime[] startTime) {
        if (result == null) {
            return;
        }
        String type = result.get("type");
        String content = result.get("content");
        if (content != null) {
            if ("answer".equals(type)) {
                if (!thinkingEnded[0]) {
                    thinkingEnded[0] = true;
                    endTime[0] = LocalDateTime.now();
                }
                assistantResponse.append(content);
            } else if ("thinking".equals(type)) {
                thinkingContent.append(content);
            }
        }
    }

    @Transactional
    private void onStreamComplete(Long sessionId,
                                   StringBuilder assistantResponse,
                                   StringBuilder thinkingContent,
                                   LocalDateTime startTime,
                                   LocalDateTime endTime,
                                   String model,
                                   Boolean enableThinking,
                                   Integer thinkingBudget,
                                   boolean isNewSession) {
        if (assistantResponse.length() <= 0) {
            return;
        }

        try {
            Long duration = null;
            if (endTime != null) {
                duration = Duration.between(startTime, endTime).toMillis();
            }

            Map<String, Object> metadata = messageBuilder.buildMetadata(
                    model, enableThinking, thinkingBudget,
                    thinkingContent.toString(), duration);

            chatMessageService.saveAiMessage(sessionId, assistantResponse.toString(), metadata);
            chatSessionService.updateLastActiveTime(sessionId);

            if (isNewSession) {
                titleGenerationService.generateAndUpdateTitleAsync(sessionId);
            }

            log.info("消息保存成功: sessionId={}", sessionId);

        } catch (Exception e) {
            log.error("保存AI消息失败: sessionId={}", sessionId, e);
        }
    }

    private Flux<Map<String, String>> handleError(Throwable error) {
        log.error("所有重试均失败", error);
        Map<String, String> errorResult = new HashMap<>();
        errorResult.put("type", "error");

        if (error instanceof SocketException || (error.getCause() != null
                && error.getCause() instanceof SocketException)) {
            errorResult.put("content", "网络连接不稳定，请检查网络后重试");
        } else if (error instanceof ConnectException) {
            errorResult.put("content", "无法连接到AI服务，请稍后重试");
        } else if (error instanceof UnresolvedAddressException) {
            errorResult.put("content", "网络DNS解析失败，请检查网络设置");
        } else {
            errorResult.put("content", "服务暂时不可用，请稍后重试");
        }

        return Flux.just(errorResult);
    }
}
