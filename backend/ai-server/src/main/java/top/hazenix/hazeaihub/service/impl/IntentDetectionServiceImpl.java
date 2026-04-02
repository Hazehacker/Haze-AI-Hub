package top.hazenix.hazeaihub.service.impl;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.model.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import top.hazenix.hazeaihub.constant.PromptConstant;
import top.hazenix.hazeaihub.service.IIntentDetectionService;
import top.hazenix.hazeaihub.service.result.IntentDetectionResult;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntentDetectionServiceImpl implements IIntentDetectionService {

    private final DashScopeChatModel chatModel;

    @Override
    public IntentDetectionResult analyzeIntent(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return IntentDetectionResult.text();
        }

        // Quick keyword check first for performance
        if (isImageKeyword(userInput)) {
            String prompt = extractImagePrompt(userInput);
            if (prompt != null && !prompt.isBlank()) {
                log.info("Intent detected: image_generation, prompt={}", prompt);
                return IntentDetectionResult.imageGeneration(prompt);
            }
        }

        // Fallback to AI-powered detection
        try {
            DashScopeChatOptions options = DashScopeChatOptions.builder()
                    .model("qwen-plus")
                    .enableThinking(false)
                    .maxToken(100)
                    .temperature(0.1)
                    .build();

            Prompt prompt = new Prompt(
                    String.format(PromptConstant.INTENT_DETECTION_PROMPT, userInput),
                    options
            );

            StringBuilder response = new StringBuilder();
            Flux<ChatResponse> flux = chatModel.stream(prompt);
            flux.blockFirst(); // Get first response

            // Collect full response
            for (ChatResponse chatResponse : flux.toIterable()) {
                if (chatResponse.getResults() != null && !chatResponse.getResults().isEmpty()) {
                    String text = chatResponse.getResults().get(0).getOutput().getText();
                    if (text != null) {
                        response.append(text);
                    }
                }
            }

            return parseDetectionResult(response.toString(), userInput);
        } catch (Exception e) {
            log.warn("Intent detection failed, defaulting to text: {}", e.getMessage());
            return IntentDetectionResult.text();
        }
    }

    private boolean isImageKeyword(String input) {
        String lower = input.toLowerCase();
        return lower.contains("画") || lower.contains("生成") ||
               lower.contains("创作") || lower.contains("设计") ||
               lower.contains("draw") || lower.contains("generate image") ||
               lower.contains("create image");
    }

    private String extractImagePrompt(String input) {
        // Extract the image description after keywords like "画", "生成"
        String result = input
                .replaceAll("(?i).*(帮我)?画(一张|个)?", "")
                .replaceAll("(?i).*生成(一张|个)?(图片|图像)?", "")
                .replaceAll("(?i).*创作(一张|个)?(图片|图像)?", "")
                .replaceAll("(?i).*设计(一张|个)?(图片|图像)?", "")
                .trim();

        // If extraction resulted in empty, return the whole input minus known prefixes
        if (result.isBlank()) {
            result = input
                    .replaceAll("(?i)^(帮我|请|帮我画|请画|生成|创作|设计)", "")
                    .trim();
        }
        return result.isBlank() ? null : result;
    }

    private IntentDetectionResult parseDetectionResult(String response, String originalInput) {
        try {
            // Try to parse JSON from response
            String json = response.trim();
            // Handle markdown code blocks if present
            if (json.contains("```json")) {
                json = json.substring(json.indexOf("```json") + 7);
                json = json.substring(0, json.indexOf("```"));
            } else if (json.contains("```")) {
                json = json.substring(json.indexOf("```") + 3);
                json = json.substring(0, json.indexOf("```"));
            }
            json = json.trim();

            if (json.contains("image_generation")) {
                // Extract image_prompt from JSON
                int promptStart = json.indexOf("\"image_prompt\"");
                if (promptStart >= 0) {
                    int colon = json.indexOf(":", promptStart);
                    int quoteStart = json.indexOf("\"", colon + 1);
                    int quoteEnd = json.indexOf("\"", quoteStart + 1);
                    String extractedPrompt = json.substring(quoteStart + 1, quoteEnd);
                    if (!extractedPrompt.isBlank()) {
                        return IntentDetectionResult.imageGeneration(extractedPrompt);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse detection JSON: {}", response);
        }

        // If AI detection didn't return image intent, try keyword extraction
        String extracted = extractImagePrompt(originalInput);
        if (extracted != null && !extracted.isBlank()) {
            return IntentDetectionResult.imageGeneration(extracted);
        }

        return IntentDetectionResult.text();
    }
}