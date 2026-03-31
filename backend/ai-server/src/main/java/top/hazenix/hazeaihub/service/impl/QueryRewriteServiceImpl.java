package top.hazenix.hazeaihub.service.impl;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import top.hazenix.hazeaihub.config.AstraProperties;
import top.hazenix.hazeaihub.service.IQueryRewriteService;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryRewriteServiceImpl implements IQueryRewriteService {

    private final DashScopeChatModel chatModel;
    private final AstraProperties astraProperties;

    private static final String REWRITE_PROMPT = """
            你是一个查询改写助手。请将用户问题简化并规范化为适合知识库检索的标准查询形式。

            要求：
            1. 去除口语化表达和语气词
            2. 保留核心意图和关键术语
            3. 保持简洁，不超过原句长度
            4. 不添加原句中没有的信息

            示例：
            输入："这个PDF里面讲了些什么内容啊"
            输出："PDF内容摘要"

            输入："怎么才能创建实例呢"
            输出："如何创建实例"

            输入："请问一下关于微服务架构的设计原则都有哪些"
            输出："微服务架构设计原则"

            输入："%s"
            输出：
            """;

    @Override
    public String rewrite(String query) {
        if (!isEnabled()) {
            return query;
        }

        try {
            DashScopeChatOptions options = DashScopeChatOptions.builder()
                    .model(astraProperties.getQueryRewrite().getModel())
                    .maxToken(astraProperties.getQueryRewrite().getMaxTokens())
                    .temperature(astraProperties.getQueryRewrite().getTemperature())
                    .build();

            Prompt prompt = new Prompt(
                    String.format(REWRITE_PROMPT, query),
                    options
            );

            ChatResponse response = chatModel.call(prompt);

            if (response != null && response.getResult() != null
                    && response.getResult().getOutput() != null) {
                String rewritten = response.getResult().getOutput().getText().trim();
                log.debug("Query重写成功: {} -> {}", query, rewritten);
                return rewritten;
            }

            log.warn("Query重写返回空结果，使用原查询: {}", query);
            return query;

        } catch (Exception e) {
            log.error("Query重写异常，使用原查询: {}", query, e);
            return query;
        }
    }

    @Override
    public boolean isEnabled() {
        return astraProperties.getQueryRewrite().isEnabled();
    }
}