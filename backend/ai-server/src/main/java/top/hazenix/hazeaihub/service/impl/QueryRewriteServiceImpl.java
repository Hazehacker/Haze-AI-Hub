package top.hazenix.hazeaihub.service.impl;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import top.hazenix.hazeaihub.config.AstraProperties;
import top.hazenix.hazeaihub.constant.PromptConstant;
import top.hazenix.hazeaihub.service.IQueryRewriteService;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryRewriteServiceImpl implements IQueryRewriteService {

    private final DashScopeChatModel chatModel;
    private final AstraProperties astraProperties;

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
                    String.format(PromptConstant.QUERY_REWRITE_PROMPT, query),
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