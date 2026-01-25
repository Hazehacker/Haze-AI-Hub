package top.hazenix.hazeaihub.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import top.hazenix.hazeaihub.service.IBailianThinkingService;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @description: 对话相关接口
 * @author: Hazenix
 * @version: 0.0.1
 * @date: 2026/1/15
 * @return
 */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class ChatController {
    private final ChatClient chatClient;
    private final IBailianThinkingService bailianThinkingService;

    /**
     * 原有的聊天接口（不包含思考过程）
     */
    @PostMapping(value = "/chat", produces = "text/html;charset=utf-8")
    public Flux<String> chat(@RequestParam String prompt, String chatId) {
        return chatClient.prompt()
                .user(prompt)
                .advisors(a -> a.param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, chatId))
                .stream()
                .content();
    }

    /**
     * 带思考过程的聊天接口（纯文本流式返回）【该版本SpringAI不支持阿里云百炼的模型，所以要有思考过程，需要自己封装】
     * 返回格式：<think>思考内容</think>回答内容
     * 
     * @param prompt 用户输入
     * @param sessionId 会话ID，用于管理会话上下文（可选）
     * @param enableThinking 是否启用思考过程
     * @param thinkingBudget 思考过程的最大 token 数
     * @param model 模型名称（可选，默认使用配置文件中的模型）
     */
    @PostMapping(value = "/chat-with-thinking-text", produces = "text/html;charset=utf-8")
    public Flux<String> chatWithThinkingText(
            @RequestParam String prompt,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false, defaultValue = "true") Boolean enableThinking,
            @RequestParam(required = false) Integer thinkingBudget,
            @RequestParam(required = false) String model) {
        
        AtomicBoolean thinkingStarted = new AtomicBoolean(false);

        return bailianThinkingService.chatWithThinking(prompt, enableThinking, thinkingBudget, sessionId, model)
                .map(chunk -> {
                    String type = chunk.get("type");
                    String content = chunk.get("content");
                    
                    if ("thinking".equals(type)) {
                        if (thinkingStarted.compareAndSet(false, true)) {
                            return "<think>" + content;
                        }
                        return content;
                    } else if ("answer".equals(type)) {
                        if (thinkingStarted.getAndSet(false)) {
                            return "</think>" + content;
                        }
                        return content;
                    }
                    return "";
                })
                .filter(s -> !s.isEmpty())
                .switchIfEmpty(Flux.just("[ERROR]未收到任何响应数据，请查看服务器日志了解详情[/ERROR]"));
    }
}

