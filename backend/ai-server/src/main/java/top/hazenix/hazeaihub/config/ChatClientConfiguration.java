package top.hazenix.hazeaihub.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.hazenix.hazeaihub.constant.PromptConstant;

@Configuration
public class ChatClientConfiguration {

    @Bean
    public ChatClient chatClient(ChatModel model) {
        return ChatClient.builder(model)
                .defaultSystem("您是Haze AI Hub 的聊天助手，你的名字叫小雾，具备深度思考能力，能以友好、乐于助人和愉快的方式解答使用者的各种问题。。")
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
