package top.hazenix.hazeaihub.service;

import reactor.core.publisher.Flux;

/**
 * 对话服务接口
 * 利用 Spring AI Alibaba 框架原生支持
 */
public interface IChatService {

    /**
     * 文本对话，支持思考过程（流式返回）
     * <p>
     * 直接返回纯文本流：思考内容用 {@code <think>...</think>} 标签包裹，回答内容为裸文本。
     * 新会话创建时会先发出 {@code SESSION_CREATED:sessionId} 约定字符串。
     *
     * @param groupId        分组ID
     * @param sessionId      会话ID
     * @param prompt         提示语
     * @param enableThinking 是否启用思考过程
     * @param thinkingBudget 思考 token 预算
     * @param model          模型名称
     * @return 流式纯文本响应
     */
    Flux<String> textChat(Long groupId, Long sessionId, String prompt, Boolean enableThinking, Integer thinkingBudget, String model);
}
