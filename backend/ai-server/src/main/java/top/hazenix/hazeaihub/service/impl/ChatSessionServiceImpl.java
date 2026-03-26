package top.hazenix.hazeaihub.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.hazenix.hazeaihub.constant.MessageConstant;
import top.hazenix.hazeaihub.context.BaseContext;
import top.hazenix.hazeaihub.dto.SessionListDTO;
import top.hazenix.hazeaihub.entity.ChatMessage;
import top.hazenix.hazeaihub.entity.ChatSession;
import top.hazenix.hazeaihub.mapper.ChatMessageMapper;
import top.hazenix.hazeaihub.mapper.ChatSessionMapper;
import top.hazenix.hazeaihub.service.IChatSessionService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @description: 会话服务实现类
 * @author: Hazenix
 * @version: 1.0.0
 * @date: 2026/1/27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionServiceImpl implements IChatSessionService {
    
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    
    /**
     * 创建新会话
     * @param userId 用户ID
     * @param type 会话类型 (chat/pdf/game/service)
     * @param title 会话标题（可选）
     * @return 创建的会话对象（包含自增生成的ID）
     */
    @Override
    @Transactional
    public ChatSession createSession(Long userId, String type, String title) {
        LocalDateTime now = LocalDateTime.now();
        
        // 如果没有提供标题，使用默认标题
        if (title == null || title.trim().isEmpty()) {
            title = "新对话";
        }
        
        ChatSession session = ChatSession.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .status(true)
                .isTop(false)
                .lastActiveAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
        
        chatSessionMapper.insert(session);
        
        log.info("创建新会话成功: sessionId={}, userId={}, type={}, title={}", 
                session.getId(), userId, type, title);
        
        return session;
    }

    @Override
    @Transactional
    public void updateSession(Long sessionId, String title, Long groupId) throws RuntimeException {
        // 参数校验
        if (sessionId == null || sessionId <= 0) {
            throw new IllegalArgumentException(MessageConstant.ILLEGAL_SESSION_ID);
        }

        // 身份校验
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new RuntimeException("会话不存在");
        }
        
        Long currentUserId = BaseContext.getCurrentId();
        if (currentUserId != null && !currentUserId.equals(session.getUserId())) {
            throw new RuntimeException(MessageConstant.NOT_AUTHED_TO_DELETE);
        }

        LambdaUpdateWrapper<ChatSession> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(ChatSession::getId, sessionId);
        updateWrapper.set(ChatSession::getUpdatedAt, LocalDateTime.now());
        
        if (title != null && !title.trim().isEmpty()) {
            updateWrapper.set(ChatSession::getTitle, title);
            log.info("更新会话标题: sessionId={}, title={}", sessionId, title);
        }
        
        if (groupId != null) {
            updateWrapper.set(ChatSession::getGroupId, groupId);
            log.info("更新会话分组: sessionId={}, groupId={}", sessionId, groupId);
        }

        chatSessionMapper.update(updateWrapper);
    }

    @Override
    @Transactional
    public void deleteSession(Long sessionId) {
        // 参数校验
        if (sessionId == null || sessionId <= 0) {
            throw new IllegalArgumentException(MessageConstant.ILLEGAL_SESSION_ID);
        }

        // 身份校验
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new RuntimeException("会话不存在");
        }
        
        Long currentUserId = BaseContext.getCurrentId();
        if (currentUserId != null && !currentUserId.equals(session.getUserId())) {
            throw new RuntimeException(MessageConstant.NOT_AUTHED_TO_DELETE);
        }

        // 软删除
        LambdaUpdateWrapper<ChatSession> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(ChatSession::getId, sessionId);
        updateWrapper.set(ChatSession::getStatus, false);
        updateWrapper.set(ChatSession::getUpdatedAt, LocalDateTime.now());
        
        chatSessionMapper.update(updateWrapper);
        log.info("删除会话: sessionId={}", sessionId);
    }
    
    @Override
    public ChatSession getSessionById(Long sessionId) {
        return chatSessionMapper.selectById(sessionId);
    }
    
    @Override
    @Transactional
    public void updateLastActiveTime(Long sessionId) {
        LambdaUpdateWrapper<ChatSession> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(ChatSession::getId, sessionId);
        updateWrapper.set(ChatSession::getLastActiveAt, LocalDateTime.now());
        updateWrapper.set(ChatSession::getUpdatedAt, LocalDateTime.now());
        
        chatSessionMapper.update(updateWrapper);
        log.debug("更新会话活跃时间: sessionId={}", sessionId);
    }
    
    @Override
    public List<SessionListDTO> getSessionList(Long userId, String type, Long groupId, 
                                                Integer page, Integer pageSize) {
        log.info("获取会话列表: userId={}, type={}, groupId={}, page={}, pageSize={}", 
                userId, type, groupId, page, pageSize);
        
        // 构建查询条件
        LambdaQueryWrapper<ChatSession> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ChatSession::getUserId, userId);
        queryWrapper.eq(ChatSession::getStatus, true);
        
        if (type != null && !type.trim().isEmpty()) {
            queryWrapper.eq(ChatSession::getType, type);
        }
        
        if (groupId != null) {
            queryWrapper.eq(ChatSession::getGroupId, groupId);
        }
        
        // 排序：置顶优先，然后按最后活跃时间降序
        queryWrapper.orderByDesc(ChatSession::getIsTop);
        queryWrapper.orderByDesc(ChatSession::getLastActiveAt);
        
        // 分页
        if (page != null && pageSize != null && page > 0 && pageSize > 0) {
            int offset = (page - 1) * pageSize;
            queryWrapper.last("LIMIT " + pageSize + " OFFSET " + offset);
        }
        
        List<ChatSession> sessions = chatSessionMapper.selectList(queryWrapper);
        
        // 转换为DTO
        List<SessionListDTO> result = new ArrayList<>();
        for (ChatSession session : sessions) {
            // 获取消息数量
            LambdaQueryWrapper<ChatMessage> msgQuery = new LambdaQueryWrapper<>();
            msgQuery.eq(ChatMessage::getSessionId, session.getId());
            msgQuery.eq(ChatMessage::getStatus, true);
            Long messageCount = chatMessageMapper.selectCount(msgQuery);
            
            // 获取最后一条消息预览
            List<ChatMessage> lastMessages = chatMessageMapper.selectBySessionIdOrderByCreatedAt(
                    session.getId(), 1);
            String lastMessagePreview = null;
            if (!lastMessages.isEmpty()) {
                String content = lastMessages.get(0).getContent();
                lastMessagePreview = content.length() > 50 
                        ? content.substring(0, 50) + "..." 
                        : content;
            }
            
            SessionListDTO dto = SessionListDTO.builder()
                    .id(session.getId())
                    .title(session.getTitle())
                    .type(session.getType())
                    .groupId(session.getGroupId())
                    .isTop(session.getIsTop())
                    .lastActiveAt(session.getLastActiveAt())
                    .messageCount(messageCount.intValue())
                    .lastMessagePreview(lastMessagePreview)
                    .build();
            
            result.add(dto);
        }
        
        log.info("获取会话列表成功: count={}", result.size());
        return result;
    }
    
    @Override
    @Transactional
    public void toggleTop(Long sessionId, Boolean isTop) {
        log.info("切换会话置顶状态: sessionId={}, isTop={}", sessionId, isTop);
        
        LambdaUpdateWrapper<ChatSession> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(ChatSession::getId, sessionId);
        updateWrapper.set(ChatSession::getIsTop, isTop);
        updateWrapper.set(ChatSession::getUpdatedAt, LocalDateTime.now());
        
        chatSessionMapper.update(updateWrapper);
    }
}

