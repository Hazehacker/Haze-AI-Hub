package top.hazenix.hazeaihub.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.spec.DashScopeModel;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.hazenix.hazeaihub.properties.ModelProperties;

@Configuration
@RequiredArgsConstructor
public class ModelConfiguration {
    private final ModelProperties modelProperties;

    @Bean
    public ChatModel textChatModel(){
        // 初始化 DashScope ChatModel
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(modelProperties.getApiKey())
                .build();

        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions.builder()
                        .model("qwen-plus")
                        .enableThinking(true)
                        .stream(true)
                        .temperature(0.8)
                        .maxToken(2000)
                        .build()
                )
                .build();
    }
}
