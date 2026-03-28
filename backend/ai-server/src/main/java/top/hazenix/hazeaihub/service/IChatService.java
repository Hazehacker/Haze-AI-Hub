package top.hazenix.hazeaihub.service;

import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 对话服务接口
 * 利用 Spring AI Alibaba 框架原生支持
 */
public interface IChatService {

    /**
     * 文本对话，支持思考过程（流式返回）
     *
     * @param groupId        分组ID
     * @param sessionId      会话ID
     * @param prompt         提示语
     * @param enableThinking 是否启用思考过程
     * @param thinkingBudget 思考 token 预算
     * @param model          模型名称
     * @return 流式响应，包含 type (thinking/answer/session-created/error) 和 content
     */
    Flux<Map<String, String>> textChat(Long groupId, Long sessionId, String prompt, Boolean enableThinking, Integer thinkingBudget, String model);
}
