package top.hazenix.hazeaihub.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.hazenix.hazeaihub.entity.ChatMessage;
import top.hazenix.hazeaihub.mapper.ChatMessageMapper;
import top.hazenix.hazeaihub.service.IChatMessageService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * @description: 消息服务实现
 * @author: Hazenix
 * @version: 1.0.0
 * @date: 2026/1/27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageServiceImpl implements IChatMessageService {
    
    private final ChatMessageMapper chatMessageMapper;
    
    @Override
    public Long saveUserMessage(Long sessionId, String content) {
        log.info("保存用户消息: sessionId={}, content={}", sessionId, content);
        
        ChatMessage message = ChatMessage.builder()
                .sessionId(sessionId)
                .role("U")
                .content(content)
                .status(true)
                .createdAt(LocalDateTime.now())
                .build();

        int inserted = chatMessageMapper.insert(message);
        if (inserted < 1) {
            log.error("保存用户消息失败: sessionId={}, content={}", sessionId, content);
            throw new RuntimeException("保存用户消息失败");
        }
        log.info("用户消息已保存: messageId={}", message.getId());
        
        return message.getId();
    }
    
    @Override
    @Transactional
    public Long saveAiMessage(Long sessionId, String content, Map<String, Object> metadata) {
        log.info("保存AI消息: sessionId={}, contentLength={}, metadata={}", 
                sessionId, content.length(), metadata);
        
        ChatMessage message = ChatMessage.builder()
                .sessionId(sessionId)
                .role("A")
                .content(content)
                .status(true)
                .metadataJson(metadata)
                .createdAt(LocalDateTime.now())
                .build();
        
        chatMessageMapper.insert(message);
        log.info("AI消息已保存: messageId={}", message.getId());
        
        return message.getId();
    }
    
    @Override
    public List<ChatMessage> getMessagesBySessionId(Long sessionId, Integer limit) {
        log.debug("获取会话消息: sessionId={}, limit={}", sessionId, limit);
        
        if (limit == null || limit <= 0) {
            limit = 100; // 默认最多返回100条
        }
        
        return chatMessageMapper.selectBySessionIdOrderByCreatedAt(sessionId, limit);
    }
    
    @Override
    public List<ChatMessage> getFirstRoundMessages(Long sessionId) {
        log.debug("获取首轮对话: sessionId={}", sessionId);
        
        // 获取前2条消息（用户问题 + AI回答）
        List<ChatMessage> messages = chatMessageMapper.selectBySessionIdOrderByCreatedAt(sessionId, 2);
        
        if (messages.size() < 2) {
            log.warn("会话消息不足2条，无法生成标题: sessionId={}, messageCount={}", 
                    sessionId, messages.size());
        }
        
        return messages;
    }
    
    @Override
    @Transactional
    public void deleteMessage(Long messageId) {
        log.info("删除消息: messageId={}", messageId);
        
        ChatMessage message = ChatMessage.builder()
                .id(messageId)
                .status(false)
                .build();
        
        chatMessageMapper.updateById(message);
        log.info("消息已删除: messageId={}", messageId);
    }
}
