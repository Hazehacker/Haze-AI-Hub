package top.hazenix.hazeaihub.service;

import top.hazenix.hazeaihub.entity.ChatMessage;

import java.util.List;

/**
 * @description: 标题生成服务接口
 * @author: Hazenix
 * @version: 1.0.0
 * @date: 2026/1/27
 */
public interface ITitleGenerationService {
    
    /**
     * 根据对话内容生成标题
     * @param messages 对话消息列表（通常是首轮对话）
     * @return 生成的标题
     */
    String generateTitle(List<ChatMessage> messages);
    
    /**
     * 异步生成并更新会话标题
     * @param sessionId 会话ID
     */
    void generateAndUpdateTitle(Long sessionId);
}
