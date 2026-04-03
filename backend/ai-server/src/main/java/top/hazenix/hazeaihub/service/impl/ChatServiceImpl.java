package top.hazenix.hazeaihub.service.impl;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import top.hazenix.hazeaihub.constant.RoleConstant;
import top.hazenix.hazeaihub.context.BaseContext;
import top.hazenix.hazeaihub.entity.ChatMessage;
import top.hazenix.hazeaihub.entity.ChatSession;
import top.hazenix.hazeaihub.properties.WanxProperties;
import top.hazenix.hazeaihub.service.IChatMessageService;
import top.hazenix.hazeaihub.service.IChatService;
import top.hazenix.hazeaihub.service.IChatSessionService;
import top.hazenix.hazeaihub.service.IIntentDetectionService;
import top.hazenix.hazeaihub.service.ITitleGenerationService;
import top.hazenix.hazeaihub.service.IWanxImageService;
import top.hazenix.hazeaihub.service.result.IntentDetectionResult;
import top.hazenix.hazeaihub.service.result.WanxImageResult;

import java.util.*;

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
    private final IIntentDetectionService intentDetectionService;
    private final IWanxImageService wanxImageService;
    private final WanxProperties wanxProperties;


    /**
     * 处理普通文本聊天请求（直接使用 ChatModel 实现流式对话）
     */
    @Override
    @Transactional
    public Flux<String> textChat(Long groupId, Long sessionId, String prompt,
                                 Boolean enableThinking, Integer thinkingBudget, String model) {
        log.info("开始处理textChat请求: groupId={}, sessionId={}, prompt={}", groupId, sessionId, prompt);

        // 1. 处理会话创建
        boolean isNewSession = (sessionId == null);
        Long finalSessionId = createSessionIfNeeded(isNewSession, sessionId, groupId);
        
        // 2. 保存用户消息（无论后续走什么流程，用户消息都需要保存）
        chatMessageService.saveUserMessage(finalSessionId, prompt);
        
        // 检测是否有"生图"的语意
        IntentDetectionResult intentResult = intentDetectionService.analyzeIntent(prompt);
        if ("image_generation".equals(intentResult.getIntent())) {
            // 路由跳转到图片生成
            return generateImageResponse(groupId, finalSessionId, intentResult.getImagePrompt(), enableThinking, thinkingBudget, model, isNewSession);
        }
        
        // 构建初始事件流（新会话通知）
        Flux<String> initialFlux = isNewSession
                ? Flux.just("SESSION_CREATED:" + finalSessionId)
                : Flux.empty();

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
        // 添加当前用户消息
        historyMessages.add(new UserMessage(prompt));

        // 4. 直接使用 ChatModel 流式调用（绕过 ChatClient advisor 链，避免流式 enableThinking 缺陷）
        DashScopeChatOptions runtimeOptions = DashScopeChatOptions.builder()
                .model(model != null ? model : "qwen-plus")
                .enableThinking(enableThinking)
                .build();
        if (thinkingBudget != null) {
            runtimeOptions.setMaxTokens(thinkingBudget);
        }

        Prompt chatPrompt = new Prompt(historyMessages, runtimeOptions);
        Flux<ChatResponse> chatResponseFlux = textChatModel.stream(chatPrompt);

        // 5. 解析流式响应，直接输出 <think> 标签包裹的思考内容 + 裸文本回答
        StringBuilder fullResponseBuilder = new StringBuilder();
        StringBuilder fullReasoningBuilder = new StringBuilder();

        Flux<String> chatFlux = chatResponseFlux
                .concatMap(chatResponse -> {
                    if (chatResponse.getResults() == null || chatResponse.getResults().isEmpty()) {
                        return Flux.empty();
                    }
                    var output = chatResponse.getResults().get(0).getOutput();
                    var chunkMetadata = output.getMetadata();
                    String text = output.getText();

                    // 诊断日志
                    if (!chunkMetadata.isEmpty()) {
                        log.debug("[chunk-meta] keys={} | reasoningContent={}",
                                chunkMetadata.keySet(),
                                chunkMetadata.get("reasoningContent"));
                    }

                    // 提取思考内容（DashScope 使用 camelCase key: "reasoningContent"）
                    String thinkingChunk = null;
                    Object raw = chunkMetadata.get("reasoningContent");
                    if (raw instanceof String s && StringUtils.hasText(s)) {
                        thinkingChunk = s;
                    }

                    List<String> results = new ArrayList<>();
                    if (thinkingChunk != null) {
                        fullReasoningBuilder.append(thinkingChunk);
                        results.add("<think>" + thinkingChunk + "</think>");
                    }
                    if (StringUtils.hasText(text)) {
                        fullResponseBuilder.append(text);
                        results.add(text);
                    }
                    return Flux.fromIterable(results);
                })
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
                .doOnError(error -> log.error("流式对话出错", error));

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

    private Flux<String> generateImageResponse(Long groupId, Long sessionId, String imagePrompt,
            Boolean enableThinking, Integer thinkingBudget, String model, boolean isNewSession) {
        // 构建初始事件流（新会话通知）
        Flux<String> initialFlux = isNewSession
                ? Flux.just("SESSION_CREATED:" + sessionId)
                : Flux.empty();

        // Emit AI prompt echo
        String promptEcho = "为您生成图片: " + imagePrompt;

        try {
            WanxImageResult imageResult = wanxImageService.generateImage(imagePrompt, sessionId);

            // TODO 生成图片之外的其他流程都异步处理，ai对话不是强一致性的场景，先确保用户体验
            String ossUrl = imageResult.getImageUrl();
            // Save as AI message
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("model", wanxProperties.getModel());
            metadata.put("prompt", imagePrompt);
            metadata.put("image_url", ossUrl);
            metadata.put("original_url", imageResult.getOriginalUrl());

            String content = "![image](" + ossUrl + ")";
            chatMessageService.saveAiMessage(sessionId, content, metadata);
            chatSessionService.updateLastActiveTime(sessionId);

            // 新会话则生成标题
            if (isNewSession) {
                titleGenerationService.generateAndUpdateTitle(sessionId);
            }

            return initialFlux
                    .concatWith(Flux.just("AI_PROMPT:" + promptEcho))
                    .concatWith(Flux.just("IMAGE_URL:" + ossUrl))
                    .concatWith(Flux.just("DONE"));

        } catch (Exception e) {
            log.error("Image generation failed: {}", e.getMessage());
            return initialFlux
                    .concatWith(Flux.just("ERROR:图片生成失败，请稍后重试"));
        }
    }
}
