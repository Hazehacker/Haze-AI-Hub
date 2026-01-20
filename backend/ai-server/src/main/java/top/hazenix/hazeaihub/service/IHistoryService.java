package top.hazenix.hazeaihub.service;

import top.hazenix.hazeaihub.entity.ChatMessage;

import java.util.List;

/**
 * @description: 历史记录服务接口
 * @author: Hazenix
 * @version: 0.0.1
 * @date: 2026/1/20
 */
public interface IHistoryService {
    
    /**
     * 根据会话类型和用户ID获取会话ID列表
     * @param type 会话类型 (chat/pdf/game/service)
     * @param userId 用户ID
     * @return 会话ID列表（按最后活跃时间降序）
     */
    List<Long> getSessionIdsByTypeAndUserId(String type, Long userId);
    
    /**
     * 根据会话ID和用户ID获取消息列表
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @return 消息列表（按创建时间升序）
     */
    List<ChatMessage> getMessagesBySessionIdAndUserId(Long sessionId, Long userId);
}
