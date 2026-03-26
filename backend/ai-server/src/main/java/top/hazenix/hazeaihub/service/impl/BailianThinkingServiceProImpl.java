package top.hazenix.hazeaihub.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import top.hazenix.hazeaihub.constant.MessageConstant;
import top.hazenix.hazeaihub.constant.RoleConstant;
import top.hazenix.hazeaihub.context.BaseContext;
import top.hazenix.hazeaihub.entity.ChatMessage;
import top.hazenix.hazeaihub.entity.ChatSession;
import top.hazenix.hazeaihub.mapper.ChatMessageMapper;
import top.hazenix.hazeaihub.mapper.ChatSessionMapper;
import top.hazenix.hazeaihub.service.IBailianThinkingService;
import top.hazenix.hazeaihub.service.IChatMessageService;
import top.hazenix.hazeaihub.service.IChatSessionService;
import top.hazenix.hazeaihub.service.ITitleGenerationService;

import java.net.ConnectException;
import java.net.SocketException;
import java.nio.channels.UnresolvedAddressException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @description: 支持思考过程的聊天服务（Pro版本）- 使用 PostgreSQL 持久化存储会话数据和消息数据
 * @author: Hazenix
 * @version: 0.0.1
 * @date: 2026/1/15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BailianThinkingServiceProImpl implements IBailianThinkingService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final IChatSessionService chatSessionService;
    private final IChatMessageService chatMessageService;
    private final ITitleGenerationService titleGenerationService;

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    @Value("${ai.bailian.default.model:deepseek-r1}")
    private String model;// 默认模型


    /**
     * 调用百炼 API 并返回包含思考过程的流式响应
     * 
     * 核心流程：
     * 1. 如果 sessionId 为空，创建新会话并保存用户消息
     * 2. 如果 sessionId 存在，直接保存用户消息
     * 3. 流式调用大模型获取回复
     * 4. 流结束后保存 AI 消息
     * 5. 异步生成会话标题（仅首条消息）
     * 
     * @param userMessage 用户消息
     * @param enableThinking 是否启用思考过程
     * @param thinkingBudget 思考过程的最大 token 数
     * @param sessionId 会话ID（首条消息传null）
     * @param groupId 分组ID（可选）
     * @param modelName 模型名称（可选，如果为空则使用配置文件中的默认模型）
     */
    @Override
    public Flux<Map<String, String>> chatWithThinking(String userMessage,
                                                       Boolean enableThinking,
                                                       Integer thinkingBudget,
                                                       Long sessionId,
                                                       Long groupId,
                                                       String modelName) {
        
        log.info("开始处理聊天请求: sessionId={}, userMessage={}", sessionId, userMessage);
        
        // 标记是否为新会话
        final boolean isNewSession = (sessionId == null);
        final Long[] finalSessionId = {sessionId};
        
        // 如果是新会话，创建会话并保存用户消息
        if (isNewSession) {
            try {
                Long userId = BaseContext.getCurrentId();
                if (userId == null) {
                    throw new RuntimeException("用户未登录");
                }
                
                // 创建新会话
                ChatSession newSession = chatSessionService.createSession(userId, "chat", "新对话");
                finalSessionId[0] = newSession.getId();
                
                // 如果有分组ID，更新会话分组
                if (groupId != null) {
                    chatSessionService.updateSession(finalSessionId[0], null, groupId);
                }
                
                log.info("创建新会话成功: sessionId={}", finalSessionId[0]);
                
            } catch (Exception e) {
                log.error("创建会话失败", e);
                return Flux.error(new RuntimeException("创建会话失败: " + e.getMessage()));
            }
        }
        
        // 保存用户消息
        try {
            chatMessageService.saveUserMessage(finalSessionId[0], userMessage);
        } catch (Exception e) {
            log.error("保存用户消息失败", e);
            return Flux.error(new RuntimeException("保存用户消息失败: " + e.getMessage()));
        }

        // 使用传入的模型名称，如果为空则使用默认模型
        String selectedModel = (modelName != null && !modelName.trim().isEmpty()) ? modelName : model;
        
        // 构建消息列表，包含历史消息和当前用户消息
        List<Map<String, String>> messages = new ArrayList<>();
        
        // 获取历史消息
        try {
            List<ChatMessage> historyMessages = chatMessageService.getMessagesBySessionId(finalSessionId[0], 10);
            if (historyMessages != null && !historyMessages.isEmpty()) {
                // 将历史消息转换为 API 格式（排除刚保存的用户消息）
                for (int i = 0; i < historyMessages.size() - 1; i++) {
                    ChatMessage msg = historyMessages.get(i);
                    Map<String, String> msgMap = new HashMap<>();
                    if ("U".equals(msg.getRole())) {
                        msgMap.put("role", "user");
                        msgMap.put("content", msg.getContent());
                    } else if ("A".equals(msg.getRole())) {
                        msgMap.put("role", "assistant");
                        msgMap.put("content", msg.getContent());
                    }
                    if (!msgMap.isEmpty()) {
                        messages.add(msgMap);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取历史消息失败，将使用新会话: {}", e.getMessage());
        }
        
        // 添加当前用户消息
        Map<String, String> currentMessage = new HashMap<>();
        currentMessage.put("role", "user");
        currentMessage.put("content", userMessage);
        messages.add(currentMessage);
        
        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", selectedModel);
        requestBody.put("stream", true);
        requestBody.put("messages", messages);
        
        // 添加思考过程相关参数
        if (enableThinking != null && enableThinking) {
            requestBody.put("enable_thinking", true);
            if (thinkingBudget != null && thinkingBudget > 0) {
                requestBody.put("thinking_budget", thinkingBudget);
            }
        }

        // 用于收集完整的AI回复内容
        StringBuilder assistantResponse = new StringBuilder();
        // 用于收集思考过程片段
        StringBuilder thinkingContent = new StringBuilder();
        
        final LocalDateTime[] startTime = {LocalDateTime.now()};
        final LocalDateTime[] endTime = {null};
        final Boolean[] thinkingEnded = {false};
        
        // 添加重试计数器
        final AtomicInteger retryCount = new AtomicInteger(0);
        final int maxRetries = 2;
        
        // 如果是新会话，先发送 session-created 事件
        Flux<Map<String, String>> sessionCreatedFlux = Flux.empty();
        if (isNewSession) {
            Map<String, String> sessionCreatedEvent = new HashMap<>();
            sessionCreatedEvent.put("type", "session-created");
            sessionCreatedEvent.put("content", String.valueOf(finalSessionId[0]));
            sessionCreatedFlux = Flux.just(sessionCreatedEvent);
        }
        
        return sessionCreatedFlux.concatWith(
            Flux.defer(() -> webClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                // 实时处理：将每个 DataBuffer 转换为字符串并按行拆分
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    String content = new String(bytes, StandardCharsets.UTF_8);
                    
                    // 按行拆分
                    String[] lines = content.split("\\r?\\n");
                    List<String> lineList = new ArrayList<>();
                    for (String line : lines) {
                        if (!line.isEmpty()) {
                            lineList.add(line);
                        }
                    }
                    return Flux.fromIterable(lineList);
                })
                .filter(line -> line.startsWith("data: ") && !line.contains("[DONE]"))
                .map(line -> {
                    String json = line.substring(6).trim();
                    return json.isEmpty() ? null : json;
                })
                .filter(json -> json != null && !json.isEmpty())
                .mapNotNull(this::parseStreamChunk)
                .doOnNext(result -> {
                    // 收集内容
                    if (result != null) {
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
                })
                .doOnComplete(() -> {
                    // 流式响应完成后，保存AI消息到数据库
                    if (assistantResponse.length() > 0) {
                        try {
                            // 构建元数据
                            Map<String, Object> metadata = new HashMap<>();
                            metadata.put("model", selectedModel);
                            
                            if (enableThinking != null && enableThinking) {
                                metadata.put("enable_thinking", true);
                                if (thinkingBudget != null) {
                                    metadata.put("thinking_budget", thinkingBudget);
                                }
                                if (thinkingContent.length() > 0) {
                                    metadata.put("thinking_content", thinkingContent.toString());
                                }
                                if (endTime[0] != null) {
                                    Duration duration = Duration.between(startTime[0], endTime[0]);
                                    metadata.put("thinking_duration", duration);
                                }
                            }
                            
                            // 保存AI消息
                            chatMessageService.saveAiMessage(
                                finalSessionId[0], 
                                assistantResponse.toString(), 
                                metadata
                            );
                            
                            // 更新会话活跃时间
                            chatSessionService.updateLastActiveTime(finalSessionId[0]);
                            
                            // 如果是新会话，异步生成标题
                            if (isNewSession) {
                                titleGenerationService.generateAndUpdateTitleAsync(finalSessionId[0]);
                            }
                            
                            log.info("消息保存成功: sessionId={}", finalSessionId[0]);
                            
                        } catch (Exception e) {
                            log.error("保存AI消息失败: sessionId={}", finalSessionId[0], e);
                        }
                    }
                })
                .doOnError(error -> {
                    int currentRetry = retryCount.get();
                    if (error instanceof UnresolvedAddressException) {
                        log.error("DNS解析失败 (重试: {}/{})", currentRetry, maxRetries, error);
                    } else if (error instanceof ConnectException) {
                        log.error("连接失败 (重试: {}/{})", currentRetry, maxRetries, error);
                    } else if (error instanceof SocketException) {
                        log.error("网络连接被重置 (重试: {}/{})", currentRetry, maxRetries, error);
                    } else {
                        log.error("调用API出错 (重试: {}/{})", currentRetry, maxRetries, error);
                    }
                })
                .retry(maxRetries)
                .onErrorResume(error -> {
                    log.error("所有重试均失败", error);
                    Map<String, String> errorResult = new HashMap<>();
                    errorResult.put("type", "error");
                    
                    if (error instanceof SocketException || (error.getCause() != null && error.getCause() instanceof SocketException)) {
                        errorResult.put("content", "网络连接不稳定，请检查网络后重试");
                    } else if (error instanceof ConnectException) {
                        errorResult.put("content", "无法连接到AI服务，请稍后重试");
                    } else if (error instanceof UnresolvedAddressException) {
                        errorResult.put("content", "网络DNS解析失败，请检查网络设置");
                    } else {
                        errorResult.put("content", "服务暂时不可用，请稍后重试");
                    }
                    
                    return Flux.just(errorResult);
                })
        ));
    }

    /**
     * 解析流式响应的每个 chunk
     */
    private Map<String, String> parseStreamChunk(String json) {
        try {
            // 处理空行或空白
            if (json == null || json.trim().isEmpty()) {
                return null;
            }
            
            JsonNode root = objectMapper.readTree(json);
            JsonNode choices = root.path("choices");
            
            if (!choices.isArray() || choices.size() == 0) {
                return null;
            }
            
            JsonNode delta = choices.get(0).path("delta");
            if (delta.isMissingNode() || delta.isEmpty()) {
                return null;
            }
            
            // 提取各个字段的值
            String reasoningContent = null;
            if (delta.has("reasoning_content") && !delta.get("reasoning_content").isNull()) {
                String rc = delta.get("reasoning_content").asText();
                if (rc != null && !rc.isEmpty() && !rc.equals("null")) {
                    reasoningContent = rc;
                }
            }
            
            String reasoning = null;
            if (delta.has("reasoning") && !delta.get("reasoning").isNull()) {
                String r = delta.get("reasoning").asText();
                if (r != null && !r.isEmpty() && !r.equals("null")) {
                    reasoning = r;
                }
            }
            
            String content = null;
            if (delta.has("content") && !delta.get("content").isNull()) {
                String c = delta.get("content").asText();
                if (c != null && !c.isEmpty() && !c.equals("null")) {
                    content = c;
                }
            }
            
            Map<String, String> result = new HashMap<>();
            
            // 优先级：content > reasoning_content > reasoning
            if (content != null) {
                result.put("type", "answer");
                result.put("content", content);
                return result;
            }
            
            if (reasoningContent != null) {
                result.put("type", "thinking");
                result.put("content", reasoningContent);
                return result;
            }
            
            if (reasoning != null) {
                result.put("type", "thinking");
                result.put("content", reasoning);
                return result;
            }
        } catch (Exception e) {
            log.error("解析响应失败: {}", json, e);
        }
        return null;
    }
}
