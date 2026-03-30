package top.hazenix.hazeaihub.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.hazenix.hazeaihub.adviser.ReasoningContentAdvisor;
import top.hazenix.hazeaihub.constant.PromptConstant;

@Configuration
public class ChatClientConfiguration {

    @Bean
    public ChatClient chatClient(DashScopeChatModel model) {
        return ChatClient.builder(model)
                .defaultAdvisors(new ReasoningContentAdvisor(0))
                .build();
    }

    /**
     * 用于哄哄模拟器
     */
    @Bean
    public ChatClient gameChatClient(DashScopeChatModel model) {
        return ChatClient.builder(model)
                .defaultSystem(PromptConstant.GAME_SYSTEM_PROMPT)
                .build();
    }

    @Bean
    public ChatClient mediaChatClient(DashScopeChatModel model) {
        return ChatClient.builder(model)
                .defaultSystem(PromptConstant.GAME_SYSTEM_PROMPT)
                .build();
    }

    /**
     * 用于Astra知识库对话
     */
    @Bean
    public ChatClient astraClient(DashScopeChatModel model) {
        return ChatClient.builder(model)
                .defaultSystem("请根据提供的上下文回答问题，不要自己猜测。")
                .build();
    }
}
