package top.hazenix.hazeaihub.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
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
     * 文本对话接口（纯文本 SSE 流式返回）
     *
     * <p>使用 SseEmitter + MediaType.TEXT_PLAIN 强制纯文本编码，避免 Spring MVC 环境下
     * Jackson 对 String 数据的 JSON 序列化（加引号、转义 unicode）问题。</p>
     *
     * <p>SSE 数据格式：</p>
     * <pre>
     * data:SESSION_CREATED:&lt;id&gt;       // 新会话创建通知（仅首条消息）
     * data:&lt;think&gt;思考片段&lt;/think&gt;  // 思考过程分片
     * data:回答文本片段               // 回答内容分片
     * data:ERROR:错误信息             // 错误通知
     * </pre>
     */
    @PostMapping(value = "/text-chat")
    @Operation(summary = "智能对话", description = "支持思考过程的流式对话接口，SSE 纯文本格式")
    public SseEmitter textChat(
            @Parameter(description = "用户输入", required = true) @RequestParam String prompt,
            @Parameter(description = "会话ID（首条消息传null）") @RequestParam(required = false) Long sessionId,
            @Parameter(description = "分组ID") @RequestParam(required = false) Long groupId,
            @Parameter(description = "是否启用思考过程") @RequestParam(required = false, defaultValue = "true") Boolean enableThinking,
            @Parameter(description = "思考token预算") @RequestParam(required = false) Integer thinkingBudget,
            @Parameter(description = "模型名称") @RequestParam(required = false) String model) {

        log.info("收到聊天请求: sessionId={}, prompt={}", sessionId, prompt);

        // -1L 表示不超时（由业务逻辑控制流的结束）
        SseEmitter emitter = new SseEmitter(-1L);

        chatService.textChat(groupId, sessionId, prompt, enableThinking, thinkingBudget, model)
                .subscribe(
                        chunk -> {
                            try {
                                // MediaType.TEXT_PLAIN 强制 StringHttpMessageConverter，不走 Jackson
                                emitter.send(SseEmitter.event().data(chunk, MediaType.TEXT_PLAIN));
                            } catch (Exception e) {
                                log.warn("发送 SSE 数据失败: {}", e.getMessage());
                                emitter.completeWithError(e);
                            }
                        },
                        error -> {
                            log.warn("流式响应错误: {}", error.getMessage());
                            try {
                                emitter.send(SseEmitter.event().data("ERROR:服务暂时不可用", MediaType.TEXT_PLAIN));
                            } catch (Exception ignored) {
                                // 发送错误消息失败时忽略
                            }
                            emitter.complete();
                        },
                        emitter::complete
                );

        return emitter;
    }
}
