package top.hazenix.hazeaihub.service;


import reactor.core.publisher.Flux;

import java.util.Map;

public interface IBailianThinkingService {
    Flux<Map<String, String>> chatWithThinking(String userMessage,
                                               Boolean enableThinking,
                                               Integer thinkingBudget,
                                               String chatId);

}
