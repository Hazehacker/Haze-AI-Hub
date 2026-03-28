package top.hazenix.hazeaihub.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.hazenix.hazeaihub.properties.ModelProperties;

@Configuration
@RequiredArgsConstructor
public class AgentConfiguration {
    private final ModelProperties modelProperties;
    private final ChatModel textChatModel;

    /**
     * 创建一个 textChatAgent
     * 1. 默认设置为深度思考、流式响应
     * 2. 模型 temperature: 0.8，maxToken: 1000
     * 3. 支持思考过程输出
     * @return
     */
    @Bean
    public ReactAgent textChatAgent() {
        // 初始化 DashScope ChatModel


        // 创建 ReactAgent
        return ReactAgent.builder()
                .name("textChatAgent")
                .model(textChatModel)
                // 可以在这里添加工具(tools)
                // .tools(getUserInfoTool)
                .build();
    }
}
