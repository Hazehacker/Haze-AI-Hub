package top.hazenix.hazeaihub.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import top.hazenix.hazeaihub.service.IChatService;

/**
 * 对话控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI对话", description = "AI对话相关接口")
public class ChatController {

    private final IChatService chatService;

    /**
     * 文本对话接口（使用chatClient实现流式对话）
     * @param prompt 用户输入
     * @param sessionId 会话ID（首条消息传null）
     * @param groupId 分组ID
     * @param enableThinking 是否启用思考过程
     * @param thinkingBudget 思考token预算
     * @param model 模型名称
     * @return 流式返回格式：思考内容和回答内容
     */
    @PostMapping(value = "/text-chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "智能对话", description = "支持思考过程的流式对话接口")
    public Flux<String> textChat(
            @Parameter(description = "用户输入", required = true) @RequestParam String prompt,
            @Parameter(description = "会话ID（首条消息传null）") @RequestParam(required = false) Long sessionId,
            @Parameter(description = "分组ID") @RequestParam(required = false) Long groupId,
            @Parameter(description = "是否启用思考过程") @RequestParam(required = false, defaultValue = "true") Boolean enableThinking,
            @Parameter(description = "思考token预算") @RequestParam(required = false) Integer thinkingBudget,
            @Parameter(description = "模型名称") @RequestParam(required = false) String model) {

        log.info("收到聊天请求: sessionId={}, prompt={}", sessionId, prompt);

        return chatService.textChat(groupId, sessionId, prompt, enableThinking, thinkingBudget, model)
                .map(chunk -> {
                    String type = chunk.get("type");
                    String content = chunk.get("content");

                    // 直接返回内容，让chatClient和advisor处理格式
                    if ("thinking".equals(type) || "answer".equals(type)) {
                        return content;
                    }
                    return "";
                })
                .filter(s -> !s.isEmpty());
    }
}