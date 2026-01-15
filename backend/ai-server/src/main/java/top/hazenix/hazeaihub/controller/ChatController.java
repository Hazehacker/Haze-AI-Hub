package top.hazenix.hazeaihub.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import top.hazenix.hazeaihub.service.BailianThinkingService;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class ChatController {
    private final ChatClient chatClient;
    private final BailianThinkingService bailianThinkingService;

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
     * 带思考过程的聊天接口（JSON 流式返回）
     * 返回格式：每行一个 JSON 对象 （NDJSON）
     *
     * ```json
     * {"type":"thinking","content":"首先分析问题..."}
     * {"type":"thinking","content":"然后考虑..."}
     * {"type":"answer","content":"根据分析，答案是..."}
     * {"type":"answer","content":"..."}
     * ```
     * 
     * @param prompt 用户输入
     * @param chatId 会话ID，用于管理会话上下文（可选）
     * @param enableThinking 是否启用思考过程（针对 qwen3-vl-plus 等模型，deepseek-r1 默认支持）
     * @param thinkingBudget 思考过程的最大 token 数（可选，默认无限制）
     */
    @PostMapping(value = "/chat-with-thinking", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<Map<String, String>> chatWithThinking(
            @RequestParam String prompt,
            @RequestParam(required = false) String chatId,
            @RequestParam(required = false) Boolean enableThinking,
            @RequestParam(required = false) Integer thinkingBudget) {
        return bailianThinkingService.chatWithThinking(prompt, enableThinking, thinkingBudget, chatId);
    }

    /**
     * 带思考过程的聊天接口（纯文本流式返回）
     * 返回格式：<think>思考内容</think>回答内容
     * 
     * @param prompt 用户输入
     * @param chatId 会话ID，用于管理会话上下文（可选）
     * @param enableThinking 是否启用思考过程
     * @param thinkingBudget 思考过程的最大 token 数
     */
    @PostMapping(value = "/chat-with-thinking-text", produces = "text/html;charset=utf-8")
    public Flux<String> chatWithThinkingText(
            @RequestParam String prompt,
            @RequestParam(required = false) String chatId,
            @RequestParam(required = false) Boolean enableThinking,
            @RequestParam(required = false) Integer thinkingBudget) {
        
        AtomicBoolean thinkingStarted = new AtomicBoolean(false);

        return bailianThinkingService.chatWithThinking(prompt, enableThinking, thinkingBudget, chatId)
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

