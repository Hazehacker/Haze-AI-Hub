package top.hazenix.hazeaihub.service.impl.stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * SSE 流式响应解析器
 * 专门负责解析百炼 API 返回的流式 chunk 数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StreamChunkParser {

    private final ObjectMapper objectMapper;

    /**
     * 解析流式响应的每个 chunk
     *
     * @param json SSE chunk 的 JSON 字符串
     * @return 解析后的结果，包含 type (answer/thinking) 和 content
     */
    public Map<String, String> parse(String json) {
        try {
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
            String reasoningContent = extractString(delta, "reasoning_content");
            String reasoning = extractString(delta, "reasoning");
            String content = extractString(delta, "content");

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

    private String extractString(JsonNode node, String fieldName) {
        if (node.has(fieldName) && !node.get(fieldName).isNull()) {
            String value = node.get(fieldName).asText();
            if (value != null && !value.isEmpty() && !value.equals("null")) {
                return value;
            }
        }
        return null;
    }
}
