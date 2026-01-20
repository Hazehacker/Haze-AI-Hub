package top.hazenix.hazeaihub.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import top.hazenix.hazeaihub.context.BaseContext;
import top.hazenix.hazeaihub.entity.ChatMessage;
import top.hazenix.hazeaihub.result.Result;
import top.hazenix.hazeaihub.service.IHistoryService;

import java.util.List;

/**
 * @description: 历史记录管理接口
 * @author: Hazenix
 * @version: 0.0.1
 * @date: 2026/1/20
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai/history")
@RequiredArgsConstructor
public class HistoryController {
    
    private final IHistoryService historyService;
    
    /**
     * 获取指定类型的会话历史列表
     * @param type 会话类型 (chat/pdf/game/service)
     * @return 会话ID列表（按最后活跃时间降序）
     */
    @GetMapping("/{type}")
    public Result<List<Long>> getChatHistory(@PathVariable String type) {
        try {
            Long userId = BaseContext.getCurrentId();
            log.info("获取会话历史列表，用户ID: {}, 类型: {}", userId, type);
            List<Long> sessionIds = historyService.getSessionIdsByTypeAndUserId(type, userId);
            return Result.success(sessionIds);
        } catch (Exception e) {
            log.error("获取会话历史列表失败", e);
            return Result.error("获取会话历史列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取指定会话的消息历史
     * @param type 会话类型 (chat/pdf/game/service)
     * @param chatId 会话ID
     * @return 消息列表（按创建时间升序）
     */
    @GetMapping("/{type}/{chatId}")
    public Result<List<ChatMessage>> getChatMessages(
            @PathVariable String type,
            @PathVariable Long chatId) {
        try {
            Long userId = BaseContext.getCurrentId();
            log.info("获取会话消息历史，用户ID: {}, 类型: {}, 会话ID: {}", userId, type, chatId);
            List<ChatMessage> messages = historyService.getMessagesBySessionIdAndUserId(chatId, userId);
            return Result.success(messages);
        } catch (Exception e) {
            log.error("获取会话消息历史失败", e);
            return Result.error("获取会话消息历史失败: " + e.getMessage());
        }
    }
}
