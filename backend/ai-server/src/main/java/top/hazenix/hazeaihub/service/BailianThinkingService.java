package top.hazenix.hazeaihub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

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
public class BailianThinkingService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.chat.options.model:deepseek-r1}")
    private String model;

    public BailianThinkingService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * 调用百炼 API 并返回包含思考过程的流式响应
     * 1. BailianThinkingService：直接调用阿里云百炼 API，解析 SSE 流式响应
     * 2. 响应解析：从 `delta.reasoning_content` 提取思考过程，从 `delta.content` 提取最终答案
     * 3. 流式传输：使用 WebFlux 的 Flux 实现流式响应，前端可实时接收
     */
    public Flux<Map<String, String>> chatWithThinking(String userMessage, 
                                                       Boolean enableThinking,
                                                       Integer thinkingBudget) {
        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("stream", true);
        
        // 构建消息
        Map<String, String> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", userMessage);
        requestBody.put("messages", List.of(message));
        
        // 添加思考过程相关参数（针对 qwen3-vl-plus 等模型）
        if (enableThinking != null && enableThinking) {
            requestBody.put("enable_thinking", true);
            if (thinkingBudget != null && thinkingBudget > 0) {
                requestBody.put("thinking_budget", thinkingBudget);
            }
        }

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
                .doOnError(error -> {
                    if (error instanceof java.nio.channels.UnresolvedAddressException) {
                        log.error("DNS 解析失败，无法连接到 dashscope.aliyuncs.com。请检查：1) 网络连接 2) DNS 配置 3) 是否需要代理", error);
                    } else if (error instanceof java.net.ConnectException) {
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
