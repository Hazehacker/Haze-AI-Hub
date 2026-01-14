package top.hazenix.hazeaihub.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfiguration {

    // 注意参数中的model就是使用的模型，用什么模型就指定什么模型
    @Bean
    public ChatClient chatClient(ChatModel model) {
        return ChatClient.builder(model) // 创建ChatClient工厂实例
                .defaultSystem("您是Hazenix一个网站的聊天助手，你的名字叫小雾。请以友好、乐于助人和愉快的方式解答游客的各种问题。")
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build(); // 构建ChatClient实例
    }
}
