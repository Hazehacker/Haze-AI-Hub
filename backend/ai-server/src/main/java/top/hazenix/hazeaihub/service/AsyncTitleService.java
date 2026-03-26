package top.hazenix.hazeaihub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import top.hazenix.hazeaihub.entity.ChatMessage;
import top.hazenix.hazeaihub.service.impl.TitleGenerationServiceImpl;

import java.util.List;

/**
 * @description: 异步标题生成服务
 * @author: Hazenix
 * @version: 1.0.0
 * @date: 2026/3/26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncTitleService {

    private final IChatMessageService chatMessageService;
    private final IChatSessionService chatSessionService;
    private final TitleGenerationServiceImpl titleGenerationServiceImpl;

    @Async
    public void generateAndUpdateTitleAsync(Long sessionId) {
        log.info("开始异步生成标题: sessionId={}", sessionId);
        try {
            List<ChatMessage> messages = chatMessageService.getFirstRoundMessages(sessionId);
            if (messages.size() < 2) {
                log.warn("消息不足，跳过标题生成: sessionId={}", sessionId);
                return;
            }
            String title = titleGenerationServiceImpl.generateTitle(messages);
            chatSessionService.updateSession(sessionId, title, null);
            log.info("标题生成并更新成功: sessionId={}, title={}", sessionId, title);
        } catch (Exception e) {
            log.error("异步标题生成失败: sessionId={}", sessionId, e);
        }
    }
}