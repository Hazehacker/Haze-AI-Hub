package top.hazenix.hazeaihub.service.impl;

import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesis;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisListResult;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisParam;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisResult;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.task.AsyncTaskListParam;
import com.alibaba.dashscope.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import top.hazenix.hazeaihub.entity.Attachment;
import top.hazenix.hazeaihub.mapper.AttachmentMapper;
import top.hazenix.hazeaihub.properties.WanxProperties;
import top.hazenix.hazeaihub.service.IWanxImageService;
import top.hazenix.hazeaihub.service.result.WanxImageResult;
import top.hazenix.hazeaihub.utils.AliOssUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
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

    public void basicCall(String prompt) throws ApiException, NoApiKeyException {
        // 设置parameters参数
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("prompt_extend", true);
        parameters.put("watermark", false);
        parameters.put("seed", 12345);

        ImageSynthesisParam param =
                ImageSynthesisParam.builder()
                        .apiKey(wanxProperties.getApiKey())
                        .model(wanxProperties.getModel())
                        .prompt(prompt)
                        .n(1)
                        .size("1280*1280")
                        .negativePrompt("")
                        .parameters(parameters)
                        .build();

        ImageSynthesis imageSynthesis = new ImageSynthesis();
        ImageSynthesisResult result = null;
        try {
            log.info("---sync call, please wait a moment----");
            result = imageSynthesis.call(param);
        } catch (ApiException | NoApiKeyException e){
            throw new RuntimeException(e.getMessage());
        }

        log.info(JsonUtils.toJson(result));
    }

    public void listTask() throws ApiException, NoApiKeyException {
        ImageSynthesis is = new ImageSynthesis();
        AsyncTaskListParam param = AsyncTaskListParam.builder().build();
        param.setApiKey(wanxProperties.getApiKey());
        ImageSynthesisListResult result = is.list(param);
        System.out.println(result);
    }

    public void fetchTask(String taskId) throws ApiException, NoApiKeyException {
        ImageSynthesis is = new ImageSynthesis();
        // If set DASHSCOPE_API_KEY environment variable, apiKey can null.
        ImageSynthesisResult result = is.fetch(taskId, wanxProperties.getApiKey());
        System.out.println(result.getOutput());
        System.out.println(result.getUsage());
    }
    @Override
    public WanxImageResult generateImage(String prompt, Long sessionId) {
        log.info("Generating image with prompt: {}", prompt);

        // 1. 构建SDK参数
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("prompt_extend", true);
        parameters.put("watermark", false);

        ImageSynthesisParam param = ImageSynthesisParam.builder()
                .apiKey(wanxProperties.getApiKey())
                .model(wanxProperties.getModel())
                .prompt(prompt)
                .n(1)
                .size("1024*1024")
                .negativePrompt("")
                .parameters(parameters)
                .build();

        // 2. 调用SDK获取结果
        ImageSynthesisResult result;
        try {
            ImageSynthesis imageSynthesis = new ImageSynthesis();
            result = imageSynthesis.call(param);
            log.info("Wanx SDK result: {}", JsonUtils.toJson(result));
        } catch (ApiException | NoApiKeyException e) {
            log.error("Wanx SDK call failed: {}", e.getMessage());
            throw new RuntimeException("图片生成失败，请稍后重试");
        }

        // 3. 解析图片URL
        String imageUrl;
        try {
            imageUrl = result.getOutput().getResults().get(0).getOrDefault("url", null);
            if (imageUrl == null || imageUrl.isBlank()) {
                throw new RuntimeException("Wanx SDK returned empty image URL");
            }
        } catch (Exception e) {
            log.error("Failed to get image URL from SDK result: {}", e.getMessage());
            throw new RuntimeException("图片生成失败，请稍后重试");
        }

        // 4. 保存到OSS
        String ossKey;
        String ossUrl;
        byte[] imageBytes = null;
        try {
            imageBytes = downloadImage(imageUrl);
            ossKey = "wanx/" + UUID.randomUUID().toString() + ".png";
            ossUrl = aliOssUtil.upload(imageBytes, ossKey);
            log.info("Image saved to OSS: {}", ossUrl);
        } catch (Exception e) {
            log.warn("OSS upload failed, using original Wanx URL: {}", e.getMessage());
            ossUrl = imageUrl;
            ossKey = null;
        }

        // 5. 持久化到attachment
        try {
            // 计算contentHash（如果成功下载了图片）
            String contentHash;
            if (imageBytes != null && imageBytes.length > 0) {
                contentHash = calculateSha256(imageBytes);
            } else {
                // 下载失败时使用占位符（OSS上传依赖Wanx预签名URL，需要用户自己处理）
                contentHash = "pending";
            }

            Attachment attachment = Attachment.builder()
                    .fileName("wanx_" + UUID.randomUUID().toString() + ".png")
                    .mimeType("image/png")
                    .fileSize(imageBytes != null ? (long) imageBytes.length : 0L)
                    .storagePath(ossUrl)
                    .contentHash(contentHash) // sha256字段
                    .sourceType("wanx")
                    .createdAt(LocalDateTime.now())
                    .build();

            attachmentMapper.insert(attachment);
            log.info("Attachment record created: id={}", attachment.getId());
        } catch (Exception e) {
            log.warn("Failed to create attachment record: {}", e.getMessage());
        }

        WanxImageResult wanxResult = new WanxImageResult();
        wanxResult.setImageUrl(ossUrl);
        wanxResult.setPrompt(prompt);
        wanxResult.setOssKey(ossKey);
        wanxResult.setOriginalUrl(imageUrl);

        log.info("Image generation complete: prompt={}, url={}", prompt, ossUrl);
        return wanxResult;
    }

    private byte[] downloadImage(String imageUrl) throws IOException {
        return wanxRestTemplate.getForObject(imageUrl, byte[].class);
    }

    private String calculateSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.warn("SHA-256 algorithm not available", e);
            return null;
        }
    }
}