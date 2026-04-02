package top.hazenix.hazeaihub.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import top.hazenix.hazeaihub.entity.Attachment;
import top.hazenix.hazeaihub.mapper.AttachmentMapper;
import top.hazenix.hazeaihub.properties.WanxProperties;
import top.hazenix.hazeaihub.service.IWanxImageService;
import top.hazenix.hazeaihub.service.result.WanxImageResult;
import top.hazenix.hazeaihub.utils.AliOssUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WanxImageServiceImpl implements IWanxImageService {

    private final WanxProperties wanxProperties;
    private final AliOssUtil aliOssUtil;
    private final AttachmentMapper attachmentMapper;
    private final RestTemplate wanxRestTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public WanxImageResult generateImage(String prompt, Long sessionId) {
        log.info("Generating image with prompt: {}", prompt);

        // Step 1: Call Wanx API
        String wanxUrl = wanxProperties.getEndpoint() + "?apiKey=" + wanxProperties.getApiKey();

        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> input = new HashMap<>();
        input.put("prompt", prompt);
        input.put("size", "1024*1024");
        input.put("n", 1);
        requestBody.put("model", wanxProperties.getModel());
        requestBody.put("input", input);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("response_format", "url");
        requestBody.put("parameters", parameters);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response;
        try {
            response = wanxRestTemplate.exchange(
                    wanxUrl,
                    HttpMethod.POST,
                    entity,
                    String.class
            );
        } catch (Exception e) {
            log.error("Wanx API call failed: {}", e.getMessage());
            throw new RuntimeException("图片生成失败，请稍后重试");
        }

        // Step 2: Parse response
        String imageUrl;
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            imageUrl = root.path("output").path("image_url").asText();
            if (imageUrl == null || imageUrl.isBlank()) {
                throw new RuntimeException("Wanx API returned empty image URL");
            }
        } catch (Exception e) {
            log.error("Failed to parse Wanx response: {}", response.getBody());
            throw new RuntimeException("图片生成失败，请稍后重试");
        }

        // Step 3: Download and save to OSS
        String ossKey;
        String ossUrl;
        try {
            byte[] imageBytes = downloadImage(imageUrl);
            String extension = ".png";
            ossKey = "wanx/" + UUID.randomUUID().toString() + extension;
            ossUrl = aliOssUtil.upload(imageBytes, ossKey);
            log.info("Image saved to OSS: {}", ossUrl);
        } catch (Exception e) {
            log.warn("OSS upload failed, using original Wanx URL: {}", e.getMessage());
            ossUrl = imageUrl;
            ossKey = null;
        }

        // Step 4: Create attachment record
        try {
            Attachment attachment = Attachment.builder()
                    .fileName("wanx_" + UUID.randomUUID().toString() + ".png")
                    .mimeType("image/png")
                    .fileSize(0L)
                    .storagePath(ossUrl)
                    .sourceType("wanx_flux")
                    .build();

            attachmentMapper.insert(attachment);
            log.info("Attachment record created: id={}", attachment.getId());
        } catch (Exception e) {
            log.warn("Failed to create attachment record: {}", e.getMessage());
            // Don't fail the whole operation if attachment recording fails
        }

        WanxImageResult result = new WanxImageResult();
        result.setImageUrl(ossUrl);
        result.setPrompt(prompt);
        result.setOssKey(ossKey);
        result.setOriginalUrl(imageUrl);

        log.info("Image generation complete: prompt={}, url={}", prompt, ossUrl);
        return result;
    }

    private byte[] downloadImage(String imageUrl) throws IOException {
        return wanxRestTemplate.getForObject(imageUrl, byte[].class);
    }
}