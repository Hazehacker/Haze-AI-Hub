package top.hazenix.hazeaihub.service.impl;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
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
import reactor.core.publisher.Mono;
import top.hazenix.hazeaihub.constant.RoleConstant;
import top.hazenix.hazeaihub.context.BaseContext;
import top.hazenix.hazeaihub.entity.ChatMessage;
import top.hazenix.hazeaihub.entity.ChatSession;
import top.hazenix.hazeaihub.service.IChatMessageService;
import top.hazenix.hazeaihub.service.IChatService;
import top.hazenix.hazeaihub.service.IChatSessionService;
import top.hazenix.hazeaihub.service.ITitleGenerationService;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private final ChatClient chatClient;


    /**
     * 处理普通文本聊天请求（使用chatClient实现流式对话）
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

        // 3. 构建历史消息列表（用于上下文）
        List<Message> historyMessages = new ArrayList<>();
        if (!isNewSession) {
            List<ChatMessage> dbMessages = chatMessageService.getMessagesBySessionId(finalSessionId, 10);
            for (ChatMessage msg : dbMessages) {
                if (RoleConstant.USER.equals(msg.getRole())) {
                    historyMessages.add(new UserMessage(msg.getContent()));
                } else if (RoleConstant.ASSISTANT.equals(msg.getRole())) {
                    historyMessages.add(new AssistantMessage(msg.getContent()));
                }
            }
        }

        // 5. 使用chatClient进行流式对话（传入历史消息）
        Flux<String> responseFlux = chatClient
                .prompt()
                .messages(historyMessages)                    // 传入历史消息
                .user(prompt)
                .options(DashScopeChatOptions.builder()
                        .model(model != null ? model : "qwen-plus")
                        .enableThinking(enableThinking)
                        .withMaxToken(thinkingBudget)
                        .build())
                .stream()
                .content();

        // 6. 处理流式响应
        StringBuilder fullResponseBuilder = new StringBuilder();
        StringBuilder fullReasoningBuilder = new StringBuilder();
        AtomicBoolean isInThinking = new AtomicBoolean(false);

        Flux<Map<String, String>> chatFlux = responseFlux
                .map(content -> {
                    Map<String, String> result = new HashMap<>();
                    
                    // 判断是否是思考内容（以<think>开头）
                    if (content.startsWith("<think>") && content.endsWith("</think>")) {
                        String thinkingContent = content.substring(7, content.length() - 8); // 去掉<think>和</think>
                        fullReasoningBuilder.append(thinkingContent);
                        result.put("type", "thinking");
                        result.put("content", thinkingContent);
                        isInThinking.set(true);
                    } else {
                        // 回答内容
                        fullResponseBuilder.append(content);
                        result.put("type", "answer");
                        result.put("content", content);
                        isInThinking.set(false);
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
                    metadata.put("reasoning_content", fullReasoningBuilder.toString());
                    metadata.put("thinking_budget", thinkingBudget);

                    chatMessageService.saveAiMessage(finalSessionId, fullResponse, metadata);
                    chatSessionService.updateLastActiveTime(finalSessionId);

                    // 新会话则生成标题
                    if (isNewSession) {
                        titleGenerationService.generateAndUpdateTitle(finalSessionId);
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
