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
        ChatSession session = chatSessionService.createSession(userId, type, title);
        return Result.success(session.getId());
    }

    /**
     * 修改会话信息
     * @param sessionId 会话ID
     * @param title 会话标题（可选）
     * @param groupId 分组ID（可选）
     */
    @PutMapping
    public Result<Void> updateSession(
            @RequestParam Long sessionId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Long groupId
    ) {
//        log.info("修改会话: {}", sessionId);
        chatSessionService.updateSession(sessionId, title, groupId);
        return Result.success();
    }

    /**
     * 删除会话
     * @param sessionId 会话ID
     */
    @DeleteMapping
    public Result<Void> deleteSession(@RequestParam Long sessionId) {
//        log.info("删除会话: {}", sessionId);
        chatSessionService.deleteSession(sessionId);
        return Result.success();
    }


}
