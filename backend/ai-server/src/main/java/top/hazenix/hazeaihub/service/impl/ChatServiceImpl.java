package top.hazenix.hazeaihub.service.impl;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import top.hazenix.hazeaihub.constant.RoleConstant;
import top.hazenix.hazeaihub.context.BaseContext;
import top.hazenix.hazeaihub.entity.ChatMessage;
import top.hazenix.hazeaihub.entity.ChatSession;
import top.hazenix.hazeaihub.service.IChatMessageService;
import top.hazenix.hazeaihub.service.IChatService;
import top.hazenix.hazeaihub.service.IChatSessionService;
import top.hazenix.hazeaihub.service.ITitleGenerationService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 对话服务实现
 * 利用 Spring AI Alibaba 框架原生支持
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements IChatService {

    private final IChatSessionService chatSessionService;
    private final IChatMessageService chatMessageService;
    private final ITitleGenerationService titleGenerationService;
    private final ChatModel textChatModel;


    /**
     * 处理普通文本聊天请求（保留Spring AI Alibaba框架的便捷方式）
     * @param groupId        分组ID
     * @param sessionId      会话ID
     * @param prompt         提示语
     * @param enableThinking 是否启用思考过程
     * @param thinkingBudget 思考 token 预算
     * @param model          模型名称
     */
    @Override
    @Transactional
    public Flux<Map<String, String>> textChat(Long groupId, Long sessionId, String prompt, Boolean enableThinking, Integer thinkingBudget, String model) {
        log.info("开始处理textChat请求: groupId={}, sessionId={}, prompt={}", groupId, sessionId, prompt);

        // 1. 处理会话创建
        boolean isNewSession = (sessionId == null);
        Long finalSessionId = createSessionIfNeeded(isNewSession, sessionId, groupId);

        // 构建初始事件流（新会话）
        Flux<Map<String, String>> initialFlux = buildSessionCreatedFlux(isNewSession, finalSessionId);

        // 2. 保存用户消息
        chatMessageService.saveUserMessage(finalSessionId, prompt);

        // 3. 构建对话历史
        List<Message> messages = new ArrayList<>();
        if (!isNewSession) {
            List<ChatMessage> historyMessages = chatMessageService.getMessagesBySessionId(finalSessionId, 10);
            messages.addAll(historyMessages.stream().map(message -> {
                return switch (message.getRole()) {
                    case RoleConstant.SYSTEM -> new SystemMessage(message.getContent());
                    case RoleConstant.USER_ROLE -> new UserMessage(message.getContent());
                    case RoleConstant.ASSISTANT_ROLE -> new AssistantMessage(message.getContent());
                    default -> null;
                };
            }).toList());
        }
        messages.add(new UserMessage(prompt));

        // 4. 创建带有特定选项的 Prompt
        DashScopeChatOptions runtimeOptions = DashScopeChatOptions.builder()
                .model(model != null ? model : "qwen-plus")
                .enableThinking(enableThinking)
                .withMaxToken(thinkingBudget)
                .build();

        Prompt chatPrompt = new Prompt(messages, runtimeOptions);

        // 5. 使用流式 API
        Flux<ChatResponse> responseStream = textChatModel.stream(chatPrompt);

        // 收集完整的AI响应
        StringBuilder fullResponseBuilder = new StringBuilder();
        StringBuilder fullReasoningContentBuilder = new StringBuilder();

        // 转换：将ChatResponse转为结构化Map
        Flux<Map<String, String>> chatFlux = responseStream
                .map(response -> {
                    Map<String, String> result = new HashMap<>();
                    AssistantMessage assistantMessage = response.getResult().getOutput();

                    if (assistantMessage == null) {
                        return result;
                    }

                    // 获取思考内容
                    if (enableThinking && assistantMessage.getMetadata() != null) {
                        String reasoningContent = (String) assistantMessage.getMetadata().get("reasoningContent");
                        if (reasoningContent != null && !reasoningContent.isEmpty()) {
                            fullReasoningContentBuilder.append(reasoningContent);
                            result.put("type", "thinking");
                            result.put("content", reasoningContent);
                        }
                    }

                    // 获取回答内容
                    String text = assistantMessage.getText();
                    if (text != null && !text.isEmpty()) {
                        fullResponseBuilder.append(text);
                        result.put("type", "answer");
                        result.put("content", text);
                    }

                    return result;
                })
                .filter(map -> !map.isEmpty() && map.get("content") != null)
                .doOnComplete(() -> {
                    // 流结束时保存数据
                    String fullResponse = fullResponseBuilder.toString();
                    if (fullResponse.isEmpty()) {
                        return;
                    }

                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("model", model != null ? model : "qwen-plus");
                    metadata.put("enable_thinking", enableThinking);
                    metadata.put("reasoning_content", fullReasoningContentBuilder.toString());
                    metadata.put("thinking_budget", thinkingBudget);

                    chatMessageService.saveAiMessage(finalSessionId, fullResponse, metadata);
                    chatSessionService.updateLastActiveTime(finalSessionId);

                    // 新会话则生成标题
                    if (isNewSession) {
                        titleGenerationService.generateAndUpdateTitleAsync(finalSessionId);
                    }
                })
                .doOnError(error -> log.error("流式对话出错", error))
                .onErrorResume(e -> {
                    log.warn("流式响应错误: {}", e.getMessage());
                    Map<String, String> errorResult = new HashMap<>();
                    errorResult.put("type", "error");
                    errorResult.put("content", e.getMessage());
                    return Flux.just(errorResult);
                });

        return initialFlux.concatWith(chatFlux);
    }

    private Long createSessionIfNeeded(boolean isNewSession, Long sessionId, Long groupId) {
        if (!isNewSession) {
            return sessionId;
        }

        try {
            Long userId = BaseContext.getCurrentId();
            if (userId == null) {
                throw new RuntimeException("用户未登录");
            }

            ChatSession newSession = chatSessionService.createSession(userId, "chat", "新对话");
            Long newSessionId = newSession.getId();

            if (groupId != null) {
                chatSessionService.updateSession(newSessionId, null, groupId);
            }

            log.info("创建新会话成功: sessionId={}", newSessionId);
            return newSessionId;

        } catch (Exception e) {
            log.error("创建会话失败", e);
            throw new RuntimeException("创建会话失败: " + e.getMessage());
        }
    }

    private Flux<Map<String, String>> buildSessionCreatedFlux(boolean isNewSession, Long sessionId) {
        if (!isNewSession) {
            return Flux.empty();
        }
        Map<String, String> event = new HashMap<>();
        event.put("type", "session-created");
        event.put("content", String.valueOf(sessionId));
        return Flux.just(event);
    }
}
