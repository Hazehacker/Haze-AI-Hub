package top.hazenix.hazeaihub.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.hazenix.hazeaihub.constant.MessageConstant;
import top.hazenix.hazeaihub.context.BaseContext;
import top.hazenix.hazeaihub.entity.ChatSession;
import top.hazenix.hazeaihub.mapper.ChatSessionMapper;
import top.hazenix.hazeaihub.service.IChatSessionService;

import java.time.LocalDateTime;

/**
 * @description: 会话服务实现类
 * @author: Hazenix
 * @version: 0.0.1
 * @date: 2026/1/16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionServiceServiceImpl implements IChatSessionService {
    
    private final ChatSessionMapper chatSessionMapper;
    
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
        
        ChatSession session = ChatSession.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .status(true)
                .lastActiveAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
        
        chatSessionMapper.insert(session);
        
        log.info("创建新会话成功，会话ID: {}, 用户ID: {}, 类型: {}", session.getId(), userId, type);
        
        return session;
    }

    @Override
    public void updateSession(Long sessionId, String title, Long groupId) throws RuntimeException {
        // 参数校验
        if (sessionId == null || sessionId <= 0) {
            throw new IllegalArgumentException(MessageConstant.ILLEGAL_SESSION_ID);
        }
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException(MessageConstant.ILLEGAL_TITLE);
        }
        if (groupId == null || groupId <= 0) {
            throw new IllegalArgumentException(MessageConstant.ILLEGAL_GROUP_ID);
        }

        // 身份校验
        if (!BaseContext.getCurrentId().equals(chatSessionMapper.selectById(sessionId).getUserId())) {
            throw new RuntimeException(MessageConstant.NOT_AUTHED_TO_DELETE);
        }

        LambdaUpdateWrapper<ChatSession> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(ChatSession::getId, sessionId);
        updateWrapper.set(ChatSession::getUpdatedAt, LocalDateTime.now());
        if(title != null) {
            updateWrapper.set(ChatSession::getTitle, title);
        }
        if(groupId != null) {
            updateWrapper.set(ChatSession::getGroupId, groupId);
        }

        chatSessionMapper.update(updateWrapper);

    }

    @Override
    public void deleteSession(Long sessionId) {
        // 参数校验
        if (sessionId == null || sessionId <= 0) {
            throw new IllegalArgumentException(MessageConstant.ILLEGAL_SESSION_ID);
        }

        // 身份校验
        if (!BaseContext.getCurrentId().equals(chatSessionMapper.selectById(sessionId).getUserId())) {
            throw new RuntimeException(MessageConstant.NOT_AUTHED_TO_DELETE);
        }

        chatSessionMapper.deleteById(sessionId);
    }
}
