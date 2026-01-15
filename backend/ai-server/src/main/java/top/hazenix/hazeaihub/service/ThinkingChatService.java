package top.hazenix.hazeaihub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

/**
 * 支持思考过程的聊天服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThinkingChatService {
    
    private final ChatModel chatModel;
    
    @Value("${spring.ai.openai.chat.options.model:deepseek-r1}")
    private String modelName;

    /**
     * 带思考过程的流式聊天
     * @param userMessage 用户消息
     * @param enableThinking 是否启用思考过程（对于 qwen3-vl-plus 等模型）
     * @param thinkingBudget 思考过程的最大 token 数（可选）
     * @return 流式响应，包含思考过程和最终答案
     */
    public Flux<Map<String, String>> chatWithThinking(String userMessage, 
                                                       Boolean enableThinking, 
                                                       Integer thinkingBudget) {
        // 构建选项
        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .withModel(modelName);
        
        // 如果需要启用思考过程（针对 qwen3-vl-plus 等模型）
        if (enableThinking != null && enableThinking) {
            Map<String, Object> extraParams = new HashMap<>();
            extraParams.put("enable_thinking", true);
            if (thinkingBudget != null && thinkingBudget > 0) {
                extraParams.put("thinking_budget", thinkingBudget);
            }
            // 注意：Spring AI 可能不直接支持这些参数，需要通过底层 API 传递
        }
        
        Prompt prompt = new Prompt(userMessage, optionsBuilder.build());
        
        return chatModel.stream(prompt)
                .map(response -> {
                    Map<String, String> result = new HashMap<>();
                    
                    if (response.getResults() != null && !response.getResults().isEmpty()) {
                        var generation = response.getResults().get(0);
                        var metadata = generation.getMetadata();
                        
                        // 提取思考过程
                        if (metadata != null) {
                            Object reasoningObj = metadata.get("reasoning_content");
                            if (reasoningObj != null) {
                                result.put("type", "thinking");
                                result.put("content", reasoningObj.toString());
                                return result;
                            }
                        }
                        
                        // 提取回答内容
                        String content = generation.getOutput().getContent();
                        if (content != null && !content.isEmpty()) {
                            result.put("type", "answer");
                            result.put("content", content);
                            return result;
                        }
                    }
                    
                    return result;
                })
                .filter(map -> !map.isEmpty());
    }
    
    /**
     * 简化版本：直接使用默认配置
     */
    public Flux<Map<String, String>> chatWithThinking(String userMessage) {
        return chatWithThinking(userMessage, null, null);
    }
}
