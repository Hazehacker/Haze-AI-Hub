package top.hazenix.hazeaihub.service;

import top.hazenix.hazeaihub.entity.ChatMessage;

import java.util.List;
import java.util.Map;

/**
 * @description: 消息服务接口
 * @author: Hazenix
 * @version: 1.0.0
 * @date: 2026/1/27
 */
public interface IChatMessageService {
    
    /**
     * 保存用户消息
     * @param sessionId 会话ID
     * @param content 消息内容
     * @return 消息ID
     */
    Long saveUserMessage(Long sessionId, String content);
    
    /**
     * 保存AI消息
     * @param sessionId 会话ID
     * @param content 消息内容
     * @param metadata 元数据（模型名、token统计、thinking等）
     * @return 消息ID
     */
    Long saveAiMessage(Long sessionId, String content, Map<String, Object> metadata);
    
    /**
     * 获取会话的消息列表
     * @param sessionId 会话ID
     * @param limit 限制数量（可选）
     * @return 消息列表
     */
    List<ChatMessage> getMessagesBySessionId(Long sessionId, Integer limit);
    
    /**
     * 获取会话的首轮对话（用于生成标题）
     * @param sessionId 会话ID
     * @return 首轮对话消息列表（用户消息+AI回复）
     */
    List<ChatMessage> getFirstRoundMessages(Long sessionId);
    
    /**
     * 删除消息（软删除）
     * @param messageId 消息ID
     */
    void deleteMessage(Long messageId);
}
