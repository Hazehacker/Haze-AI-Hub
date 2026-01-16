package top.hazenix.hazeaihub.service;

import top.hazenix.hazeaihub.entity.ChatSession;

public interface IChatSessionService {
    /**
     * 创建新会话
     * @param userId 用户ID
     * @param type 会话类型 (chat/pdf/game/service)
     * @param title 会话标题（可选）
     * @return 创建的会话对象（包含自增生成的ID）
     */
    ChatSession createSession(Long userId, String type, String title);
}
