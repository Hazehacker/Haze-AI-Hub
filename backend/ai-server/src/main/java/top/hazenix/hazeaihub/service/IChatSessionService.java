package top.hazenix.hazeaihub.service;

import top.hazenix.hazeaihub.dto.SessionListDTO;
import top.hazenix.hazeaihub.entity.ChatSession;

import java.util.List;

/**
 * @description: 会话服务接口
 * @author: Hazenix
 * @version: 1.0.0
 * @date: 2026/1/27
 */
public interface IChatSessionService {
    /**
     * 创建新会话
     * @param userId 用户ID
     * @param type 会话类型 (chat/pdf/game/service)
     * @param title 会话标题（可选）
     * @return 创建的会话对象（包含自增生成的ID）
     */
    ChatSession createSession(Long userId, String type, String title);

    /**
     * 修改会话信息
     * @param sessionId 会话ID
     * @param title 会话标题
     * @param groupId 分组ID
     */
    void updateSession(Long sessionId, String title, Long groupId) throws RuntimeException;

    /**
     * 删除会话
     * @param sessionId 会话ID
     */
    void deleteSession(Long sessionId);
    
    /**
     * 获取会话详情
     * @param sessionId 会话ID
     * @return 会话对象
     */
    ChatSession getSessionById(Long sessionId);
    
    /**
     * 更新会话最后活跃时间
     * @param sessionId 会话ID
     */
    void updateLastActiveTime(Long sessionId);
    
    /**
     * 获取用户的会话列表
     * @param userId 用户ID
     * @param type 会话类型（可选）
     * @param groupId 分组ID（可选）
     * @param page 页码
     * @param pageSize 每页数量
     * @return 会话列表
     */
    List<SessionListDTO> getSessionList(Long userId, String type, Long groupId, Integer page, Integer pageSize);
    
    /**
     * 置顶/取消置顶会话
     * @param sessionId 会话ID
     * @param isTop 是否置顶
     */
    void toggleTop(Long sessionId, Boolean isTop);
}

