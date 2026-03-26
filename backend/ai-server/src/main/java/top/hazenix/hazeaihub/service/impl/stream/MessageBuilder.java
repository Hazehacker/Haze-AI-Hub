package top.hazenix.hazeaihub.service.impl.stream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.hazenix.hazeaihub.constant.RoleConstant;
import top.hazenix.hazeaihub.entity.ChatMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API 消息格式转换器
 * 负责将内部消息格式转换为百炼 API 所需的格式
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageBuilder {

    /**
     * 将 ChatMessage 列表转换为 API 格式的消息列表
     *
     * @param historyMessages 历史消息列表
     * @param currentUserMessage 当前用户消息
     * @return API 格式的消息列表
     */
    public List<Map<String, String>> buildApiMessages(
            List<ChatMessage> historyMessages,
            String currentUserMessage) {

        List<Map<String, String>> messages = new ArrayList<>();

        // 转换历史消息（排除刚保存的最新用户消息）
        if (historyMessages != null && !historyMessages.isEmpty()) {
            for (int i = 0; i < historyMessages.size() - 1; i++) {
                ChatMessage msg = historyMessages.get(i);
                Map<String, String> msgMap = convertToApiFormat(msg);
                if (!msgMap.isEmpty()) {
                    messages.add(msgMap);
                }
            }
        }

        // 添加当前用户消息
        Map<String, String> currentMessage = new HashMap<>();
        currentMessage.put("role", "user");
        currentMessage.put("content", currentUserMessage);
        messages.add(currentMessage);

        return messages;
    }

    /**
     * 将单个 ChatMessage 转换为 API 格式
     */
    private Map<String, String> convertToApiFormat(ChatMessage msg) {
        Map<String, String> msgMap = new HashMap<>();
        if (RoleConstant.USER_ROLE.equals(msg.getRole())) {
            msgMap.put("role", RoleConstant.USER);
            msgMap.put("content", msg.getContent());
        } else if (RoleConstant.ASSISTANT_ROLE.equals(msg.getRole())) {
            msgMap.put("role", RoleConstant.ASSISTANT);
            msgMap.put("content", msg.getContent());
        }
        return msgMap;
    }

    /**
     * 构建 API 请求体
     *
     * @param model 模型名称
     * @param messages 消息列表
     * @param enableThinking 是否启用思考
     * @param thinkingBudget 思考预算
     * @return API 请求体
     */
    public Map<String, Object> buildRequestBody(
            String model,
            List<Map<String, String>> messages,
            Boolean enableThinking,
            Integer thinkingBudget) {

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("stream", true);
        requestBody.put("messages", messages);

        if (enableThinking != null && enableThinking) {
            requestBody.put("enable_thinking", true);
            if (thinkingBudget != null && thinkingBudget > 0) {
                requestBody.put("thinking_budget", thinkingBudget);
            }
        }

        return requestBody;
    }

    /**
     * 构建 AI 消息元数据
     *
     * @param model 模型名称
     * @param enableThinking 是否启用思考
     * @param thinkingBudget 思考预算
     * @param thinkingContent 思考内容
     * @param thinkingDuration 思考耗时
     * @return 元数据 Map
     */
    public Map<String, Object> buildMetadata(
            String model,
            Boolean enableThinking,
            Integer thinkingBudget,
            String thinkingContent,
            Long thinkingDuration) {

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("model", model);

        if (enableThinking != null && enableThinking) {
            metadata.put("enable_thinking", true);
            if (thinkingBudget != null) {
                metadata.put("thinking_budget", thinkingBudget);
            }
            if (thinkingContent != null && !thinkingContent.isEmpty()) {
                metadata.put("thinking_content", thinkingContent);
            }
            if (thinkingDuration != null) {
                metadata.put("thinking_duration", thinkingDuration);
            }
        }

        return metadata;
    }
}
