package top.hazenix.hazeaihub.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.hazenix.hazeaihub.entity.ChatMessage;
import top.hazenix.hazeaihub.entity.ChatSession;
import top.hazenix.hazeaihub.mapper.ChatMessageMapper;
import top.hazenix.hazeaihub.mapper.ChatSessionMapper;
import top.hazenix.hazeaihub.service.IHistoryService;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @description: 历史记录服务实现类
 * @author: Hazenix
 * @version: 0.0.1
 * @date: 2026/1/20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryServiceImpl implements IHistoryService {
    
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    
    /**
     * 根据会话类型和用户ID获取会话ID列表
     * @param type 会话类型 (chat/pdf/game/service)
     * @param userId 用户ID
     * @return 会话ID列表（按最后活跃时间降序）
     */
    @Override
    public List<Long> getSessionIdsByTypeAndUserId(String type, Long userId) {
        LambdaQueryWrapper<ChatSession> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ChatSession::getType, type)
                    .eq(ChatSession::getUserId, userId)
                    .eq(ChatSession::getStatus, true)
                    .orderByDesc(ChatSession::getLastActiveAt);
        
        List<ChatSession> sessions = chatSessionMapper.selectList(queryWrapper);
        
        log.info("查询到用户 {} 的 {} 条 {} 类型的会话记录", userId, sessions.size(), type);
        
        return sessions.stream()
                .map(ChatSession::getId)
                .collect(Collectors.toList());
    }
    
    /**
     * 根据会话ID和用户ID获取消息列表（增加用户权限校验）
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @return 消息列表（按创建时间升序）
     */
    @Override
    public List<ChatMessage> getMessagesBySessionIdAndUserId(Long sessionId, Long userId) {
        // 先验证会话是否属于该用户
        LambdaQueryWrapper<ChatSession> sessionWrapper = new LambdaQueryWrapper<>();
        sessionWrapper.eq(ChatSession::getId, sessionId)
                     .eq(ChatSession::getUserId, userId)
                     .eq(ChatSession::getStatus, true);
        
        ChatSession session = chatSessionMapper.selectOne(sessionWrapper);
        
        if (session == null) {
            log.warn("会话 {} 不存在或不属于用户 {}", sessionId, userId);
            return List.of();
        }
        
        // 查询消息列表
        LambdaQueryWrapper<ChatMessage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ChatMessage::getSessionId, sessionId)
                    .eq(ChatMessage::getStatus, true)
                    .orderByAsc(ChatMessage::getCreatedAt);
        
        List<ChatMessage> messages = chatMessageMapper.selectList(queryWrapper);
        
        log.info("查询到会话 {} 的 {} 条消息记录", sessionId, messages.size());
        
        return messages;
    }
}
