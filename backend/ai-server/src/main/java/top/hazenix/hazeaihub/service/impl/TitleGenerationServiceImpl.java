package top.hazenix.hazeaihub.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import top.hazenix.hazeaihub.constant.PromptConstant;
import top.hazenix.hazeaihub.constant.RoleConstant;
import top.hazenix.hazeaihub.entity.ChatMessage;
import top.hazenix.hazeaihub.service.IChatMessageService;
import top.hazenix.hazeaihub.service.IChatSessionService;
import top.hazenix.hazeaihub.service.ITitleGenerationService;

import java.util.List;

/**
 * @description: 标题生成服务实现
 * @author: Hazenix
 * @version: 1.0.0
 * @date: 2026/1/27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TitleGenerationServiceImpl implements ITitleGenerationService {
    
    private final IChatMessageService chatMessageService;
    private final IChatSessionService chatSessionService;
    private final DashScopeChatModel chatModel;
    
    private static final int MAX_TITLE_LENGTH = 30;
    
    @Override
    public String generateTitle(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            log.warn("消息列表为空，返回默认标题");
            return "新对话";
        }
        
        try {
            // 构建提示词
            StringBuilder conversationText = new StringBuilder();
            for (ChatMessage msg : messages) {
                String role = RoleConstant.USER.equals(msg.getRole()) ? "用户" : "AI";
                conversationText.append(role).append("：").append(msg.getContent()).append("\n");
            }
            
            String promptText = String.format(
                PromptConstant.SESSION_TITLE_GENERATE_PROMPT_V2,
                MAX_TITLE_LENGTH,
                conversationText.toString()
            );
            
            // 使用 Spring AI 调用大模型生成标题
            Prompt prompt = new Prompt(promptText);
            ChatResponse response = chatModel.call(prompt);
            
            if (response != null && response.getResult() != null 
                    && response.getResult().getOutput() != null) {
                
                String title = response.getResult().getOutput().getText().trim();
                
                // 移除可能的引号
                title = title.replaceAll("^[\"']|[\"']$", "");
                
                // 限制长度
                if (title.length() > MAX_TITLE_LENGTH) {
                    title = title.substring(0, MAX_TITLE_LENGTH) + "...";
                }
                
                log.info("标题生成成功: {}", title);
                return title;
            }
            
            log.warn("标题生成失败，返回默认标题");
            return "新对话";
            
        } catch (Exception e) {
            log.error("标题生成异常", e);
            return "新对话";
        }
    }
    
    @Override
    public void generateAndUpdateTitle(Long sessionId) {
        log.info("开始生成标题: sessionId={}", sessionId);
        
        try {
            // 获取首轮对话
            List<ChatMessage> messages = chatMessageService.getFirstRoundMessages(sessionId);
            
//            if (messages.size() < 2) {
//                log.warn("消息不足，跳过标题生成: sessionId={}", sessionId);
//                return;
//            }
            
            // 生成标题
            String title = generateTitle(messages);
            
            // 更新会话标题
            chatSessionService.updateSession(sessionId, title, null);
            
            log.info("标题生成并更新成功: sessionId={}, title={}", sessionId, title);
            
        } catch (Exception e) {
            log.error("异步标题生成失败: sessionId={}", sessionId, e);
        }
    }
}
