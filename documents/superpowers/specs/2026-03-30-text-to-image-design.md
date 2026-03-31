# 文生图 (Text-to-Image) Multimodal Feature Design

**Date:** 2026-03-30
**Status:** Draft

---

## Context

The Haze AI Hub currently supports text-based streaming chat with AI reasoning. Users want the ability to generate images from natural language prompts within the same chat interface. The feature should:
- Detect image generation intent from any natural phrasing ("帮我画...", "生成一张...", etc.)
- Use Wanx FLUX model for high-quality image generation
- Display images inline in the chat with prompt echo
- Save generated images to OSS storage for download and reuse

---

## Architecture Overview

```
User Input
    ↓
IntentDetectionService (AI-powered intent analysis)
    ↓
Intent Router
    ↓
┌─────────────────────────┴─────────────────────────┐
↓                                                  ↓
TextChatService                               WanxImageService
(existing)                                    (new - Wanx FLUX API)
    ↓                                                  ↓
SSE streaming                                 Save to OSS + Attachment table
    ↓                                                  ↓
Frontend text                                Frontend image rendering
```

---

## Backend Components

### 1. New Services

#### IntentDetectionService
- **File:** `backend/ai-server/src/main/java/top/hazenix/hazeaihub/service/IntentDetectionService.java`
- **Interface:**
```java
public interface IntentDetectionService {
    IntentDetectionResult analyzeIntent(String userInput);
}

@Data
public class IntentDetectionResult {
    private String intent; // "image_generation" or "text"
    private String imagePrompt; // extracted prompt for image generation
}
```
- **Implementation:** Calls AI with a structured detection prompt
- **Detection Prompt:** "分析用户输入，判断是否需要生成图片。图片生成关键词包括：画、生成、创作、设计。"

#### WanxImageService
- **File:** `backend/ai-server/src/main/java/top/hazenix/hazeaihub/service/WanxImageService.java`
- **Interface:**
```java
public interface WanxImageService {
    WanxImageResult generateImage(String prompt, Long sessionId);
}

@Data
public class WanxImageResult {
    private String imageUrl; // OSS URL
    private String prompt; // original prompt
    private String ossKey; // OSS storage key
}
```
- **Dependencies:** RestTemplate/WebClient for Wanx API, FileService for OSS upload

### 2. Modified Components

#### ChatServiceImpl (Modify existing)
- **File:** `backend/ai-server/src/main/java/top/hazenix/hazeaihub/service/impl/ChatServiceImpl.java`
- **Change:** At start of `textChat()`, call `intentDetectionService.analyzeIntent(prompt)`
  - If `intent == "image_generation"`: route to `wanxImageService.generateImage()`
  - If `intent == "text"`: continue with existing text chat flow

#### Attachment Entity (Modify existing)
- **File:** `backend/ai-pojo/src/main/java/top/hazenix/hazeaihub/entity/Attachment.java`
- **Add fields:**
  - `prompt` (String) - image generation prompt
  - `sourceType` (String) - "wanx_flux" for generated images
  - `originalUrl` (String) - original Wanx CDN URL before OSS upload

### 3. New Endpoint

#### POST /api/v1/ai/image/generate
- **File:** `backend/ai-server/src/main/java/top/hazenix/hazeaihub/controller/ImageGenerationController.java`
- **Parameters:**
  - `prompt` (String, required) - image generation prompt
  - `sessionId` (Long, optional) - link to chat session
- **Response:** SSE streaming
  ```
  data:AI_PROMPT:为您生成图片: [extracted prompt]
  data:IMAGE_URL:[OSS URL]
  data:DONE
  ```

---

## Wanx FLUX API Integration

### Configuration (application.yaml)
```yaml
spring:
  ai:
    dashscope:
      api-key: ${ai.bailian.api-key}
  wanx:
    api-key: ${ai.wanx.api-key}  # Separate Wanx key
    model: wanxFLUX
    endpoint: https://dashscope.aliyuncs.com/api/v1/services/a2xlvm9xb7tx/image Generation
```

### API Request
```json
POST /api/v1/services/a2xlvm9xb7tx/image Generation
Headers: Authorization: Bearer {WANX_API_KEY}
{
  "model": "wanxFLUX",
  "input": {
    "prompt": "一只可爱的猫咪",
    "size": "1024*1024",
    "n": 1
  },
  "parameters": {
    "response_format": "url"
  }
}
```

### API Response
```json
{
  "output": {
    "image_url": "https://dashscope-result.xxx.com/xxx.png"
  },
  "request_id": "xxx"
}
```

---

## Storage Flow

1. Receive image URL from Wanx API response
2. Download image bytes from Wanx CDN URL
3. Upload to OSS via existing `FileService.uploadFile()`
4. Create `Attachment` record:
   - `prompt`: extracted image prompt
   - `mimeType`: detected from response or default "image/png"
   - `storagePath`: OSS URL
   - `sourceType`: "wanx_flux"
   - `originalUrl`: original Wanx CDN URL
   - `sessionId`: linked chat session

---

## Frontend Changes

### ChatMessage.vue (Modify existing)
- **File:** `frontend/src/components/ChatMessage.vue`
- **Add message type detection for image content**
- **New image message rendering:**
```vue
<div v-if="message.type === 'image'" class="image-message">
  <div class="image-prompt">为您生成图片: {{ message.prompt }}</div>
  <img :src="message.imageUrl" class="generated-image" @click="openLightbox" />
  <div class="image-actions">
    <button @click="copyImage">复制</button>
    <button @click="downloadImage">下载</button>
  </div>
</div>
```
- **Add lightbox modal for full-size view**

### AIChat.vue (Modify existing)
- **File:** `frontend/src/views/AIChat.vue`
- **Handle new SSE event types:**
  - `AI_PROMPT:` - display prompt echo
  - `IMAGE_URL:` - receive image URL
- **Store image messages with structure:**
```javascript
{
  id: 'temp_xxx',
  role: 'assistant',
  type: 'image',
  prompt: '一只可爱的猫咪',
  imageUrl: 'https://oss.xxx.com/xxx.png',
  status: 'done'
}
```

---

## SSE Event Format

### Image Generation Flow
```
data:AI_PROMPT:为您生成图片: 一只可爱的猫咪

data:IMAGE_URL:https://oss.hazenix.top/images/xxx.png

data:DONE
```

### Error Format
```
data:ERROR:图片生成失败，请稍后重试
```

---

## Error Handling

| Scenario | Handling |
|----------|----------|
| Wanx API failure | Return error message: "图片生成失败，请稍后重试" |
| Wanx API timeout (>30s) | Return error message with retry option |
| OSS upload failure | Retry 3 times, then fallback to original Wanx URL |
| Intent detection failure | Default to text chat (fail open) |
| Invalid prompt (empty after extraction) | Return error: "未能识别图片描述，请重新描述" |

---

## Testing Plan

1. **Intent Detection Tests**
   - Test various phrasings: "帮我画", "生成一张", "创作", etc.
   - Test non-image prompts are routed to text chat

2. **Image Generation Tests**
   - Generate simple image and verify OSS storage
   - Verify Attachment record created correctly
   - Test error handling with invalid API key

3. **End-to-End Tests**
   - Send "帮我画一只猫" → verify image appears in chat
   - Verify image is clickable and shows lightbox
   - Verify download button works

---

## Files to Modify/Create

### Backend - New Files
- `backend/ai-server/src/main/java/top/hazenix/hazeaihub/service/IntentDetectionService.java` (interface)
- `backend/ai-server/src/main/java/top/hazenix/hazeaihub/service/impl/IntentDetectionServiceImpl.java`
- `backend/ai-server/src/main/java/top/hazenix/hazeaihub/service/WanxImageService.java` (interface)
- `backend/ai-server/src/main/java/top/hazenix/hazeaihub/service/impl/WanxImageServiceImpl.java`
- `backend/ai-server/src/main/java/top/hazenix/hazeaihub/controller/ImageGenerationController.java`
- `backend/ai-server/src/main/java/top/hazenix/hazeaihub/config/WanxConfiguration.java`

### Backend - Modified Files
- `backend/ai-server/src/main/java/top/hazenix/hazeaihub/service/impl/ChatServiceImpl.java` (add routing)
- `backend/ai-pojo/src/main/java/top/hazenix/hazeaihub/entity/Attachment.java` (add fields)
- `backend/ai-server/src/main/resources/application.yaml` (add wanx config)
- `backend/ai-server/src/main/resources/application-dev.yaml` (add wanx api-key)

### Frontend - Modified Files
- `frontend/src/components/ChatMessage.vue` (add image rendering)
- `frontend/src/views/AIChat.vue` (handle image SSE events)
- `frontend/src/services/api.js` (add image generation API)

---

## Success Criteria

1. User can type "帮我画一只可爱的猫咪" and see the generated image inline in chat
2. Generated images are saved to OSS and accessible via download
3. Image generation intent is detected from natural language (not just explicit keywords)
4. Failed image generation shows error message without breaking chat flow
5. Existing text chat functionality remains unchanged