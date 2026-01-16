package top.hazenix.hazeaihub.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import top.hazenix.hazeaihub.entity.ChatSession;
import top.hazenix.hazeaihub.result.Result;
import top.hazenix.hazeaihub.service.IChatSessionService;

/**
 * @description: 会话管理接口
 * @author: Hazenix
 * @version: 0.0.1
 * @date: 2026/1/16
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai/session")
@RequiredArgsConstructor
public class SessionController {
    
    private final IChatSessionService chatSessionService;
    
    /**
     * 创建新会话
     * @param userId 用户ID
     * @param type 会话类型 (chat/pdf/game/service)
     * @param title 会话标题（可选）
     * @return 包含会话ID的响应
     */
    @PostMapping("/create")
    public Result<Long> createSession(
            @RequestParam Long userId,
            @RequestParam String type,
            @RequestParam(required = false) String title) {
        
        try {
            ChatSession session = chatSessionService.createSession(userId, type, title);
            return Result.success(session.getId());
        } catch (Exception e) {
            log.error("创建会话失败", e);
            return Result.error("创建会话失败: " + e.getMessage());
        }
    }
}
