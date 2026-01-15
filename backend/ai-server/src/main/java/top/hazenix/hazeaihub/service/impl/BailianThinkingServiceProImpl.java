package top.hazenix.hazeaihub.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import top.hazenix.hazeaihub.entity.ChatMessage;
import top.hazenix.hazeaihub.entity.ChatSession;
import top.hazenix.hazeaihub.mapper.ChatMessageMapper;
import top.hazenix.hazeaihub.mapper.ChatSessionMapper;
import top.hazenix.hazeaihub.service.IBailianThinkingService;

import java.net.ConnectException;
import java.nio.channels.UnresolvedAddressException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @description: 支持思考过程的聊天服务（Pro版本）- 使用 PostgreSQL 持久化存储会话数据和消息数据
 * @author: Hazenix
 * @version: 0.0.1
 * @date: 2026/1/15
 */
@Slf4j
@Service
public class BailianThinkingServiceProImpl implements IBailianThinkingService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.chat.options.model:deepseek-r1}")
    private String model;

    public BailianThinkingServiceProImpl(WebClient.Builder webClientBuilder,
                                        ObjectMapper objectMapper,
                                        ChatSessionMapper chatSessionMapper,
                                        ChatMessageMapper chatMessageMapper) {
        this.webClient = webClientBuilder
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .build();
        this.objectMapper = objectMapper;
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
    }

    /**
     * 调用百炼 API 并返回包含思考过程的流式响应
     * 使用 PostgreSQL 持久化存储会话数据和消息数据
     * 
     * @param userMessage 用户消息
     * @param enableThinking 是否启用思考过程
     * @param thinkingBudget 思考过程的最大 token 数
     * @param chatId 会话ID，用于获取和保存会话上下文（可选）
     */
    @Override
    public Flux<Map<String, String>> chatWithThinking(String userMessage,
                                                       Boolean enableThinking,
                                                       Integer thinkingBudget,
                                                       String chatId) {
        // 构建消息列表，包含历史消息和当前用户消息
        List<Map<String, String>> messages = new ArrayList<>();
        Long sessionIdLong = null;
        
        // 如果有 chatId，从数据库获取历史消息
        if (chatId != null && !chatId.trim().isEmpty()) {
            try {
                // 尝试将 chatId 转换为 Long
                try {
                    sessionIdLong = Long.parseLong(chatId);
                } catch (NumberFormatException e) {
                    log.warn("chatId 无法转换为 Long，将创建新会话: {}", chatId);
                    // 如果无法转换，可能需要根据业务逻辑处理，这里先设为 null
                    sessionIdLong = null;
                }
                
                if (sessionIdLong != null) {
                    // 从数据库获取历史消息（最近5条）
                    List<ChatMessage> historyMessages = chatMessageMapper.selectBySessionIdOrderByCreatedAt(sessionIdLong, 5);
                    if (historyMessages != null && !historyMessages.isEmpty()) {
                        // 将历史消息转换为 API 格式
                        for (ChatMessage msg : historyMessages) {
                            Map<String, String> msgMap = new HashMap<>();
                            if ("user".equals(msg.getRole())) {
                                msgMap.put("role", "user");
                                msgMap.put("content", msg.getContent());
                            } else if ("assistant".equals(msg.getRole())) {
                                msgMap.put("role", "assistant");
                                msgMap.put("content", msg.getContent());
                            }
                            if (!msgMap.isEmpty()) {
                                messages.add(msgMap);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("获取会话历史失败，将使用新会话: {}", e.getMessage());
            }
        }
        
        // 添加当前用户消息
        Map<String, String> currentMessage = new HashMap<>();
        currentMessage.put("role", "user");
        currentMessage.put("content", userMessage);
        messages.add(currentMessage);
        
        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("stream", true);
        requestBody.put("messages", messages);
        
        // 添加思考过程相关参数（针对 qwen3-vl-plus 等模型）
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
        
        // 保存最终会话ID（可能需要在流式响应中创建新会话）
        final Long finalSessionId = sessionIdLong;
        
        return webClient.post()
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
                    
                    // 按行拆分，支持跨 buffer 的行
                    String[] lines = content.split("\\r?\\n");
                    List<String> lineList = new ArrayList<>();
                    for (String line : lines) {
                        if (!line.isEmpty()) {
                            lineList.add(line);
                        }
                    }
                    return Flux.fromIterable(lineList);
                })
                .filter(line -> {
                    return line.startsWith("data: ") && !line.contains("[DONE]");
                })
                .map(line -> {
                    String json = line.substring(6).trim(); // 移除 "data: " 前缀并去除空白
                    if (json.isEmpty()) {
                        return null;
                    }
                    return json;
                })
                .filter(json -> json != null && !json.isEmpty()) // 过滤空 JSON
                .mapNotNull(chunk -> parseStreamChunk(chunk))
                .doOnNext(result -> {
                    // 收集 answer 类型的内容，用于保存到数据库
                    if (result != null) {
                        String type = result.get("type");
                        String content = result.get("content");
                        if (content != null) {
                            if ("answer".equals(type)) {
                                assistantResponse.append(content);
                            } else if ("thinking".equals(type)) {
                                thinkingContent.append(content);
                            }
                        }
                    }
                })
                .doOnComplete(() -> {
                    // 流式响应完成后，保存消息到数据库
                    if (assistantResponse.length() > 0) {
                        try {
                            saveMessagesToDatabase(finalSessionId, userMessage, assistantResponse.toString(), 
                                                 thinkingContent.toString(), enableThinking, thinkingBudget);
                        } catch (Exception e) {
                            log.error("保存会话历史失败: {}", e.getMessage(), e);
                        }
                    }
                })
                .doOnError(error -> {
                    if (error instanceof UnresolvedAddressException) {
                        log.error("DNS 解析失败，无法连接到 dashscope.aliyuncs.com。请检查：1) 网络连接 2) DNS 配置 3) 是否需要代理", error);
                    } else if (error instanceof ConnectException) {
                        log.error("连接失败，无法连接到 dashscope.aliyuncs.com。请检查：1) 网络连接 2) 防火墙设置 3) 代理配置", error);
                    } else {
                        log.error("调用百炼 API 出错: {}", error.getMessage(), error);
                    }
                });
    }

    /**
     * 保存消息到数据库
     * @param sessionId 会话ID
     * @param userMessage 用户消息
     * @param assistantResponse AI回复
     * @param thinkingContent 思考过程内容
     * @param enableThinking 是否启用思考过程
     * @param thinkingBudget 思考预算
     */
    @Transactional
    protected void saveMessagesToDatabase(Long sessionId, String userMessage, 
                                         String assistantResponse, String thinkingContent,
                                         Boolean enableThinking, Integer thinkingBudget) {
        try {
            // 如果 sessionId 为空，需要创建新会话
            // 注意：这里需要 userId，可能需要从安全上下文获取或作为参数传入
            // 暂时先使用 sessionId，如果不存在则创建
            ChatSession session = null;
            if (sessionId != null) {
                session = chatSessionMapper.selectById(sessionId);
            }
            
            // 如果会话不存在，创建新会话
            // TODO: 需要从安全上下文获取 userId，这里暂时使用默认值或抛出异常
            if (session == null) {
                log.warn("会话不存在，无法保存消息。需要先创建会话或提供有效的 sessionId");
                // 可以选择创建新会话，但需要 userId
                // session = ChatSession.builder()
                //         .userId(userId) // 需要从安全上下文获取
                //         .type("chat")
                //         .status(true)
                //         .lastActiveAt(LocalDateTime.now())
                //         .createdAt(LocalDateTime.now())
                //         .updatedAt(LocalDateTime.now())
                //         .build();
                // chatSessionMapper.insert(session);
                // sessionId = session.getId();
                return;
            }
            
            // 更新会话的最后活跃时间
            session.setLastActiveAt(LocalDateTime.now());
            session.setUpdatedAt(LocalDateTime.now());
            chatSessionMapper.updateById(session);
            
            LocalDateTime now = LocalDateTime.now();
            
            // 保存用户消息
            ChatMessage userMsg = ChatMessage.builder()
                    .sessionId(sessionId)
                    .role("user")
                    .content(userMessage)
                    .createdAt(now)
                    .build();
            chatMessageMapper.insert(userMsg);
            
            // 构建 metadataJson，包含思考过程相关信息
            Map<String, Object> metadata = new HashMap<>();
            if (enableThinking != null && enableThinking) {
                metadata.put("enable_thinking", true);
                if (thinkingBudget != null) {
                    metadata.put("thinking_budget", thinkingBudget);
                }
                if (thinkingContent != null && !thinkingContent.isEmpty()) {
                    metadata.put("thinking_content", thinkingContent);
                }
            }
            metadata.put("model", model);
            
            // 保存AI回复
            ChatMessage assistantMsg = ChatMessage.builder()
                    .sessionId(sessionId)
                    .role("assistant")
                    .content(assistantResponse)
                    .metadataJson(metadata.isEmpty() ? null : metadata)
                    .createdAt(now)
                    .build();
            chatMessageMapper.insert(assistantMsg);
            
            log.debug("成功保存消息到数据库，会话ID: {}", sessionId);
        } catch (Exception e) {
            log.error("保存消息到数据库失败: {}", e.getMessage(), e);
            throw e;
        }
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
            
            // 提取各个字段的值（不自动转换为null，而是检查是否为null node或空字符串）
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
            // 如果 content 存在，优先返回 answer（因为这是最终答案）
            if (content != null) {
                result.put("type", "answer");
                result.put("content", content);
                return result;
            }
            
            // 如果没有 content，但有 reasoning_content，返回 thinking
            if (reasoningContent != null) {
                result.put("type", "thinking");
                result.put("content", reasoningContent);
                return result;
            }
            
            // 如果只有 reasoning 字段，返回 thinking
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
