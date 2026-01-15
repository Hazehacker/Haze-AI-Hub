package top.hazenix.hazeaihub.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import top.hazenix.hazeaihub.service.IBailianThinkingService;

import java.net.ConnectException;
import java.nio.channels.UnresolvedAddressException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * @description: 支持思考过程的聊天服务——SpringAI 1.0.0-M5 不支持读取思考过程，
 * 所以编写这个Service，直接调用阿里云百炼 API 以支持思考过程
 * @author: Hazenix
 * @version: 0.0.1
 * @date: 2026/1/15
 * @return
 */
@Slf4j
@Service
//public class BailianThinkingServiceImpl implements IBailianThinkingService {
public class BailianThinkingServiceImpl  {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final ChatMemory chatMemory;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.chat.options.model:deepseek-r1}")
    private String model;

    public BailianThinkingServiceImpl(WebClient.Builder webClientBuilder,
                                      ObjectMapper objectMapper,
                                      ChatMemory chatMemory) {
        this.webClient = webClientBuilder
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .build();
        this.objectMapper = objectMapper;
        this.chatMemory = chatMemory;
    }

    /**
     * 调用百炼 API 并返回包含思考过程的流式响应
     * 1. BailianThinkingService：直接调用阿里云百炼 API，解析 SSE 流式响应
     * 2. 响应解析：从 `delta.reasoning_content` 提取思考过程，从 `delta.content` 提取最终答案
     * 3. 流式传输：使用 WebFlux 的 Flux 实现流式响应，前端可实时接收
     * 4. 会话上下文：自动从 ChatMemory 获取历史消息并补充到请求中
     * 
     * @param userMessage 用户消息
     * @param enableThinking 是否启用思考过程
     * @param thinkingBudget 思考过程的最大 token 数
     * @param chatId 会话ID，用于获取和保存会话上下文（可选）
     */
    public Flux<Map<String, String>> chatWithThinking(String userMessage, 
                                                       Boolean enableThinking,
                                                       Integer thinkingBudget,
                                                       String chatId) {
        // 构建消息列表，包含历史消息和当前用户消息
        List<Map<String, String>> messages = new ArrayList<>();
        
        // 如果有 chatId，从 ChatMemory 获取历史消息
        if (chatId != null && !chatId.trim().isEmpty()) {
            try {
                List<Message> historyMessages = chatMemory.get(chatId,5);// 获取最近的5条消息作为上下文
                if (historyMessages != null && !historyMessages.isEmpty()) {
                    // 将历史消息转换为 API 格式
                    for (Message msg : historyMessages) {
                        Map<String, String> msgMap = new HashMap<>();
                        if (msg instanceof UserMessage) {
                            msgMap.put("role", "user");
                            msgMap.put("content", msg.getText());
                        } else if (msg instanceof AssistantMessage) {
                            msgMap.put("role", "assistant");
                            msgMap.put("content", msg.getText());
                        }
                        if (!msgMap.isEmpty()) {
                            messages.add(msgMap);
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
                    // 收集 answer 类型的内容，用于保存到 ChatMemory
                    if (result != null && "answer".equals(result.get("type"))) {
                        String content = result.get("content");
                        if (content != null) {
                            assistantResponse.append(content);
                        }
                    }
                })
                .doOnComplete(() -> {
                    // 流式响应完成后，保存消息到 ChatMemory
                    if (chatId != null && !chatId.trim().isEmpty() && assistantResponse.length() > 0) {
                        try {
                            // 保存用户消息
                            chatMemory.add(chatId, new UserMessage(userMessage));
                            // 保存AI回复
                            chatMemory.add(chatId, new AssistantMessage(assistantResponse.toString()));
                        } catch (Exception e) {
                            log.warn("保存会话历史失败: {}", e.getMessage());
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
