package top.hazenix.hazeaihub.service.result;

import lombok.Data;

@Data
public class IntentDetectionResult {
    private String intent; // "image_generation" or "text"
    private String imagePrompt; // extracted prompt for image generation

    public static IntentDetectionResult imageGeneration(String prompt) {
        IntentDetectionResult result = new IntentDetectionResult();
        result.setIntent("image_generation");
        result.setImagePrompt(prompt);
        return result;
    }

    public static IntentDetectionResult text() {
        IntentDetectionResult result = new IntentDetectionResult();
        result.setIntent("text");
        return result;
    }
}