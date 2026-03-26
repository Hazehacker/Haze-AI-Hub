package top.hazenix.hazeaihub.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import top.hazenix.hazeaihub.dto.ChatRequestDTO;
import top.hazenix.hazeaihub.service.IBailianThinkingService;
import top.hazenix.hazeaihub.service.IChatSessionService;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @description: 对话相关接口
 * @author: Hazenix
 * @version: 1.0.0
 * @date: 2026/1/27
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI对话", description = "AI对话相关接口")
public class ChatController {
    private final ChatClient chatClient;
    private final ChatClient gameChatClient;
    private final IBailianThinkingService bailianThinkingService;
    private final IChatSessionService chatSessionService;

    /**
     * 原有的聊天接口（不包含思考过程）
     * @deprecated 建议使用 chatWithThinkingText 接口
     */
    @Deprecated
    @PostMapping(value = "/chat", produces = "text/html;charset=utf-8")
    @Operation(summary = "简单聊天接口", description = "不包含思考过程的聊天接口")
    public Flux<String> chat(
            @Parameter(description = "用户输入") @RequestParam String prompt,
            @Parameter(description = "会话ID") @RequestParam(required = false) String sessionId) {
        return chatClient.prompt()
                .user(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .stream()
                .content();
    }

    /**
     * 哄哄模拟器聊天接口
     */
    @PostMapping(value = "/game", produces = "text/html;charset=utf-8")
    @Operation(summary = "哄哄模拟器", description = "哄哄模拟器聊天接口")
    public Flux<String> gameChat(
            @Parameter(description = "用户输入") @RequestParam String prompt,
            @Parameter(description = "会话ID") @RequestParam(required = false) String sessionId) {
        return gameChatClient.prompt()
                .user(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .stream()
                .content();
    }


    /**
     * 带思考过程的聊天接口（纯文本流式返回）【该版本SpringAI不支持阿里云百炼的模型，所以要有思考过程，需要自己封装】
     * 返回格式：<think>思考内容</think>回答内容
     * 
     * @param prompt 用户输入
     * @param sessionId 会话ID（首条消息传null）
     * @param groupId 分组ID（可选）
     * @param enableThinking 是否启用思考过程
     * @param thinkingBudget 思考过程的最大 token 数
     * @param model 模型名称（可选）
     * @return 流式返回格式：<think>思考内容</think>回答内容
     */
    @PostMapping(value = "/chat-with-thinking-text", produces = "text/html;charset=utf-8")
    @Operation(summary = "智能对话", description = "支持思考过程的流式对话接口")
    public Flux<String> chatWithThinkingText(
            @Parameter(description = "用户输入", required = true) @RequestParam String prompt,
            @Parameter(description = "会话ID（首条消息传null）") @RequestParam(required = false) Long sessionId,
            @Parameter(description = "分组ID") @RequestParam(required = false) Long groupId,
            @Parameter(description = "是否启用思考过程") @RequestParam(required = false, defaultValue = "true") Boolean enableThinking,
            @Parameter(description = "思考token预算") @RequestParam(required = false) Integer thinkingBudget,
            @Parameter(description = "模型名称") @RequestParam(required = false) String model) {
        
        log.info("收到聊天请求: sessionId={}, prompt={}", sessionId, prompt);
        
        AtomicBoolean thinkingStarted = new AtomicBoolean(false);
        AtomicBoolean isNewSession = new AtomicBoolean(sessionId == null);

        return bailianThinkingService.chatWithThinking(prompt, enableThinking, thinkingBudget, sessionId, groupId, model)
                .map(chunk -> {
                    String type = chunk.get("type");
                    String content = chunk.get("content");
                    
                    // 处理新会话创建事件
                    if ("session-created".equals(type)) {
                        log.info("新会话已创建: sessionId={}", content);
                        return "event: session-created\ndata: {\"sessionId\":" + content + "}\n\n";
                    }
                    
                    // 处理思考过程
                    if ("thinking".equals(type)) {
                        if (thinkingStarted.compareAndSet(false, true)) {
                            return "<think>" + content;
                        }
                        return content;
                    } 
                    // 处理回答内容
                    else if ("answer".equals(type)) {
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

